package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.Random;

/**
 * Screensaver — all upgrades active:
 *   A: center-align + AM/PM superscript
 *   B: rotating tech quote + thin separator
 *   C: rainbow glow matching Mac-icons palette (red→orange→yellow→green→blue→purple)
 *   D: staggered child fade-in (clock → day name → date → separator → quote)
 *   E: 15% scrim + Ken Burns slow zoom on wallpaper
 *   F: date split into day-name and day+month on separate lines
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;

    /** C: Rainbow colours that mirror the Mac-icons pixel art (Apple system palette). */
    private static final int[] PALETTE = {
        0xFFFF3B30, // red
        0xFFFF9500, // orange
        0xFFFFCC00, // yellow
        0xFF34C759, // green
        0xFF007AFF, // blue
        0xFFAF52DE  // purple
    };

    /** B: Rotating quotes — one advances per 60-second drift. */
    private static final String[] DREAM_QUOTES = {
        "There's no place like 127.0.0.1",
        "It's not a bug — it's an undocumented feature.",
        "Talk is cheap. Show me the code.  — Linus",
        "sudo make me a sandwich.",
        "The cloud is just someone else's computer.",
        "Have you tried turning it off and on again?",
        "404: motivation not found.",
        "First, solve the problem. Then, write the code.",
        "Make it work. Make it right. Make it fast.",
        "Good code is its own documentation.",
        "The best code is no code at all.",
        "Simplicity is the ultimate sophistication.",
        "Weeks of coding can save hours of planning.",
        "Ship it. Fix it. Ship it again.",
        "Life is short — use Python.",
        "Ctrl+Z is my superpower.",
        "To err is human. To blame the compiler, divine.",
        "A Wi-Fi password is just a social barrier.",
        "git commit -m 'fixed it, no idea how'",
        "Real engineers ship on Fridays. Then pray.",
        "Premature optimisation is the root of all evil.",
        "Hardware fails. Software eventually works.",
        "Caffeine: the primary programming language.",
        "Open source: standing on giants' shoulders.",
        "The best UX is the one users never notice.",
        "On the Internet, nobody knows you're a toaster.",
        "rm -rf /problems — just kidding, don't.",
        "Think twice. Code once. Deploy never.",
        "Every great dev started where you are now.",
        "Your favourite language is wrong. So is everyone's."
    };

    private final Handler handler    = new Handler(Looper.getMainLooper());
    private final Random  random     = new Random();

    private View          content;
    private View          ambient;
    private GradientDrawable glow;
    private TextView      quoteView;

    private int  colorIndex;
    private int  glowColor;

    // D: stagger index for the current fade-in pass
    // C: breathing glow animator
    private ObjectAnimator breatheAnim;
    // E: Ken Burns zoom animator
    private ObjectAnimator kenBurnsAnim;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    // ───────── Lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content   = findViewById(R.id.dreamContent);
        ambient   = findViewById(R.id.dreamAmbient);
        quoteView = findViewById(R.id.dreamQuote);

        // C: set up radial glow
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);

        // E: load wallpaper (starts Ken Burns inside)
        loadScreensaverWallpaper();

        // B: initial quote
        updateQuote();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();

        // C: breathing glow — pulses alpha 0.6↔1.0 on a 4 s cycle
        breatheAnim = ObjectAnimator.ofFloat(ambient, "alpha", 0.6f, 1.0f);
        breatheAnim.setDuration(4000);
        breatheAnim.setRepeatCount(ValueAnimator.INFINITE);
        breatheAnim.setRepeatMode(ValueAnimator.REVERSE);
        breatheAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        breatheAnim.start();

        // D: staggered initial appearance
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            fadeInChildren();
        });
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        if (breatheAnim != null) { breatheAnim.cancel(); breatheAnim = null; }
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (breatheAnim  != null) { breatheAnim.cancel();  breatheAnim  = null; }
        if (kenBurnsAnim != null) { kenBurnsAnim.cancel(); kenBurnsAnim = null; }
        super.onDetachedFromWindow();
    }

    // ───────── Wallpaper ─────────────────────────────────────────────────────

    private void loadScreensaverWallpaper() {
        File dir  = Config.dir(this);
        if (dir == null) return;
        File file = new File(dir, "screensaver.jpg");
        if (!file.isFile() || file.length() == 0) return;

        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), probe);

        WindowManager wm      = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display       display = wm != null ? wm.getDefaultDisplay() : null;
        Point         size    = new Point(1920, 1080);
        if (display != null) display.getSize(size);

        int sampleX = Math.max(1, probe.outWidth  / Math.max(1, size.x));
        int sampleY = Math.max(1, probe.outHeight / Math.max(1, size.y));
        int sample  = Math.max(1, Math.min(sampleX, sampleY));

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (bmp == null) return;

        ImageView wallpaperView = findViewById(R.id.dreamWallpaper);
        View      overlayView   = findViewById(R.id.dreamOverlay);
        if (wallpaperView == null) return;

        wallpaperView.setImageBitmap(bmp);
        wallpaperView.setAlpha(1f);

        // E: 15% scrim — wallpaper breathes through; text relies on colour contrast
        if (overlayView != null) overlayView.setBackgroundColor(0x26000000);

        // E: Ken Burns — slow 8% zoom in/out over 60 s, looping forever
        kenBurnsAnim = ObjectAnimator.ofPropertyValuesHolder(wallpaperView,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.08f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.08f));
        kenBurnsAnim.setDuration(60_000L);
        kenBurnsAnim.setRepeatCount(ValueAnimator.INFINITE);
        kenBurnsAnim.setRepeatMode(ValueAnimator.REVERSE);
        kenBurnsAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        kenBurnsAnim.start();
    }

    // ───────── Clock movement ─────────────────────────────────────────────────

    private void moveClock() {
        // Fade out the whole block together, then stagger each child back in
        content.animate().cancel();
        content.animate().alpha(0f).setDuration(800).withEndAction(() -> {
            placeClock();
            updateQuote();
            fadeInChildren();
        }).start();
    }

    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth()  - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter(
                    (x + content.getWidth()  / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    // ───────── D: Staggered fade-in ──────────────────────────────────────────

    /** Reveals the parent (alpha=1) then fades each direct child in 250 ms apart. */
    private void fadeInChildren() {
        content.setAlpha(1f);
        ViewGroup g = (ViewGroup) content;
        for (int i = 0; i < g.getChildCount(); i++) {
            View child = g.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(0f);
            child.animate()
                    .alpha(1f)
                    .setStartDelay(i * 250L)
                    .setDuration(1000)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    // ───────── B: Quote ──────────────────────────────────────────────────────

    private void updateQuote() {
        if (quoteView == null) return;
        int idx = (int) Math.abs((System.currentTimeMillis() / MOVE_MS) % DREAM_QUOTES.length);
        quoteView.setText(DREAM_QUOTES[idx]);
    }

    // ───────── C: Rainbow glow ───────────────────────────────────────────────

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
