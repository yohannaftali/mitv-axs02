package io.github.yohannaftali.axshome;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * System monitor: what an app may read on Android 11, live. Memory, storage,
 * uptime and the CPU clock (average core speed from cpufreq time_in_state)
 * refresh every 2 s; the apps used today come from usage stats (GET_USAGE_STATS,
 * set by apply.sh). Android 11 hides /proc/stat, load, temperature and other
 * apps' CPU and memory from apps (SELinux), so per-process CPU/RAM is shown on
 * a PC with `sh scripts/tv.sh top`. Nothing runs while the page is closed.
 */
final class MonitorPanel {
    private static final long LIVE_MS = 2_000;
    private static final long APPS_MS = 30_000;
    private static final int ROWS = 8;
    private static final int DIM = 0x99FFFFFF;
    private static final int CARD = 0xB30B0F18;

    private final HomeActivity home;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, CharSequence> labels = new HashMap<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private final Map<Integer, Map<Long, Long>> lastTimes = new HashMap<>();
    private Dialog dialog;
    private HandlerThread thread;
    private Handler worker;

    private TextView cpuValue;
    private TextView cpuDetail;
    private Spark cpuSpark;
    private TextView ramValue;
    private TextView ramDetail;
    private Bar ramBar;
    private TextView diskValue;
    private TextView diskDetail;
    private Bar diskBar;
    private TextView upValue;
    private TextView upDetail;
    private TextView tableTitle;
    private TextView tableNote;
    private LinearLayout tableCard;
    private LinearLayout table;

    private boolean byLastUsed;
    private List<App> apps = new ArrayList<>();
    private String appsNote = "Loading…";

    static final class App {
        final String pkg;
        final long foregroundMs;
        final long lastUsed;

        App(String pkg, long foregroundMs, long lastUsed) {
            this.pkg = pkg;
            this.foregroundMs = foregroundMs;
            this.lastUsed = lastUsed;
        }
    }

    MonitorPanel(HomeActivity home) {
        this.home = home;
    }

    void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    void show() {
        dismiss();
        FrameLayout root = new FrameLayout(home);
        View scrim = new View(home);
        scrim.setBackgroundColor(0xA605070D);
        root.addView(scrim, match());

        LinearLayout main = new LinearLayout(home);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(56), dp(36), dp(56), dp(28));
        root.addView(main, match());

        main.addView(header());
        LinearLayout cards = new LinearLayout(home);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(cpuCard(), weight(0));
        cards.addView(ramCard(), weight(14));
        cards.addView(diskCard(), weight(14));
        cards.addView(upCard(), weight(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        clp.topMargin = dp(18);
        main.addView(cards, clp);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        tlp.topMargin = dp(14);
        main.addView(tableCard(), tlp);

        dialog = new Dialog(home, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0f);
        }
        dialog.setOnKeyListener((d, code, ev) -> onKey(code, ev));
        dialog.setOnDismissListener(d -> {
            stop();
            home.showHomeContent(true);
        });
        home.showHomeContent(false);
        dialog.show();
        start();
    }

    // ---- layout ----

