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

import org.json.JSONObject;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * Screensaver — Twitter-inspired minimal look:
 *   - Wallpaper at full brightness (NO scrim)
 *   - Abbreviated date above a large 24 h clock
 *   - Astronomical sunrise/sunset times below the clock
 *   - Rainbow ambient glow (C) + Ken Burns zoom (E)
 *   - Staggered fade-in (D) + rotating tech quote (B)
 *   - Drifting block for burn-in protection
 *
 * Sunrise/sunset defaults to Pala, Kerala (9.70 N, 76.68 E).
 * Override in config.json: "screensaver_lat": <lat>, "screensaver_lon": <lon>
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;

    // ── Location (for sunrise/sunset) ────────────────────────────────────────
    /** Default: Pala, Kerala, India. Change via config.json keys screensaver_lat / screensaver_lon. */
    private static final double DEFAULT_LAT = 9.70;
    private static final double DEFAULT_LON = 76.68;

    // ── C: Rainbow palette (Apple system colours, matches Mac-icons palette) ──
    private static final int[] PALETTE = {
        0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00,
        0xFF34C759, 0xFF007AFF, 0xFFAF52DE
    };

    // ── B: Rotating tech quotes ───────────────────────────────────────────────
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
    private TextView      sunTimesView;

    private int  colorIndex;
    private int  glowColor;

    private ObjectAnimator breatheAnim;
    private ObjectAnimator kenBurnsAnim;

    /** Cached location read from config.json. */
    private double cachedLat = DEFAULT_LAT;
    private double cachedLon = DEFAULT_LON;

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

        content      = findViewById(R.id.dreamContent);
        ambient      = findViewById(R.id.dreamAmbient);
        quoteView    = findViewById(R.id.dreamQuote);
        sunTimesView = findViewById(R.id.dreamSunTimes);

        // C: radial rainbow glow
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);

        // Read location from config.json (falls back to Pala, Kerala defaults)
        readLocation();

        // E: wallpaper + Ken Burns
        loadScreensaverWallpaper();

        // Initial content
        updateDriftContent();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();

        // C: breathing glow — 0.55 ↔ 1.0 alpha, 4 s cycle
        breatheAnim = ObjectAnimator.ofFloat(ambient, "alpha", 0.55f, 1.0f);
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
        if (wallpaperView == null) return;

        wallpaperView.setImageBitmap(bmp);
        wallpaperView.setAlpha(1f);
        // NO scrim — wallpaper is the hero; text reads via colour contrast alone.

        // E: Ken Burns — subtle 8% zoom over 60 s, looping
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
        content.animate().cancel();
        content.animate().alpha(0f).setDuration(800).withEndAction(() -> {
            placeClock();
            updateDriftContent();
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

    // ───────── B: Content updated each drift ─────────────────────────────────

    private void updateDriftContent() {
        // Quote
        if (quoteView != null) {
            int idx = (int) Math.abs((System.currentTimeMillis() / MOVE_MS) % DREAM_QUOTES.length);
            quoteView.setText(DREAM_QUOTES[idx]);
        }
        // Sunrise/sunset
        if (sunTimesView != null) {
            String[] sun = calcSunTimes(cachedLat, cachedLon);
            sunTimesView.setText("\uD83C\uDF05 " + sun[0] + "   \u2193 " + sun[1]);
        }
    }

    // ───────── Location & Sun times ──────────────────────────────────────────

    /** Reads screensaver_lat / screensaver_lon from config.json (falls back to Pala, Kerala). */
    private void readLocation() {
        cachedLat = DEFAULT_LAT;
        cachedLon = DEFAULT_LON;
        try {
            File dir = Config.dir(this);
            if (dir == null) return;
            File f = new File(dir, Config.FILE_NAME);
            if (!f.isFile()) return;
            byte[] bytes = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            //noinspection ResultOfMethodCallIgnored
            fis.read(bytes);
            fis.close();
            JSONObject j = new JSONObject(new String(bytes, "UTF-8"));
            cachedLat = j.optDouble("screensaver_lat", cachedLat);
            cachedLon = j.optDouble("screensaver_lon", cachedLon);
        } catch (Exception ignored) {}
    }

    /**
     * Returns {sunriseHHmm, sunsetHHmm} for today at the given coordinates.
     * Uses the simplified NOAA equation-of-time formula; accurate to ±2 min.
     */
    private static String[] calcSunTimes(double latDeg, double lonDeg) {
        Calendar c = Calendar.getInstance();
        int N = c.get(Calendar.DAY_OF_YEAR);

        double B    = Math.toRadians(360.0 / 365.0 * (N - 81));
        double eqT  = 9.87 * Math.sin(2 * B) - 7.53 * Math.cos(B) - 1.5 * Math.sin(B); // minutes
        double decl = Math.toRadians(23.45 * Math.sin(B));
        double lat  = Math.toRadians(latDeg);

        // Hour angle at horizon (−0.833° for atmospheric refraction)
        double cosH = (Math.cos(Math.toRadians(90.833)) - Math.sin(lat) * Math.sin(decl))
                    / (Math.cos(lat) * Math.cos(decl));
        cosH = Math.max(-1.0, Math.min(1.0, cosH)); // clamp for polar edge cases
        double H = Math.toDegrees(Math.acos(cosH));

        double tzH         = TimeZone.getDefault().getOffset(c.getTimeInMillis()) / 3_600_000.0;
        double solarNoonLT = 12.0 - lonDeg / 15.0 - eqT / 60.0 + tzH;
        double rise        = ((solarNoonLT - H / 15.0) % 24 + 24) % 24;
        double set         = ((solarNoonLT + H / 15.0) % 24 + 24) % 24;

        return new String[]{hhmm(rise), hhmm(set)};
    }

    private static String hhmm(double h) {
        int hr = (int) h;
        int mn = (int) Math.round((h - hr) * 60);
        if (mn >= 60) { hr++; mn -= 60; }
        return String.format(Locale.ROOT, "%02d:%02d", hr, mn);
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
        glow.setColors(new int[]{0x30000000 | c, 0x10000000 | c, 0x00000000 | c});
    }
}
