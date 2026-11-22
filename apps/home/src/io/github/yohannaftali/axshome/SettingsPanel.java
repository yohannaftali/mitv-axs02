package io.github.yohannaftali.axshome;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** "Home settings": clock format, wallpaper style, own wallpaper file, video sound. */
final class SettingsPanel {
    static final String PREF_CLOCK24 = "clock24";
    static final String PREF_WALLPAPER = "wallpaper";
    static final String PREF_CUSTOM_FILE = "customFile";
    static final String PREF_VIDEO_SOUND = "videoSound";
    static final String CUSTOM = "custom";

    private final HomeActivity home;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Dialog dialog;
    private LinearLayout list;
    private int focusIndex;

    SettingsPanel(HomeActivity home, SharedPreferences prefs) {
        this.home = home;
        this.prefs = prefs;
    }

    void show() {
        ScrollView scroll = new ScrollView(home);
        scroll.setBackgroundColor(0xF205070D);
        list = new LinearLayout(home);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(home.dp(96), home.dp(36), home.dp(96), home.dp(36));
        scroll.addView(list);
        dialog = new Dialog(home, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.setContentView(scroll);
        focusIndex = 0;
        build();
        dialog.show();
    }

    private void build() {
        list.removeAllViews();
        List<View> rows = new ArrayList<>();
        list.addView(home.label("Home settings", 26, Color.WHITE, true));

        header("Clock");
        boolean is24 = home.clockIs24();
        rows.add(option("24-hour  (15:30)", is24, () -> setClock(true)));
        rows.add(option("12-hour with AM/PM  (3:30 PM)", !is24, () -> setClock(false)));

        header("Wallpaper");
        String current = home.currentWallpaper();
        for (String s : StyleView.ALL) {
            rows.add(option(StyleView.title(s), current.equals(s), () -> setWallpaper(s)));
        }
        File custom = customFile();
        if (custom != null) {
            rows.add(option("Your file: " + custom.getName().replaceFirst("^custom\\.", "wallpaper."),
                    current.equals(CUSTOM), () -> setWallpaper(CUSTOM)));
        }
        rows.add(action("Choose a file…  (USB drive, Download, Pictures, Movies)", this::chooseFile));
        boolean sound = prefs.getBoolean(PREF_VIDEO_SOUND, false);
        rows.add(option("Play sound of video wallpapers", sound, () -> {
            prefs.edit().putBoolean(PREF_VIDEO_SOUND, !sound).apply();
            home.applyWallpaper();
        }));

        for (int i = 0; i < rows.size(); i++) {
            int index = i;
            rows.get(i).setOnFocusChangeListener((v, has) -> { if (has) focusIndex = index; });
        }
        View focus = rows.get(Math.min(focusIndex, rows.size() - 1));
        list.post(focus::requestFocus);
    }

    private void header(String text) {
        TextView t = home.label(text, 15, 0x99FFFFFF, true);
        t.setPadding(home.dp(8), home.dp(24), 0, home.dp(6));
        list.addView(t);
    }

    private View option(String text, boolean selected, Runnable onPick) {
        return row((selected ? "●  " : "○  ") + text, () -> {
            onPick.run();
            build();
        });
    }

    private View action(String text, Runnable onPick) {
        return row("→  " + text, onPick);
    }

    private View row(String text, Runnable onClick) {
        TextView t = home.label(text, 18, Color.WHITE, false);
        t.setPadding(home.dp(16), home.dp(12), home.dp(16), home.dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(home.focusBg(0x00000000, 8));
        t.setOnClickListener(v -> onClick.run());
        list.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private void setClock(boolean is24) {
        prefs.edit().putBoolean(PREF_CLOCK24, is24).apply();
        home.applyClockFormats();
    }

    private void setWallpaper(String style) {
        prefs.edit().putString(PREF_WALLPAPER, style).apply();
        home.applyWallpaper();
    }

    File customFile() {
        String path = prefs.getString(PREF_CUSTOM_FILE, null);
        if (path == null) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    // ---- file browser ----

    private void chooseFile() {
        home.withStoragePermission(() -> browse(null), () ->
                Toast.makeText(home, "Storage permission is needed to browse files", Toast.LENGTH_LONG).show());
    }

    /** dir == null lists the roots: USB drives, then Download / Pictures / Movies / DCIM. */
    private void browse(File dir) {
        list.removeAllViews();
        List<View> rows = new ArrayList<>();
        list.addView(home.label(dir == null ? "Choose a wallpaper" : dir.getPath(), 22, Color.WHITE, true));
        TextView hint = home.label("PNG, JPEG, WebP, GIF, animated WebP, MP4. BACK returns to Home settings.", 14, 0x99FFFFFF, false);
        hint.setPadding(home.dp(8), home.dp(6), 0, home.dp(12));
        list.addView(hint);

        if (dir == null) {
            for (String[] root : roots()) {
                File f = new File(root[1]);
                rows.add(row("📁  " + root[0], () -> browse(f)));
            }
        } else {
            File parent = dir.getParentFile();
            boolean atRoot = isRoot(dir);
            rows.add(row("←  Up", () -> browse(atRoot ? null : parent)));
            File[] children = dir.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT));
                });
                for (File f : children) {
                    if (f.getName().startsWith(".")) continue;
                    if (f.isDirectory()) rows.add(row("📁  " + f.getName(), () -> browse(f)));
                    else if (MediaBackdrop.supported(f.getName())) rows.add(row("🖼  " + f.getName(), () -> pick(f)));
                }
            }
            if (rows.size() == 1) {
                TextView empty = home.label("No folders or supported files here.", 16, 0x99FFFFFF, false);
                empty.setPadding(home.dp(16), home.dp(12), 0, 0);
                list.addView(empty);
            }
        }
        if (rows.isEmpty()) {
            list.addView(home.label("No USB drive or media folder found.", 16, 0x99FFFFFF, false));
        } else {
            View first = rows.get(0);
            list.post(first::requestFocus);
        }
        dialog.setOnKeyListener((d, code, ev) -> {
            if (code == android.view.KeyEvent.KEYCODE_BACK && ev.getAction() == android.view.KeyEvent.ACTION_UP) {
                dialog.setOnKeyListener(null);
                build();
                return true;
            }
            return code == android.view.KeyEvent.KEYCODE_BACK;
        });
    }

    private final List<String> rootPaths = new ArrayList<>();

    private boolean isRoot(File dir) {
        return rootPaths.contains(dir.getPath());
    }

    private List<String[]> roots() {
        List<String[]> out = new ArrayList<>();
        rootPaths.clear();
        StorageManager sm = (StorageManager) home.getSystemService(Context.STORAGE_SERVICE);
        if (sm != null) {
            for (StorageVolume v : sm.getStorageVolumes()) {
                File d = v.getDirectory();
                if (v.isRemovable() && d != null && Environment.MEDIA_MOUNTED.equals(v.getState())) {
                    out.add(new String[]{"USB: " + v.getDescription(home), d.getPath()});
                }
            }
        }
        File primary = Environment.getExternalStorageDirectory();
        for (String name : new String[]{Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DCIM}) {
            File d = new File(primary, name);
            if (d.isDirectory()) out.add(new String[]{name, d.getPath()});
        }
        for (String[] r : out) rootPaths.add(r[1]);
        return out;
    }

    /** Copies the file into the app's own folder, so it keeps working without the USB drive. */
    private void pick(File source) {
        Toast.makeText(home, "Copying " + source.getName() + "…", Toast.LENGTH_SHORT).show();
        File dir = home.getExternalFilesDir("wallpaper");
        if (dir == null) {
            Toast.makeText(home, "Cannot copy: no app storage", Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            File tmp = new File(dir, "incoming.tmp");
            File dest = new File(dir, "custom." + MediaBackdrop.ext(source.getName()));
            try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[256 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (IOException e) {
                tmp.delete();
                ui.post(() -> Toast.makeText(home, "Cannot copy " + source.getName(), Toast.LENGTH_LONG).show());
                return;
            }
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) if (f.getName().startsWith("custom.")) f.delete();
            if (!tmp.renameTo(dest)) {
                tmp.delete();
                ui.post(() -> Toast.makeText(home, "Cannot save " + source.getName(), Toast.LENGTH_LONG).show());
                return;
            }
            ui.post(() -> {
                prefs.edit().putString(PREF_CUSTOM_FILE, dest.getPath()).putString(PREF_WALLPAPER, CUSTOM).apply();
                home.applyWallpaper();
                dialog.setOnKeyListener(null);
                focusIndex = 0;
                build();
            });
        }, "wallpaper-copy").start();
    }
}