    private View header() {
        LinearLayout row = new LinearLayout(home);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        LinearLayout titles = new LinearLayout(home);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView over = home.label("AXS HOME", 13, DIM, true);
        over.setLetterSpacing(0.2f);
        titles.addView(over);
        TextView title = home.label("System monitor", 32, Color.WHITE, false);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        titles.addView(title);
        row.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String kernel = System.getProperty("os.version", "");
        int dash = kernel.indexOf('-');
        if (dash > 0) kernel = kernel.substring(0, dash);
        TextView info = home.label(Build.MODEL + "  ·  Android " + Build.VERSION.RELEASE
                + "  ·  patch " + Build.VERSION.SECURITY_PATCH + "  ·  kernel " + kernel, 13, DIM, false);
        info.setGravity(Gravity.END);
        row.addView(info);
        return row;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(home);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(12));
        c.setBackground(home.rounded(CARD, 0x24FFFFFF, 18, 1));
        TextView t = home.label(title.toUpperCase(Locale.ROOT), 12, DIM, true);
        t.setLetterSpacing(0.12f);
        c.addView(t);
        return c;
    }

    private TextView value() {
        TextView v = home.label("–", 24, Color.WHITE, false);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        v.setSingleLine(true);
        return v;
    }

    private TextView detail(LinearLayout card) {
        TextView d = home.label("", 12, DIM, false);
        d.setSingleLine(true);
        d.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(d, top(6));
        return d;
    }

    private View cpuCard() {
        LinearLayout c = card("CPU clock");
        LinearLayout row = new LinearLayout(home);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        cpuValue = value();
        row.addView(cpuValue, new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT));
        cpuSpark = new Spark(home, home.accent());
        row.addView(cpuSpark, new LinearLayout.LayoutParams(0, dp(30), 1f));
        c.addView(row, top(2));
        cpuDetail = detail(c);
        return c;
    }

    private View ramCard() {
        LinearLayout c = card("Memory");
        ramValue = value();
        c.addView(ramValue, top(2));
        ramBar = new Bar(home, home.accent());
        c.addView(ramBar, barLp());
        ramDetail = detail(c);
        return c;
    }

    private View diskCard() {
        LinearLayout c = card("Storage");
        diskValue = value();
        c.addView(diskValue, top(2));
        diskBar = new Bar(home, home.accent());
        c.addView(diskBar, barLp());
        diskDetail = detail(c);
        return c;
    }

    private View upCard() {
        LinearLayout c = card("Uptime");
        upValue = value();
        c.addView(upValue, top(2));
        upDetail = detail(c);
        return c;
    }

    private View tableCard() {
        tableCard = new LinearLayout(home);
        tableCard.setOrientation(LinearLayout.VERTICAL);
        tableCard.setPadding(dp(22), dp(14), dp(22), dp(10));
        tableCard.setBackground(home.rounded(CARD, 0x24FFFFFF, 18, 1));
        LinearLayout head = new LinearLayout(home);
        head.setOrientation(LinearLayout.HORIZONTAL);
        tableTitle = home.label("", 15, Color.WHITE, true);
        head.addView(tableTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(home.label("◀ ▶ sort   ·   OK refresh   ·   BACK close", 12, DIM, false));
        tableCard.addView(head);
        tableNote = home.label("", 12, DIM, false);
        tableNote.setSingleLine(true);
        tableNote.setEllipsize(TextUtils.TruncateAt.END);
        tableCard.addView(tableNote, top(2));
        tableCard.addView(row(null, "App", "", "", true), top(8));
        table = new LinearLayout(home);
        table.setOrientation(LinearLayout.VERTICAL);
        tableCard.addView(table, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView pc = home.label("Per-app CPU and memory are hidden from apps on Android 11.  "
                + "On a PC: sh scripts/tv.sh top", 12, DIM, false);
        tableCard.addView(pc, top(4));
        return tableCard;
    }

    private View row(Drawable icon, String name, String a, String b, boolean head) {
        LinearLayout r = new LinearLayout(home);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(head ? 0 : 3), 0, dp(head ? 4 : 3));
        int color = head ? DIM : Color.WHITE;
        int sp = head ? 12 : 15;
        ImageView iv = new ImageView(home);
        if (icon != null) iv.setImageDrawable(icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(head ? 1 : 20));
        ilp.rightMargin = dp(12);
        r.addView(iv, ilp);
        TextView n = home.label(name, sp, color, false);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ta = home.label(a, sp, head && !byLastUsed ? Color.WHITE : color, head && !byLastUsed);
        ta.setGravity(Gravity.END);
        r.addView(ta, new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView tb = home.label(b, sp, head && byLastUsed ? Color.WHITE : color, head && byLastUsed);
        tb.setGravity(Gravity.END);
        r.addView(tb, new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    // ---- refresh ----

    private void start() {
        thread = new HandlerThread("monitor", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        worker = new Handler(thread.getLooper());
        lastTimes.clear();
        worker.post(live);
        worker.post(appsTask);
    }

    private void stop() {
        if (thread == null) return;
        worker.removeCallbacksAndMessages(null);
        thread.quitSafely();
        thread = null;
        worker = null;
    }

    private final Runnable live = new Runnable() {
        @Override
        public void run() {
            Live l = readLive();
            ui.post(() -> showLive(l));
            if (worker != null) worker.postDelayed(this, LIVE_MS);
        }
    };

    private final Runnable appsTask = new Runnable() {
        @Override
        public void run() {
            readApps();
            List<App> snapshot = new ArrayList<>(apps);
            String note = appsNote;
            ui.post(() -> showApps(snapshot, note));
            if (worker != null) worker.postDelayed(this, APPS_MS);
        }
    };

    static final class Live {
        long avgKhz;
        long maxKhz;
        int cores;
        long memTotal;
        long memAvail;
        long diskTotal;
        long diskFree;
        String usb = "";
        long uptimeMs;
    }

    private Live readLive() {
        Live l = new Live();
        long sum = 0;
        for (int i = 0; i < 16; i++) {
            File dir = new File("/sys/devices/system/cpu/cpu" + i);
            if (!dir.exists()) break;
            l.cores++;
            long max = number(firstLine(dir + "/cpufreq/cpuinfo_max_freq"));
            l.maxKhz = Math.max(l.maxKhz, max);
            long avg = averageKhz(i, dir + "/cpufreq/stats/time_in_state");
            if (avg <= 0) avg = number(firstLine(dir + "/cpufreq/scaling_cur_freq"));
            sum += Math.max(0, avg);
        }
        if (l.cores > 0) l.avgKhz = sum / l.cores;

        ActivityManager am = (ActivityManager) home.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            l.memTotal = mi.totalMem;
            l.memAvail = mi.availMem;
        }
        StatFs data = new StatFs(Environment.getDataDirectory().getPath());
        l.diskTotal = data.getTotalBytes();
        l.diskFree = data.getAvailableBytes();
        StorageManager sm = (StorageManager) home.getSystemService(Context.STORAGE_SERVICE);
        if (sm != null) {
            StringBuilder usb = new StringBuilder();
            for (StorageVolume v : sm.getStorageVolumes()) {
                File d = v.getDirectory();
                if (!v.isRemovable() || d == null || !Environment.MEDIA_MOUNTED.equals(v.getState())) continue;
                try {
                    StatFs fs = new StatFs(d.getPath());
                    if (usb.length() > 0) usb.append("  ·  ");
                    usb.append("USB ").append(pair(fs.getTotalBytes() - fs.getAvailableBytes(), fs.getTotalBytes()));
                } catch (IllegalArgumentException ignored) {
                    // Volume went away.
                }
            }
            l.usb = usb.toString();
        }
        l.uptimeMs = SystemClock.elapsedRealtime();
        return l;
    }

    /** Average clock of one core since the last call, from cpufreq time_in_state ("kHz ticks" per line). */
    private long averageKhz(int core, String path) {
        Map<Long, Long> now = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.trim().split("\\s+");
                if (f.length == 2) now.put(Long.parseLong(f[0]), Long.parseLong(f[1]));
            }
        } catch (IOException | NumberFormatException | SecurityException e) {
            return -1;
        }
        Map<Long, Long> before = lastTimes.put(core, now);
        if (before == null) return -1;
        long weighted = 0;
        long ticks = 0;
        for (Map.Entry<Long, Long> e : now.entrySet()) {
            Long prev = before.get(e.getKey());
            long d = e.getValue() - (prev == null ? 0 : prev);
            if (d <= 0) continue;
            weighted += e.getKey() * d;
            ticks += d;
        }
        return ticks == 0 ? -1 : weighted / ticks;
    }

    private void showLive(Live l) {
        if (dialog == null || !dialog.isShowing()) return;
        if (l.avgKhz > 0) {
            cpuValue.setText(String.format(Locale.ROOT, "%.2f GHz", l.avgKhz / 1e6));
            if (l.maxKhz > 0) cpuSpark.add(100f * l.avgKhz / l.maxKhz);
            cpuDetail.setText(l.cores + " cores  ·  max " + String.format(Locale.ROOT, "%.2f GHz", l.maxKhz / 1e6));
        }
        if (l.memTotal > 0) {
            long used = l.memTotal - l.memAvail;
            ramValue.setText(pair(used, l.memTotal));
            ramBar.set((float) used / l.memTotal);
            ramDetail.setText(gb(l.memAvail) + " available");
        }
        if (l.diskTotal > 0) {
            long used = l.diskTotal - l.diskFree;
            diskValue.setText(pair(used, l.diskTotal));
            diskBar.set((float) used / l.diskTotal);
            diskDetail.setText(l.usb.isEmpty() ? gb(l.diskFree) + " free" : gb(l.diskFree) + " free  ·  " + l.usb);
        }
        long s = l.uptimeMs / 1000;
        upValue.setText(s >= 86400 ? String.format(Locale.ROOT, "%dd %dh", s / 86400, (s % 86400) / 3600)
                : String.format(Locale.ROOT, "%dh %02dm", s / 3600, (s % 3600) / 60));
        upDetail.setText("since the TV last started");
    }

    private void readApps() {
        if (!usageAllowed()) {
            apps = new ArrayList<>();
            appsNote = "Needs usage access: run changes/1-fast-home/apply.sh (it allows GET_USAGE_STATS with appops).";
            return;
        }
        UsageStatsManager usm = (UsageStatsManager) home.getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        Map<String, UsageStats> stats = usm == null ? null
                : usm.queryAndAggregateUsageStats(day.getTimeInMillis(), System.currentTimeMillis());
        List<App> out = new ArrayList<>();
        if (stats != null) {
            for (UsageStats u : stats.values()) {
                if (u.getPackageName().equals(home.getPackageName())) continue;
                if (u.getTotalTimeInForeground() <= 0) continue;
                out.add(new App(u.getPackageName(), u.getTotalTimeInForeground(), u.getLastTimeUsed()));
            }
        }
        apps = out;
        appsNote = "Apps opened since midnight, from Android's usage stats  ·  updated every 30 s";
    }

    private boolean usageAllowed() {
        AppOpsManager ops = (AppOpsManager) home.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), home.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private void showApps(List<App> list, String note) {
        if (dialog == null || !dialog.isShowing()) return;
        tableTitle.setText(byLastUsed ? "Apps used today, most recent first" : "Apps used today, longest first");
        tableNote.setText(note);
        list.sort((a, b) -> byLastUsed ? Long.compare(b.lastUsed, a.lastUsed) : Long.compare(b.foregroundMs, a.foregroundMs));
        tableCard.removeViewAt(2);
        tableCard.addView(row(null, "App", byLastUsed ? "Time on screen" : "Time on screen ▼",
                byLastUsed ? "Last used ▼" : "Last used", true), 2, top(8));
        table.removeAllViews();
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(ROWS, list.size()); i++) {
            App a = list.get(i);
            table.addView(row(icon(a.pkg), label(a.pkg).toString(), duration(a.foregroundMs), ago(now - a.lastUsed), false));
        }
    }

    private boolean onKey(int code, KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_UP) {
            return code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                dialog.dismiss();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                byLastUsed = !byLastUsed;
                showApps(new ArrayList<>(apps), appsNote);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (worker != null) {
                    worker.removeCallbacks(appsTask);
                    worker.post(appsTask);
                    tableNote.setText("Refreshing…");
                }
                return true;
            default:
                return false;
        }
    }

    // ---- helpers ----

    private static String firstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private static long number(String s) {
        try {
            return s == null ? -1 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private CharSequence label(String pkg) {
        CharSequence hit = labels.get(pkg);
        if (hit != null) return hit;
        CharSequence name = pkg;
        try {
            PackageManager pm = home.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            name = pm.getApplicationLabel(ai);
            icons.put(pkg, pm.getApplicationIcon(ai));
        } catch (PackageManager.NameNotFoundException ignored) {
            // Uninstalled since: keep the package name.
        }
        labels.put(pkg, name);
        return name;
    }

    private Drawable icon(String pkg) {
        label(pkg);
        return icons.get(pkg);
    }

    private static String duration(long ms) {
        long m = ms / 60000;
        if (m < 1) return "< 1 min";
        return m < 60 ? m + " min" : String.format(Locale.ROOT, "%dh %02dm", m / 60, m % 60);
    }

    private static String ago(long ms) {
        long m = ms / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        return (m / 60) + " h ago";
    }

    private static String gb(long bytes) {
        double g = bytes / 1073741824.0;
        return g >= 10 ? String.format(Locale.ROOT, "%.0f GB", g) : String.format(Locale.ROOT, "%.1f GB", g);
    }

    /** "1.0 / 1.9 GB": one unit at the end keeps the value on one line. */
    private static String pair(long used, long total) {
        double t = total / 1073741824.0;
        String f = t >= 10 ? "%.0f" : "%.1f";
        return String.format(Locale.ROOT, f + " / " + f + " GB", used / 1073741824.0, t);
    }

    private int dp(int v) {
        return home.dp(v);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(margin);
        return lp;
    }

    private LinearLayout.LayoutParams weight(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = dp(leftMargin);
        return lp;
    }

    private LinearLayout.LayoutParams barLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        lp.topMargin = dp(8);
        return lp;
    }

    /** Thin rounded progress bar; turns red above 90%. */
    static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final int color;
        private float value;

        Bar(Context c, int color) {
            super(c);
            this.color = color;
            track.setColor(0x33FFFFFF);
            fill.setColor(color);
        }

        void set(float v) {
            value = Math.max(0, Math.min(1, v));
            fill.setColor(value > 0.9f ? 0xFFFF6B6B : color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, h / 2, h / 2, track);
            r.set(0, 0, Math.max(h, getWidth() * value), h);
            c.drawRoundRect(r, h / 2, h / 2, fill);
        }
    }

    /** The last 30 readings (percent) as a small line chart. */
    static final class Spark extends View {
        private final float[] points = new float[30];
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int count;

        Spark(Context c, int color) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3f);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(color);
            area.setColor((color & 0x00FFFFFF) | 0x33000000);
        }

        void add(float v) {
            System.arraycopy(points, 1, points, 0, points.length - 1);
            points[points.length - 1] = v;
            count = Math.min(points.length, count + 1);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (count < 2) return;
            float w = getWidth();
            float h = getHeight();
            float step = w / (points.length - 1);
            path.reset();
            int first = points.length - count;
            for (int i = first; i < points.length; i++) {
                float x = i * step;
                float y = h - h * Math.min(100, points[i]) / 100f;
                if (i == first) path.moveTo(x, y); else path.lineTo(x, y);
            }
            c.drawPath(path, line);
            path.lineTo(w, h);
            path.lineTo(first * step, h);
            path.close();
            c.drawPath(path, area);
        }
    }
}
