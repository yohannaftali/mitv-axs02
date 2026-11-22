package io.github.yohannaftali.axshome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HomeActivity extends Activity {
    private static final String CONF = "home.conf";
    private static final String PREF_ORDER = "order";
    private static final int COLS = 3;
    private static final int TILE_W = 164;
    private static final int TILE_H = 92;
    private static final int BG = 0xFF05070D;
    private static final int MOVE_COLOR = 0xFFFFC233;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable startWaves = this::startWaves;

    private FrameLayout root;
    private WaveView waves;
    private LinearLayout grid;
    private LinearLayout sidebar;
    private TextView menuButton;
    private PopupWindow menu;
    private Dialog allApps;

    private final Map<String, String> apps = new LinkedHashMap<>();
    private final List<String[]> inputs = new ArrayList<>();
    private final List<String[]> menuItems = new ArrayList<>();
    private int waveTop = 0xFF0A1E4A;
    private int waveAccent = 0xFF3D7BFF;

    private final List<String> order = new ArrayList<>();
    private final Map<String, View> tiles = new LinkedHashMap<>();
    private String moving;
    private boolean ignoreUp;
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
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(28), dp(48), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content.addView(topBar());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyLp.topMargin = dp(28);
        content.addView(body, bodyLp);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        body.addView(grid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(12), dp(12), dp(12), dp(12));
        sidebar.setBackground(rounded(0x73000000, 0, 12));
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.WRAP_CONTENT);
        sideLp.leftMargin = dp(24);
        body.addView(sidebar, sideLp);

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        when.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        when.addView(date);
        bar.addView(when, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        menuButton = new TextView(this);
        menuButton.setText("☰");
        menuButton.setTextColor(Color.WHITE);
        menuButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setFocusable(true);
        menuButton.setClickable(true);
        menuButton.setContentDescription("Menu");
        menuButton.setBackground(focusBg(0x00000000, 24));
        menuButton.setOnClickListener(v -> showMenu());
        bar.addView(menuButton, new LinearLayout.LayoutParams(dp(56), dp(56)));
        return bar;
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
        // The waves start only after the first frame, so they never delay the home screen.
        root.post(() -> ui.postDelayed(startWaves, 350));
    }

    @Override
    protected void onPause() {
        resumed = false;
        ui.removeCallbacks(startWaves);
        if (waves != null) waves.stop();
        super.onPause();
    }

    private void startWaves() {
        if (!resumed) return;
        if (waves == null) {
            waves = new WaveView(this, waveTop, waveAccent);
            waves.setAlpha(0f);
            root.addView(waves, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            waves.animate().alpha(1f).setDuration(400).start();
        }
        waves.start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) return;
        if (menu != null && menu.isShowing()) menu.dismiss();
        if (allApps != null && allApps.isShowing()) allApps.dismiss();
        if (moving != null) drop();
        focusFirst();
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (moving != null) return moveKey(ev);
        int code = ev.getKeyCode();
        if (ev.getAction() == KeyEvent.ACTION_UP && code == KeyEvent.KEYCODE_MENU) {
            showMenu();
            return true;
        }
        if (ev.getAction() == KeyEvent.ACTION_UP && code == KeyEvent.KEYCODE_ALL_APPS) {
            showAllApps();
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    // ---- configuration ----

    private void refresh() {
        long stamp = confStamp();
        if (stamp == loadedStamp) return;
        loadedStamp = stamp;
        parse(readConf());
        if (waves != null) {
            root.removeView(waves);
            waves.stop();
            waves = null;
        }
        loadOrder();
        buildGrid();
        buildSidebar();
        focusFirst();
    }

    private File confFile() {
        File dir = getExternalFilesDir(null);
        return dir == null ? null : new File(dir, CONF);
    }

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

    private void parse(List<String[]> conf) {
        apps.clear();
        inputs.clear();
        menuItems.clear();
        for (String[] e : conf) {
            switch (e[0]) {
                case "app":
                    if (!field(e, 2).isEmpty()) apps.put(field(e, 2), field(e, 1));
                    break;
                case "input":
                    if (!field(e, 3).isEmpty()) inputs.add(e);
                    break;
                case "menu":
                    menuItems.add(e);
                    break;
                case "wallpaper":
                    waveTop = parseColor(field(e, 2), waveTop);
                    waveAccent = parseColor(field(e, 3), waveAccent);
                    break;
                default:
                    break;
            }
        }
    }

    // ---- app grid and reordering ----

    private SharedPreferences prefs() {
        return getSharedPreferences("home", MODE_PRIVATE);
    }

    /** Saved order first (apps still configured and installed), then the rest in config order. */
    private void loadOrder() {
        order.clear();
        PackageManager pm = getPackageManager();
        String saved = prefs().getString(PREF_ORDER, "");
        for (String p : saved.split(",")) {
            if (apps.containsKey(p) && launchIntent(pm, p) != null && !order.contains(p)) order.add(p);
        }
        for (String p : apps.keySet()) {
            if (!order.contains(p) && launchIntent(pm, p) != null) order.add(p);
        }
    }

    private void saveOrder() {
        prefs().edit().putString(PREF_ORDER, String.join(",", order)).apply();
    }

    private void buildGrid() {
        grid.removeAllViews();
        tiles.clear();
        PackageManager pm = getPackageManager();
        LinearLayout row = null;
        for (int i = 0; i < order.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(20);
                grid.addView(row, lp);
            }
            String pkg = order.get(i);
            View t = appTile(pm, pkg, apps.get(pkg), pkg.equals(moving));
            if (t == null) continue;
            t.setOnLongClickListener(v -> {
                startMove(pkg);
                return true;
            });
            row.addView(t);
            tiles.put(pkg, t);
        }
    }

    private void startMove(String pkg) {
        moving = pkg;
        ignoreUp = true;
        buildGrid();
        focusTile(pkg);
        Toast.makeText(this, "Move with the arrows, OK to place", Toast.LENGTH_SHORT).show();
    }

    private boolean moveKey(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean confirm = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BACK;
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: shift(-1); break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: shift(1); break;
                case KeyEvent.KEYCODE_DPAD_UP: shift(-COLS); break;
                case KeyEvent.KEYCODE_DPAD_DOWN: shift(COLS); break;
                default: break;
            }
            return true;
        }
        if (ev.getAction() == KeyEvent.ACTION_UP && confirm) {
            // The key-up of the long press that started move mode must not drop the tile.
            if (ignoreUp && code != KeyEvent.KEYCODE_BACK) {
                ignoreUp = false;
                return true;
            }
            drop();
        }
        return true;
    }

    private void shift(int delta) {
        ignoreUp = false;
        int from = order.indexOf(moving);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= order.size()) return;
        Collections.swap(order, from, to);
        buildGrid();
        focusTile(moving);
    }

    private void drop() {
        String pkg = moving;
        moving = null;
        ignoreUp = false;
        saveOrder();
        buildGrid();
        focusTile(pkg);
    }

    private void focusTile(String pkg) {
        View t = tiles.get(pkg);
        if (t != null) t.requestFocus();
    }

    private void focusFirst() {
        if (!order.isEmpty()) focusTile(order.get(0));
        else if (sidebar.getChildCount() > 1) sidebar.getChildAt(1).requestFocus();
        else menuButton.requestFocus();
    }

    // ---- inputs sidebar ----

    private void buildSidebar() {
        sidebar.removeAllViews();
        TextView title = new TextView(this);
        title.setText("Inputs");
        title.setTextColor(0x99FFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, dp(6));
        sidebar.addView(title);

        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        for (String[] e : inputs) {
            String label = field(e, 1);
            String port = field(e, 2);
            String id = field(e, 3);
            if (tim != null && tim.getTvInputInfo(id) == null) continue;
            int color = parseColor(field(e, 5), 0xFF5A6478);
            sidebar.addView(inputEntry(label, port, id, IconView.parse(field(e, 4), port), color));
        }
        if (sidebar.getChildCount() == 1) sidebar.setVisibility(View.GONE);
        else sidebar.setVisibility(View.VISIBLE);
    }

    private View inputEntry(String label, String port, String id, int icon, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(focusBg(0x00000000, 10));
        row.setContentDescription(label.isEmpty() ? port : label);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(4);
        row.setLayoutParams(rowLp);

        row.addView(new IconView(this, icon, color), new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, 0, 0);
        text.addView(label(label.isEmpty() ? port : label, 18, Color.WHITE, true));
        if (!label.isEmpty() && !port.isEmpty()) text.addView(label(port, 13, 0xAAFFFFFF, false));
        row.addView(text);

        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        row.setOnClickListener(v -> launch(view));
        return row;
    }

    // ---- menu and all apps ----

    private void showMenu() {
        if (menu != null && menu.isShowing()) {
            menu.dismiss();
            return;
        }
        PackageManager pm = getPackageManager();
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        list.setBackground(rounded(0xF2151A24, 0x33FFFFFF, 12));
        View first = null;
        for (String[] e : menuItems) {
            String kind = field(e, 2);
            String target = field(e, 3);
            String label = field(e, 1);
            Runnable action;
            switch (kind) {
                case "action": {
                    Intent i = new Intent(target);
                    if (i.resolveActivity(pm) == null) continue;
                    action = () -> launch(i);
                    break;
                }
                case "app": {
                    Intent i = launchIntent(pm, target);
                    if (i == null) continue;
                    if (label.isEmpty()) label = appLabel(pm, target);
                    action = () -> launch(i);
                    break;
                }
                case "allapps":
                    action = this::showAllApps;
                    break;
                case "resetorder":
                    action = () -> {
                        prefs().edit().remove(PREF_ORDER).apply();
                        loadOrder();
                        buildGrid();
                        focusFirst();
                    };
                    break;
                default:
                    continue;
            }
            TextView item = label(label, 18, Color.WHITE, false);
            item.setPadding(dp(16), dp(12), dp(32), dp(12));
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackground(focusBg(0x00000000, 8));
            item.setOnClickListener(v -> {
                menu.dismiss();
                action.run();
            });
            list.addView(item, new LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT));
            if (first == null) first = item;
        }
        if (first == null) return;
        menu = new PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        menu.setBackgroundDrawable(null);
        menu.setOutsideTouchable(true);
        menu.showAsDropDown(menuButton, 0, dp(8), Gravity.END);
        View focus = first;
        list.post(focus::requestFocus);
    }

    private void showAllApps() {
        if (menu != null && menu.isShowing()) menu.dismiss();
        PackageManager pm = getPackageManager();
        List<String> all = launchablePackages(pm);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(48), dp(36), dp(48), dp(36));
        list.setClipChildren(false);
        list.addView(label("All apps", 22, Color.WHITE, true));
        LinearLayout row = null;
        View first = null;
        int cols = 5;
        for (int i = 0; i < all.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(16);
                list.addView(row, lp);
            }
            View t = appTile(pm, all.get(i), null, false);
            if (t == null) continue;
            row.addView(t);
            if (first == null) first = t;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.addView(list);
        scroll.setBackgroundColor(0xF205070D);

        allApps = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        allApps.setContentView(scroll);
        allApps.show();
        if (first != null) first.requestFocus();
    }

    private List<String> launchablePackages(PackageManager pm) {
        Set<String> pkgs = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) pkgs.add(ri.activityInfo.packageName);
        }
        pkgs.remove(getPackageName());
        List<String> keyed = new ArrayList<>();
        for (String p : pkgs) keyed.add(appLabel(pm, p).toLowerCase() + "\u0000" + p);
        Collections.sort(keyed);
        List<String> out = new ArrayList<>();
        for (String k : keyed) out.add(k.substring(k.indexOf('\u0000') + 1));
        return out;
    }

    // ---- tiles and helpers ----

    private View appTile(PackageManager pm, String pkg, String label, boolean isMoving) {
        Intent launch = launchIntent(pm, pkg);
        if (launch == null) return null;
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        String name = (label == null || label.isEmpty()) ? String.valueOf(pm.getApplicationLabel(ai)) : label;

        FrameLayout tile = new FrameLayout(this);
        tile.setId(View.generateViewId());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(name);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        tile.setClipToOutline(true);
        tile.setBackground(isMoving ? rounded(0xFF1C2028, MOVE_COLOR, 8, 4) : focusBg(0xFF1C2028, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        lp.setMargins(dp(8), 0, dp(8), 0);
        tile.setLayoutParams(lp);

        Drawable banner = pm.getApplicationBanner(ai);
        if (banner == null) {
            try {
                banner = pm.getActivityBanner(launch);
            } catch (PackageManager.NameNotFoundException ignored) {
                banner = null;
            }
        }
        if (banner != null) {
            ImageView img = new ImageView(this);
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
            tile.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView img = new ImageView(this);
            img.setImageDrawable(pm.getApplicationIcon(ai));
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER);
            ilp.bottomMargin = dp(14);
            tile.addView(img, ilp);
            TextView cap = label(name, 12, Color.WHITE, true);
            cap.setGravity(Gravity.CENTER);
            cap.setSingleLine(true);
            cap.setPadding(dp(6), 0, dp(6), dp(6));
            tile.addView(cap, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }
        if (isMoving) {
            tile.setScaleX(1.12f);
            tile.setScaleY(1.12f);
            tile.setElevation(dp(10));
        } else {
            tile.setOnFocusChangeListener((v, has) -> {
                float s = has ? 1.08f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(90).start();
                v.setElevation(has ? dp(8) : 0);
            });
        }
        tile.setOnClickListener(v -> launch(launch));
        return tile;
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return null;
        try {
            if (!pm.getApplicationInfo(pkg, 0).enabled) return null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        return i;
    }

    private static String appLabel(PackageManager pm, String pkg) {
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void launch(Intent intent) {
        Intent i = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private StateListDrawable focusBg(int color, int radiusDp) {
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = rounded(color == 0 ? 0x33FFFFFF : color, Color.WHITE, radiusDp, 3);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, rounded(color, 0, radiusDp));
        return bg;
    }

    private GradientDrawable rounded(int color, int stroke, int radiusDp) {
        return rounded(color, stroke, radiusDp, 1);
    }

    private GradientDrawable rounded(int color, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(strokeDp), stroke);
        return d;
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
