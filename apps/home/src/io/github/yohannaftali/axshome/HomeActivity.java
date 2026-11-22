package io.github.yohannaftali.axshome;

import android.Manifest;
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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
    private static final int TILE_RADIUS = 12;
    private static final int RING_GAP = 5;
    private static final int TILE_SPACING = 16;
    private static final int SCROLL_BLEED = 12;
    private static final int TILE_W = 148;
    private static final int TILE_H = 83;
    private static final int DOT_ON = 0xFF3DDC84;
    private static final int DOT_OFF = 0xFF5F6673;
    private static final int BG = 0xFF05070D;
    private static final int MOVE_COLOR = 0xFFFFC233;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable startWaves = this::startWaves;

    private FrameLayout root;
    private Backdrop backdrop;
    private SettingsPanel settingsPanel;
    private String confStyle = StyleView.WAVES;
    private LinearLayout content;
    private LinearLayout grid;
    private LinearLayout inputBar;
    private final Map<String, View> dots = new LinkedHashMap<>();
    private TvInputManager tvInputs;
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String inputId, int state) {
            View dot = dots.get(inputId);
            if (dot != null) paintDot(dot, state);
        }
    };
    private TextView menuButton;
    private NetIcon net;
    private ConnectivityManager connectivity;
    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            showNetwork(caps);
        }

        @Override
        public void onLost(Network network) {
            showNetwork(null);
        }
    };
    private TextClock clock;
    private TextClock date;
    private String timeFormat = "HH:mm";
    private String dateFormat = "EEEE, d MMMM";
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

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(28), dp(48), dp(24));
        content.setClipToPadding(false);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content.addView(topBar());

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridLp.topMargin = dp(28);
        content.addView(grid, gridLp);

        inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.VERTICAL);
        content.addView(inputBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        tvInputs = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

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
        clock = new TextClock(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        when.addView(clock);
        date = new TextClock(this);
        applyClockFormats();
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
        net = new NetIcon(this);
        LinearLayout.LayoutParams netLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        netLp.rightMargin = dp(16);
        bar.addView(net, netLp);
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
        if (tvInputs != null) {
            tvInputs.registerCallback(inputCallback, ui);
        }
        if (connectivity != null) {
            Network active = connectivity.getActiveNetwork();
            showNetwork(active == null ? null : connectivity.getNetworkCapabilities(active));
            connectivity.registerDefaultNetworkCallback(netCallback, ui);
        }
        if (tvInputs != null) {
            for (Map.Entry<String, View> d : dots.entrySet()) paintDot(d.getValue(), inputState(d.getKey()));
        }
        // The waves start only after the first frame, so they never delay the home screen.
        root.post(() -> ui.postDelayed(startWaves, 350));
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (tvInputs != null) tvInputs.unregisterCallback(inputCallback);
        if (connectivity != null) {
            try {
                connectivity.unregisterNetworkCallback(netCallback);
            } catch (IllegalArgumentException ignored) {
                // Not registered (onResume did not run).
            }
        }
        ui.removeCallbacks(startWaves);
        if (backdrop != null) backdrop.stop();
        super.onPause();
    }

    private void startWaves() {
        if (!resumed) return;
        if (backdrop == null) {
            backdrop = createBackdrop();
            View v = backdrop.view();
            v.setAlpha(0f);
            root.addView(v, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            v.animate().alpha(1f).setDuration(400).start();
        }
        backdrop.start();
    }

    // ---- wallpaper and clock settings (Home settings) ----

    String currentWallpaper() {
        String s = prefs().getString(SettingsPanel.PREF_WALLPAPER, confStyle);
        if (SettingsPanel.CUSTOM.equals(s) && settings().customFile() == null) return confStyle;
        return SettingsPanel.CUSTOM.equals(s) || StyleView.known(s) ? s : confStyle;
    }

    private Backdrop createBackdrop() {
        String style = currentWallpaper();
        if (SettingsPanel.CUSTOM.equals(style)) {
            boolean sound = prefs().getBoolean(SettingsPanel.PREF_VIDEO_SOUND, false);
            return new MediaBackdrop(this, settings().customFile(), sound, () -> {
                Toast.makeText(this, "Cannot play this wallpaper file; using " + StyleView.title(confStyle),
                        Toast.LENGTH_LONG).show();
                File bad = settings().customFile();
                if (bad != null && !bad.delete()) bad.deleteOnExit();
                prefs().edit().putString(SettingsPanel.PREF_WALLPAPER, confStyle)
                        .remove(SettingsPanel.PREF_CUSTOM_FILE).apply();
                applyWallpaper();
            });
        }
        return new StyleView(this, style, waveTop, waveAccent);
    }

    /** Replace the backdrop after a settings or config change. */
    void applyWallpaper() {
        if (backdrop != null) {
            root.removeView(backdrop.view());
            backdrop.release();
            backdrop = null;
        }
        if (resumed) startWaves();
    }

    /** Home settings shows only the wallpaper behind it, like a console's settings screen. */
    void showHomeContent(boolean visible) {
        content.animate().alpha(visible ? 1f : 0f).setDuration(150).start();
    }

    /** A small, still copy of a built-in style for Home settings; animates only while started. */
    StyleView preview(String style) {
        return new StyleView(this, style, waveTop, waveAccent);
    }

    /** Accent colour of the current wallpaper, used by Home settings. */
    int accent() {
        switch (currentWallpaper()) {
            case StyleView.ORBIT: return 0xFF3BD16F;
            case StyleView.POP: return 0xFFFF5A64;
            case StyleView.BLOOM: return 0xFF6FB6FF;
            case StyleView.WAVES: return waveAccent;
            default: return 0xFF3D7BFF;
        }
    }

    boolean clockIs24() {
        if (prefs().contains(SettingsPanel.PREF_CLOCK24)) return prefs().getBoolean(SettingsPanel.PREF_CLOCK24, true);
        return timeFormat.contains("H") || timeFormat.contains("k");
    }

    private SettingsPanel settings() {
        if (settingsPanel == null) settingsPanel = new SettingsPanel(this, prefs());
        return settingsPanel;
    }

    private static final int REQ_STORAGE = 1;
    private Runnable storageGranted;
    private Runnable storageDenied;

    void withStoragePermission(Runnable granted, Runnable denied) {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            granted.run();
            return;
        }
        storageGranted = granted;
        storageDenied = denied;
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQ_STORAGE) return;
        Runnable next = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED ? storageGranted : storageDenied;
        storageGranted = null;
        storageDenied = null;
        if (next != null) next.run();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) return;
        if (menu != null && menu.isShowing()) menu.dismiss();
        if (allApps != null && allApps.isShowing()) allApps.dismiss();
        if (settingsPanel != null) settingsPanel.dismiss();
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
        applyClockFormats();
        if (backdrop != null) {
            root.removeView(backdrop.view());
            backdrop.release();
            backdrop = null;
        }
        loadOrder();
        buildGrid();
        buildInputs();
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
                case "clock":
                    timeFormat = field(e, 1).isEmpty() ? "HH:mm" : field(e, 1);
                    dateFormat = field(e, 2).isEmpty() ? "EEEE, d MMMM" : field(e, 2);
                    break;
                case "wallpaper":
                    if (StyleView.known(field(e, 1))) confStyle = field(e, 1);
                    waveTop = parseColor(field(e, 2), waveTop);
                    waveAccent = parseColor(field(e, 3), waveAccent);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Same format for both system modes: the TV has no 12/24-hour preference set.
     * A choice made in Home settings wins over the home.conf format.
     */
    void applyClockFormats() {
        String time = timeFormat;
        if (prefs().contains(SettingsPanel.PREF_CLOCK24)) {
            time = prefs().getBoolean(SettingsPanel.PREF_CLOCK24, true) ? "HH:mm" : "h:mm a";
        }
        clock.setFormat12Hour(time);
        clock.setFormat24Hour(time);
        date.setFormat12Hour(dateFormat);
        date.setFormat24Hour(dateFormat);
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

    /**
     * One row of tiles. Flexible spacers between tiles spread them evenly
     * across the width when they fit; when they do not, the spacers shrink to
     * their minimum and the row scrolls sideways to follow the focus.
     */
    private void buildGrid() {
        grid.removeAllViews();
        tiles.clear();
        PackageManager pm = getPackageManager();

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        // Reach into the page margin so the zoomed edge tiles are not cut off.
        scroll.setPadding(dp(SCROLL_BLEED), dp(14), dp(SCROLL_BLEED), dp(14));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.leftMargin = -dp(SCROLL_BLEED);
        scrollLp.rightMargin = -dp(SCROLL_BLEED);
        grid.addView(scroll, scrollLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        scroll.addView(row);

        for (int i = 0; i < order.size(); i++) {
            String pkg = order.get(i);
            View t = appTile(pm, pkg, apps.get(pkg), pkg.equals(moving));
            if (t == null) continue;
            View ring = ringOf(t);
            ring.setOnLongClickListener(v -> {
                startMove(pkg);
                return true;
            });
            if (row.getChildCount() > 0) {
                row.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(dp(TILE_SPACING), 1, 1f));
            }
            row.addView(t);
            tiles.put(pkg, ring);
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
        else if (!dots.isEmpty()) ((View) dots.values().iterator().next().getParent()).requestFocus();
        else menuButton.requestFocus();
    }

    // ---- network status ----

    private void showNetwork(NetworkCapabilities caps) {
        if (caps == null) {
            net.set(NetIcon.OFFLINE, false, 0);
            return;
        }
        boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            net.set(NetIcon.WIFI, validated, wifiBars(caps.getSignalStrength()));
        } else {
            net.set(NetIcon.ETHERNET, validated, 0);
        }
    }

    private static int wifiBars(int rssi) {
        if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) return 4;
        if (rssi >= -55) return 4;
        if (rssi >= -66) return 3;
        if (rssi >= -77) return 2;
        if (rssi >= -88) return 1;
        return 0;
    }

    // ---- inputs bar (bottom) ----

    private void buildInputs() {
        inputBar.removeAllViews();
        dots.clear();
        TextView title = new TextView(this);
        title.setText("Inputs");
        title.setTextColor(0x99FFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, dp(8));
        inputBar.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        inputBar.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (String[] e : inputs) {
            String label = field(e, 1);
            String port = field(e, 2);
            String id = field(e, 3);
            if (tvInputs != null && tvInputs.getTvInputInfo(id) == null) continue;
            int color = parseColor(field(e, 5), 0xFF5A6478);
            row.addView(inputCard(label, port, id, IconView.parse(field(e, 4), port), color));
        }
        inputBar.setVisibility(row.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private View inputCard(String label, String port, String id, int icon, int color) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(focusBg(0x8C10141C, 12));
        card.setContentDescription(label.isEmpty() ? port : label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f);
        lp.setMargins(dp(8), 0, dp(8), 0);
        card.setLayoutParams(lp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(new IconView(this, icon, color), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, dp(14), 0);
        TextView name = label(label.isEmpty() ? port : label, 17, Color.WHITE, true);
        name.setMaxLines(1);
        name.setAutoSizeTextTypeUniformWithConfiguration(11, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        text.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        if (!label.isEmpty() && !port.isEmpty()) text.addView(label(port, 13, 0xAAFFFFFF, false));
        content.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View dot = new View(this);
        paintDot(dot, inputState(id));
        card.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP | Gravity.END));
        dots.put(id, dot);

        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? 1.05f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(90).start();
        });
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        card.setOnClickListener(v -> launch(view));
        return card;
    }

    private int inputState(String id) {
        try {
            return tvInputs == null ? TvInputManager.INPUT_STATE_DISCONNECTED : tvInputs.getInputState(id);
        } catch (IllegalArgumentException e) {
            return TvInputManager.INPUT_STATE_DISCONNECTED;
        }
    }

    /** Green only while the input sends a picture; this TV reports standby even for an empty AV port. */
    private void paintDot(View dot, int state) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(state == TvInputManager.INPUT_STATE_CONNECTED ? DOT_ON : DOT_OFF);
        dot.setBackground(d);
        dot.setContentDescription(state == TvInputManager.INPUT_STATE_CONNECTED ? "connected" : "no signal");
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
                case "settings":
                    action = () -> settings().show();
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
        list.setClipToPadding(false);
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
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.rightMargin = dp(TILE_SPACING);
            row.addView(t, tlp);
            if (first == null) first = ringOf(t);
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

    /**
     * A tile is a column: a ring frame (the focusable part) around a rounded
     * banner card, and the app name below, shown only while focused. The ring
     * sits outside the card with a gap, so it stays visible on white banners.
     * Callers that need the focusable view take {@link #ringOf(View)}.
     */
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

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        FrameLayout ring = new FrameLayout(this);
        ring.setId(View.generateViewId());
        ring.setFocusable(true);
        ring.setClickable(true);
        ring.setContentDescription(name);
        ring.setClipChildren(false);
        ring.setPadding(dp(RING_GAP), dp(RING_GAP), dp(RING_GAP), dp(RING_GAP));
        StateListDrawable ringBg = new StateListDrawable();
        GradientDrawable on = rounded(0x00000000, isMoving ? MOVE_COLOR : Color.WHITE, TILE_RADIUS + RING_GAP, 3);
        ringBg.addState(new int[]{android.R.attr.state_focused}, on);
        ringBg.addState(new int[]{}, isMoving ? on : rounded(0x00000000, 0, TILE_RADIUS + RING_GAP));
        ring.setBackground(ringBg);
        column.addView(ring, new LinearLayout.LayoutParams(
                dp(TILE_W + 2 * RING_GAP), dp(TILE_H + 2 * RING_GAP)));

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(rounded(0xFF1C2028, 0, TILE_RADIUS));
        tile.setClipToOutline(true);
        tile.setElevation(dp(6));
        ring.addView(tile, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView caption = label(name, 15, Color.WHITE, true);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setAlpha(isMoving ? 1f : 0f);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                dp(TILE_W + 2 * RING_GAP), ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(12);
        column.addView(caption, capLp);

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
            ring.setScaleX(1.1f);
            ring.setScaleY(1.1f);
        } else {
            ring.setOnFocusChangeListener((v, has) -> {
                float s = has ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(110).start();
                tile.setElevation(dp(has ? 14 : 6));
                caption.animate().alpha(has ? 1f : 0f).setDuration(110).start();
            });
        }
        ring.setOnClickListener(v -> launch(launch));
        return column;
    }

    private static View ringOf(View tileColumn) {
        return ((ViewGroup) tileColumn).getChildAt(0);
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

    TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    StateListDrawable focusBg(int color, int radiusDp) {
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = rounded(color == 0 ? 0x33FFFFFF : color, Color.WHITE, radiusDp, 3);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, rounded(color, 0, radiusDp));
        return bg;
    }

    private GradientDrawable rounded(int color, int stroke, int radiusDp) {
        return rounded(color, stroke, radiusDp, 1);
    }

    GradientDrawable rounded(int color, int stroke, int radiusDp, int strokeDp) {
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

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
