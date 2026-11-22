package io.github.yohannaftali.axsclock;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Screensaver: the time and date on black. The block moves to a new random
 * spot once a minute so nothing stays lit in one place. TextClock without
 * seconds only redraws when the minute changes, so it is idle in between.
 */
public final class ClockDream extends DreamService {
    private static final long DRIFT_MS = 60_000;
    private static final int MARGIN_DP = 32;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private FrameLayout root;
    private LinearLayout face;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            move(true);
            ui.postDelayed(this, DRIFT_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        String[] formats = readFormats();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        face = new LinearLayout(this);
        face.setOrientation(LinearLayout.VERTICAL);
        face.setGravity(Gravity.CENTER_HORIZONTAL);
        face.setAlpha(0f);

        TextClock time = clock(formats[0], 150, 0xFFEDEDED);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        face.addView(time);
        TextClock date = clock(formats[1], 30, 0xB3FFFFFF);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        face.addView(date);

        root.addView(face, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        face.post(() -> {
            move(false);
            face.animate().alpha(1f).setDuration(400);
        });
        ui.postDelayed(drift, DRIFT_MS);
    }

    @Override
    public void onDreamingStopped() {
        ui.removeCallbacks(drift);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        ui.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private TextClock clock(String format, int sp, int color) {
        TextClock c = new TextClock(this);
        // Same pattern for both, so the system 12/24-hour switch does not override clock.conf.
        c.setFormat24Hour(format);
        c.setFormat12Hour(format);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        c.setTextColor(color);
        c.setIncludeFontPadding(false);
        return c;
    }

    /** Random position inside the screen; the right margin leaves room for a wider minute ("11:11" → "20:08"). */
    private void move(boolean animate) {
        int margin = Math.round(MARGIN_DP * getResources().getDisplayMetrics().density);
        int maxX = root.getWidth() - face.getWidth() - 2 * margin - face.getWidth() / 10;
        int maxY = root.getHeight() - face.getHeight() - 2 * margin;
        if (maxX <= 0 || maxY <= 0) return;
        float x = margin + random.nextInt(maxX + 1);
        float y = margin + random.nextInt(maxY + 1);
        if (animate) {
            face.animate().translationX(x).translationY(y).setDuration(1500);
        } else {
            face.setTranslationX(x);
            face.setTranslationY(y);
        }
    }

    private String[] readFormats() {
        String[] out = {"HH:mm", "EEEE, d MMMM yyyy"};
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getAssets().open("clock.conf"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.trim().split("\\|", -1);
                if (f.length >= 3 && f[0].equals("clock")) {
                    if (!f[1].isEmpty()) out[0] = f[1];
                    if (!f[2].isEmpty()) out[1] = f[2];
                }
            }
        } catch (IOException ignored) {
            // Defaults above.
        }
        return out;
    }
}
