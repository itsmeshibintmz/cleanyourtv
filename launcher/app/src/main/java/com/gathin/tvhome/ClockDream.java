package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
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
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.File;
import java.util.Random;

/**
 * Screensaver: drifting clock and date over a slow ambient glow, on black or on a wallpaper.
 * Every minute the clock drifts to a new spot and the glow shifts colour (burn-in protection).
 * If {@code screensaver.jpg} is present in the launcher's external files directory it is shown
 * behind the glow; push it there with {@code tools/screensaver.sh} or directly via ADB.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);

        // Load screensaver wallpaper if the user has pushed one via tools/screensaver.sh.
        loadScreensaverWallpaper();
    }

    /**
     * Loads {@code screensaver.jpg} from the launcher's external files directory.
     * If absent the screensaver stays black with only the ambient glow.
     */
    private void loadScreensaverWallpaper() {
        File dir = Config.dir(this);
        if (dir == null) return;
        File file = new File(dir, "screensaver.jpg");
        if (!file.isFile() || file.length() == 0) return;

        // Decode at screen resolution to avoid OOM on 1 GB TVs.
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), probe);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm != null ? wm.getDefaultDisplay() : null;
        Point size = new Point(1920, 1080);
        if (display != null) display.getSize(size);

        int sampleX = Math.max(1, probe.outWidth  / Math.max(1, size.x));
        int sampleY = Math.max(1, probe.outHeight / Math.max(1, size.y));
        int sample  = Math.max(1, Math.min(sampleX, sampleY));

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (bmp == null) return;

        ImageView wallpaperView = findViewById(R.id.dreamWallpaper);
        View overlayView        = findViewById(R.id.dreamOverlay);
        if (wallpaperView == null) return;

        wallpaperView.setImageBitmap(bmp);
        wallpaperView.setAlpha(1f);

        // Semi-transparent dark scrim so the white clock stays readable over colourful wallpapers.
        if (overlayView != null) overlayView.setBackgroundColor(0x62000000); // ~38% dark
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.postDelayed(tick, MOVE_MS);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

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
