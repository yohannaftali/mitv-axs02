package io.github.yohannaftali.axsclock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * What the screensaver shows: the time and date over a dimmed animated
 * background (or black), with an optional weather line and market tiles
 * underneath. The whole block moves to a new random spot once a minute so
 * nothing stays lit in one place. Used by ClockDream and by the preview that
 * Home settings opens. The feed fetches only between start() and stop().
 */
final class Face {
    private static final long DRIFT_MS = 60_000;
    private static final long WEATHER_STALE_MS = 60 * 60_000;
    private static final int MARGIN_DP = 32;
    private static final int UP = 0xFF4CD964;
    private static final int DOWN = 0xFFFF6B6B;
    private static final int DIM = 0x80FFFFFF;
    /** Darkens the animated background so the text stays readable and the screen stays dim. */
    private static final int SCRIM = 0x99000000;

    private final Context context;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Conf conf;
    private final Feed feed;
    private FrameLayout root;
    private Backdrop backdrop;
    private LinearLayout face;
    private TextView weatherLine;
    private TextView[][] cells;
    private TextView footer;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            move(true);
            ui.postDelayed(this, DRIFT_MS);
        }
    };

    Face(Context context) {
        this.context = context;
        conf = Conf.load(context);
        feed = new Feed(context, conf, () -> ui.post(this::render));
    }

    View build() {
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        if (StyleView.known(conf.background)) {
            backdrop = new StyleView(context, conf.background, 0xFF0A1E4A, 0xFF3D7BFF);
            root.addView(backdrop.view(), matchParent());
            View scrim = new View(context);
            scrim.setBackgroundColor(SCRIM);
            root.addView(scrim, matchParent());
        }
        face = new LinearLayout(context);
        face.setOrientation(LinearLayout.VERTICAL);
        face.setGravity(Gravity.CENTER_HORIZONTAL);
        face.setAlpha(0f);

        boolean panel = conf.weather || !conf.tickers.isEmpty();
        TextClock time = clock(conf.timeFormat, panel ? 120 : 150, 0xFFEDEDED);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        face.addView(time, spaced(0));
        TextClock date = clock(conf.dateFormat, panel ? 26 : 30, 0xB3FFFFFF);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        face.addView(date, spaced(0));

        if (conf.weather) {
            weatherLine = text("", 22, 0xD9FFFFFF);
            weatherLine.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            face.addView(weatherLine, spaced(14));
        }
        if (!conf.tickers.isEmpty()) {
            face.addView(grid(), spaced(30));
            footer = text("", 11, 0x66FFFFFF);
            face.addView(footer, spaced(22));
        }

        root.addView(face, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        render();
        return root;
    }

    void start() {
        face.post(() -> {
            move(false);
            face.animate().alpha(1f).setDuration(400);
        });
        ui.removeCallbacks(drift);
        ui.postDelayed(drift, DRIFT_MS);
        if (backdrop != null) backdrop.start();
        feed.start();
    }

    void stop() {
        ui.removeCallbacks(drift);
        if (backdrop != null) backdrop.stop();
        feed.stop();
    }

    void release() {
        ui.removeCallbacksAndMessages(null);
        if (backdrop != null) {
            backdrop.release();
            backdrop = null;
        }
        feed.stop();
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ---- views ----

    private LinearLayout grid() {
        int n = conf.tickers.size();
        int cols = n <= 4 ? n : (n + 1) / 2;
        cells = new TextView[n][4];
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                rows.addView(row, i == 0 ? wrap() : spaced(18));
            }
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cells[i][0] = text(conf.tickers.get(i).label, 13, 0x8CFFFFFF);
            cells[i][1] = text("–", 24, Color.WHITE);
            cells[i][1].setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            cells[i][2] = text("", 13, DIM);
            cells[i][3] = text("", 11, DIM);
            for (TextView t : cells[i]) cell.addView(t);
            row.addView(cell, new LinearLayout.LayoutParams(dp(170), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return rows;
    }

    private void render() {
        if (face == null) return;
        long now = System.currentTimeMillis();
        if (weatherLine != null) {
            Feed.Weather w = feed.weather();
            if (Double.isNaN(w.temp)) {
                weatherLine.setText("");
            } else {
                String line = String.format(Locale.ROOT, "%d°  %s   ·   H %d°  L %d°",
                        Math.round(w.temp), sky(w.code), Math.round(w.max), Math.round(w.min));
                weatherLine.setText(w.place.isEmpty() ? line : w.place + "   ·   " + line);
                weatherLine.setTextColor(now - w.time > WEATHER_STALE_MS ? DIM : 0xD9FFFFFF);
            }
        }
        if (cells == null) return;
        for (int i = 0; i < cells.length; i++) {
            Conf.Ticker t = conf.tickers.get(i);
            Feed.Value v = feed.value(t);
            // Grey with its time once the value is older than two refreshes (or the market is closed).
            boolean stale = v.asOf == 0 || now - v.asOf > 2 * feed.every(t.source);
            cells[i][1].setText(Double.isNaN(v.price) ? "–" : price(v.price));
            cells[i][1].setTextColor(stale ? DIM : Color.WHITE);
            if (Double.isNaN(v.change)) {
                cells[i][2].setText("");
            } else {
                double pct = v.change * 100;
                cells[i][2].setText(String.format(Locale.ROOT, "%s%.2f%%", pct > 0 ? "▲ +" : pct < 0 ? "▼ " : "", pct));
                cells[i][2].setTextColor(stale ? DIM : pct > 0 ? UP : pct < 0 ? DOWN : DIM);
            }
            cells[i][3].setText(stale && v.asOf > 0 ? "as of " + when(v.asOf, now) : "");
        }
        long last = feed.lastUpdate();
        String status = last == 0 ? "Waiting for data"
                : (online() ? "Updated " : "Offline · last update ") + when(last, now);
        footer.setText(status + "      " + credits());
    }

    /** "14:05" today, "Fri 22:59" this week, "20 Nov" before that. */
    private String when(long t, long now) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(t);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(now);
        String pattern = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
                ? conf.timeFormat : now - t < 6 * 24 * 3_600_000L ? "EEE " + conf.timeFormat : "d MMM";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(t));
    }

    /** Credits for the sources on screen, as their terms ask. */
    private String credits() {
        List<String> names = new ArrayList<>();
        for (Conf.Ticker t : conf.tickers) {
            String n = t.source.equals(Feed.ECB) ? "ECB via Frankfurter" : Catalogue.name(t.source);
            if (!names.contains(n)) names.add(n);
        }
        String s = "Prices: " + String.join(" · ", names);
        return conf.weather ? s + "   Weather: Open-Meteo.com" : s;
    }

    private boolean online() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetwork() != null;
    }

    private static String price(double p) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
        String pattern = p >= 1000 ? "#,##0" : p >= 10 ? "#,##0.00" : "0.0000";
        return new DecimalFormat(pattern, sym).format(p);
    }

    /** WMO weather interpretation codes, as used by Open-Meteo. */
    private static String sky(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    private TextClock clock(String format, int sp, int color) {
        TextClock c = new TextClock(context);
        // Same pattern for both, so the system 12/24-hour switch does not override clock.conf.
        c.setFormat24Hour(format);
        c.setFormat12Hour(format);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        c.setTextColor(color);
        c.setIncludeFontPadding(false);
        return c;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams spaced(int topDp) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(topDp);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    /** Random position inside the screen; the right margin leaves room for a wider minute ("11:11" → "20:08"). */
    private void move(boolean animate) {
        int margin = dp(MARGIN_DP);
        int maxX = root.getWidth() - face.getWidth() - 2 * margin - face.getWidth() / 20;
        int maxY = root.getHeight() - face.getHeight() - 2 * margin;
        if (maxX < 0) maxX = 0;
        if (maxY < 0) maxY = 0;
        float x = margin + random.nextInt(maxX + 1);
        float y = margin + random.nextInt(maxY + 1);
        if (animate) {
            face.animate().translationX(x).translationY(y).setDuration(1500);
        } else {
            face.setTranslationX(x);
            face.setTranslationY(y);
        }
    }
}
