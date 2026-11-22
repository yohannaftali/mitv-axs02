package io.github.yohannaftali.axshome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HomeActivity extends Activity {
    private static final String CONF = "home.conf";
    private static final int TILE_W = 200;
    private static final int TILE_H = 112;

    private LinearLayout rows;
    private ScrollView scroll;
    private View firstTile;
    private long loadedStamp = Long.MIN_VALUE;
    private boolean resumed;

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadedStamp = Long.MIN_VALUE;
            if (resumed) refresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(0xCCFFFFFF);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clockLp.setMargins(0, dp(24), dp(48), 0);
        root.addView(clock, clockLp);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(72), 0, dp(32));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refresh();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME while already home: always land on the first tile, like a reset.
        if (intent.hasCategory(Intent.CATEGORY_HOME) && firstTile != null) {
            scroll.scrollTo(0, 0);
            firstTile.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    private void refresh() {
        long stamp = confStamp();
        if (stamp == loadedStamp) return;
        loadedStamp = stamp;
        build(readConf());
    }

    private File confFile() {
        File dir = getExternalFilesDir(null);
        return dir == null ? null : new File(dir, CONF);
    }

    /** Changes whenever the pushed config is replaced, so a rebuild only happens when needed. */
    private long confStamp() {
        File f = confFile();
        if (f == null || !f.isFile()) return 0;
        return f.lastModified() ^ (f.length() << 40);
    }

    private List<String[]> readConf() {
        List<String[]> out = new ArrayList<>();
        File f = confFile();
        try (InputStream in = (f != null && f.isFile()) ? new FileInputStream(f) : getAssets().open(CONF);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.add(line.split("\\|", -1));
            }
        } catch (IOException e) {
            Toast.makeText(this, "AXS Home: cannot read " + CONF, Toast.LENGTH_LONG).show();
        }
        return out;
    }

    private void build(List<String[]> conf) {
        View focused = getCurrentFocus();
        Object focusTag = focused == null ? null : focused.getTag();
        rows.removeAllViews();

        PackageManager pm = getPackageManager();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        Set<String> listed = new HashSet<>();
        for (String[] e : conf) {
            if ("app".equals(e[0]) && e.length > 2) listed.add(e[2]);
        }
        listed.add(getPackageName());

        LinearLayout row = null;
        View first = null;
        View restore = null;
        for (String[] e : conf) {
            String kind = e[0];
            if ("row".equals(kind)) {
                row = addRow(field(e, 1));
                continue;
            }
            if (row == null) row = addRow("");

            List<View> tiles = new ArrayList<>();
            switch (kind) {
                case "input": {
                    String id = field(e, 2);
                    if (tim != null && tim.getTvInputInfo(id) == null) break;
                    Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
                    tiles.add(textTile(field(e, 1), parseColor(field(e, 3), 0xFF2A2F3A), i, "input:" + id));
                    break;
                }
                case "app": {
                    View t = appTile(pm, field(e, 2), field(e, 1));
                    if (t != null) tiles.add(t);
                    break;
                }
                case "action": {
                    Intent i = new Intent(field(e, 2));
                    if (i.resolveActivity(pm) == null) break;
                    tiles.add(textTile(field(e, 1), 0xFF2A2F3A, i, "action:" + field(e, 2)));
                    break;
                }
                case "allapps":
                    for (String pkg : otherApps(pm, listed)) {
                        View t = appTile(pm, pkg, "");
                        if (t != null) tiles.add(t);
                    }
                    break;
                default:
                    break;
            }
            for (View t : tiles) {
                row.addView(t);
                if (first == null) first = t;
                if (focusTag != null && focusTag.equals(t.getTag())) restore = t;
            }
        }

        for (int i = rows.getChildCount() - 1; i >= 0; i--) {
            View r = rows.getChildAt(i);
            LinearLayout strip = (LinearLayout) ((HorizontalScrollView) ((ViewGroup) r).getChildAt(1)).getChildAt(0);
            int n = strip.getChildCount();
            if (n == 0) {
                rows.removeViewAt(i);
                continue;
            }
            // Keep LEFT/RIGHT inside the row instead of jumping to a neighbouring row.
            View head = strip.getChildAt(0);
            View tail = strip.getChildAt(n - 1);
            head.setNextFocusLeftId(head.getId());
            tail.setNextFocusRightId(tail.getId());
        }

        firstTile = first;
        View target = restore != null ? restore : first;
        if (target != null) target.requestFocus();
    }

    private LinearLayout addRow(String title) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 0, 0, dp(20));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(0x99FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(48), 0, 0, dp(8));
        block.addView(label);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(dp(40), dp(8), dp(40), dp(8));
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(strip);
        block.addView(hs);

        rows.addView(block);
        return strip;
    }

    private View appTile(PackageManager pm, String pkg, String label) {
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return null;
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        if (!ai.enabled) return null;
        String name = label.isEmpty() ? String.valueOf(pm.getApplicationLabel(ai)) : label;

        Drawable banner = pm.getApplicationBanner(ai);
        if (banner == null) {
            try {
                banner = pm.getActivityBanner(launch);
            } catch (PackageManager.NameNotFoundException ignored) {
                banner = null;
            }
        }
        FrameLayout tile = baseTile(0xFF1C2028, launch, "app:" + pkg);
        tile.setContentDescription(name);
        if (banner != null) {
            ImageView img = new ImageView(this);
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            tile.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView img = new ImageView(this);
            img.setImageDrawable(pm.getApplicationIcon(ai));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
            lp.bottomMargin = dp(16);
            tile.addView(img, lp);
            tile.addView(caption(name, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 13));
        }
        return tile;
    }

    private View textTile(String label, int color, Intent intent, String tag) {
        FrameLayout tile = baseTile(color, intent, tag);
        tile.setContentDescription(label);
        tile.addView(caption(label, Gravity.CENTER, 20));
        return tile;
    }

    private TextView caption(String text, int gravity, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(8), 0, dp(8), dp(8));
        t.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity));
        return t;
    }

    private FrameLayout baseTile(int color, final Intent intent, String tag) {
        FrameLayout tile = new FrameLayout(this);
        tile.setId(View.generateViewId());
        tile.setTag(tag);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        tile.setClipToOutline(true);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(color);
        normal.setCornerRadius(dp(8));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(color);
        focused.setCornerRadius(dp(8));
        focused.setStroke(dp(3), Color.WHITE);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, normal);
        tile.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        lp.setMargins(dp(8), 0, dp(8), 0);
        tile.setLayoutParams(lp);

        tile.setOnFocusChangeListener((v, has) -> {
            float s = has ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(90).start();
            v.setElevation(has ? dp(8) : 0);
        });
        tile.setOnClickListener(v -> launch(intent));
        return tile;
    }

    private void launch(Intent intent) {
        Intent i = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> otherApps(PackageManager pm, Set<String> skip) {
        Set<String> pkgs = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (!skip.contains(p)) pkgs.add(p);
            }
        }
        List<String> sorted = new ArrayList<>(pkgs);
        List<String> labels = new ArrayList<>();
        for (String p : sorted) {
            try {
                labels.add(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString().toLowerCase() + "\u0000" + p);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Uninstalled between query and lookup.
            }
        }
        Collections.sort(labels);
        List<String> out = new ArrayList<>();
        for (String l : labels) out.add(l.substring(l.indexOf('\u0000') + 1));
        return out;
    }

    private static String field(String[] e, int i) {
        return i < e.length ? e[i].trim() : "";
    }

    private static int parseColor(String s, int fallback) {
        try {
            return s.isEmpty() ? fallback : Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
