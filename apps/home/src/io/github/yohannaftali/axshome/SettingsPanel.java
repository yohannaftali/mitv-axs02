package io.github.yohannaftali.axshome;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
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

/**
 * "Home settings": clock format, wallpaper style, own wallpaper file, video
 * sound. Console-style layout: the live wallpaper behind a dimmed screen,
 * categories on the left, option cards on a translucent panel on the right,
 * with the same white focus ring and zoom as the app tiles.
 */
final class SettingsPanel {
    static final String PREF_CLOCK24 = "clock24";
    static final String PREF_WALLPAPER = "wallpaper";
    static final String PREF_CUSTOM_FILE = "customFile";
    static final String PREF_VIDEO_SOUND = "videoSound";
    static final String CUSTOM = "custom";

    private static final int CLOCK = 0;
    private static final int WALLPAPER = 1;
    private static final int SOUND = 2;
    private static final String[] CATEGORY = {"Clock", "Wallpaper", "Sound"};
    private static final int[] CATEGORY_ICON = {Glyph.CLOCK, Glyph.WALLPAPER, Glyph.SOUND};
    private static final String[] CATEGORY_HINT = {
            "How the time is shown on the home screen.",
            "Moves slowly behind the home screen. Pick one to see it here at once.",
            "Sound of video wallpapers you add yourself.",
    };

    private static final int RADIUS = 14;
    private static final int GAP = 5;
    private static final int DIM = 0x99FFFFFF;

    private final HomeActivity home;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final View[] cats = new View[CATEGORY.length];
    private final List<View> items = new ArrayList<>();
    private final List<StyleView> previews = new ArrayList<>();
    private Dialog dialog;
    private LinearLayout pane;
    private ScrollView paneScroll;
    private TextView paneTitle;
    private TextView paneHint;
    private int category;
    private boolean browsing;
    /** True while the panel is rebuilt: removing the focused card moves focus to a category, which must not re-render. */
    private boolean rendering;
    private File browseDir;

    SettingsPanel(HomeActivity home, SharedPreferences prefs) {
        this.home = home;
        this.prefs = prefs;
    }

    void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    void show() {
        dismiss();
        FrameLayout root = new FrameLayout(home);
        View scrim = new View(home);
        scrim.setBackgroundColor(0x8C05070D);
        root.addView(scrim, match());

        LinearLayout main = new LinearLayout(home);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setPadding(dp(56), dp(48), dp(56), dp(48));
        main.setClipChildren(false);
        main.setClipToPadding(false);
        root.addView(main, match());

        main.addView(categoryColumn(), new LinearLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        panelLp.leftMargin = dp(28);
        main.addView(panel(), panelLp);

        dialog = new Dialog(home, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0f);
        }
        dialog.setOnKeyListener((d, code, ev) -> onKey(code, ev));
        dialog.setOnDismissListener(d -> {
            releasePreviews();
            home.showHomeContent(true);
        });

        category = CLOCK;
        browsing = false;
        render(-1);
        home.showHomeContent(false);
        dialog.show();
        cats[CLOCK].requestFocus();
    }

    // ---- layout ----

    private View categoryColumn() {
        LinearLayout col = new LinearLayout(home);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        TextView over = home.label("AXS HOME", 13, DIM, true);
        over.setLetterSpacing(0.2f);
        col.addView(over);
        TextView title = home.label("Settings", 34, Color.WHITE, false);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        col.addView(title, top(2));

        for (int i = 0; i < CATEGORY.length; i++) {
            int index = i;
            LinearLayout row = new LinearLayout(home);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setFocusable(true);
            row.setClickable(true);
            row.addView(new Glyph(home, CATEGORY_ICON[i], Color.WHITE), new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView label = home.label(CATEGORY[i], 19, Color.WHITE, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(16);
            row.addView(label, lp);
            row.setOnFocusChangeListener((v, has) -> {
                if (rendering) return;
                if (has && (category != index || browsing)) {
                    category = index;
                    browsing = false;
                    render(-1);
                }
                styleCategories();
            });
            row.setOnClickListener(v -> focusPane(0));
            cats[i] = row;
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
            rlp.topMargin = dp(i == 0 ? 36 : 8);
            col.addView(row, rlp);
        }
        styleCategories();
        return col;
    }

    /** Focused: white ring; current category (focus in the panel): soft fill and accent bar. */
    private void styleCategories() {
        int accent = home.accent();
        for (int i = 0; i < cats.length; i++) {
            if (cats[i] == null) continue;
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, home.rounded(0x2EFFFFFF, Color.WHITE, RADIUS, 2));
            GradientDrawable idle = home.rounded(i == category ? 0x1AFFFFFF : 0x00000000, 0, RADIUS, 1);
            if (i == category) idle.setStroke(0, 0);
            bg.addState(new int[]{}, idle);
            cats[i].setBackground(bg);
            LinearLayout row = (LinearLayout) cats[i];
            ((Glyph) row.getChildAt(0)).setColor(i == category ? accent : 0xCCFFFFFF);
            ((TextView) row.getChildAt(1)).setTextColor(i == category ? Color.WHITE : 0xCCFFFFFF);
        }
    }

    private View panel() {
        FrameLayout panel = new FrameLayout(home);
        panel.setBackground(home.rounded(0xB30B0F18, 0x24FFFFFF, 24, 1));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(32), dp(28), dp(32), 0);
        // Clips the scrolling cards at the scroll view's edge; its padding leaves room for the ring and zoom.
        inner.setClipChildren(true);
        inner.setClipToPadding(false);
        paneTitle = home.label("", 26, Color.WHITE, false);
        paneTitle.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        inner.addView(paneTitle);
        paneHint = home.label("", 14, DIM, false);
        paneHint.setSingleLine(true);
        paneHint.setEllipsize(TextUtils.TruncateAt.START);
        inner.addView(paneHint, top(4));

        paneScroll = new ScrollView(home);
        paneScroll.setClipChildren(false);
        paneScroll.setClipToPadding(false);
        paneScroll.setVerticalScrollBarEnabled(false);
        // A soft fade at the scroll edges; ScrollView also keeps the focused card this far from the edge.
        paneScroll.setVerticalFadingEdgeEnabled(true);
        paneScroll.setFadingEdgeLength(dp(28));
        paneScroll.setPadding(dp(8), dp(12), dp(8), 0);
        pane = new LinearLayout(home);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(0, 0, 0, dp(20));
        pane.setClipChildren(false);
        pane.setClipToPadding(false);
        paneScroll.addView(pane);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(10);
        lp.leftMargin = -dp(8);
        lp.rightMargin = -dp(8);
        inner.addView(paneScroll, lp);
        panel.addView(inner, match());
        return panel;
    }

    /** Rebuilds the right panel; focusIndex >= 0 puts focus back on that card. */
    private void render(int focusIndex) {
        boolean paneHadFocus = dialog != null && inPane(dialog.getCurrentFocus());
        rendering = true;
        releasePreviews();
        pane.removeAllViews();
        items.clear();
        if (browsing) {
            renderBrowser();
        } else {
            paneTitle.setText(CATEGORY[category]);
            paneHint.setText(CATEGORY_HINT[category]);
            if (category == CLOCK) renderClock();
            else if (category == WALLPAPER) renderWallpaper();
            else renderSound();
        }
        styleCategories();
        pane.setAlpha(0f);
        pane.setTranslationX(dp(12));
        pane.animate().alpha(1f).translationX(0).setDuration(140).start();
        rendering = false;
        if (focusIndex >= 0) focusPane(focusIndex);
        else if (paneHadFocus) cats[category].requestFocus();
    }

    private void renderClock() {
        boolean is24 = home.clockIs24();
        LinearLayout row = row();
        row.addView(clockCard("HH:mm", "24-hour", is24, () -> setClock(true)));
        row.addView(space(16));
        row.addView(clockCard("h:mm a", "12-hour with AM/PM", !is24, () -> setClock(false)));
        pane.addView(row);
    }

    private View clockCard(String format, String caption, boolean selected, Runnable pick) {
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        TextClock t = new TextClock(home);
        t.setFormat24Hour(format);
        t.setFormat12Hour(format);
        t.setTextColor(Color.WHITE);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 40);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        inner.addView(t, top(0));
        inner.addView(home.label(caption, 14, DIM, false), top(6));
        return card(inner, 214, 132, selected, pick);
    }

    private void renderWallpaper() {
        String current = home.currentWallpaper();
        List<View> cards = new ArrayList<>();
        for (String s : StyleView.ALL) {
            FrameLayout inner = new FrameLayout(home);
            StyleView preview = home.preview(s);
            previews.add(preview);
            inner.addView(preview, match());
            inner.addView(caption(StyleView.title(s)), bottom());
            View c = card(inner, 214, 120, current.equals(s), () -> setWallpaper(s));
            c.setOnFocusChangeListener(chain(c.getOnFocusChangeListener(), (v, has) -> {
                if (has) preview.start(); else preview.stop();
            }));
            cards.add(c);
        }
        File custom = customFile();
        if (custom != null) {
            FrameLayout inner = new FrameLayout(home);
            ImageView thumb = new ImageView(home);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            inner.addView(thumb, match());
            inner.addView(caption("Your file"), bottom());
            loadThumb(custom, thumb);
            cards.add(card(inner, 214, 120, current.equals(CUSTOM), () -> setWallpaper(CUSTOM)));
        }
        LinearLayout choose = new LinearLayout(home);
        choose.setOrientation(LinearLayout.VERTICAL);
        choose.setGravity(Gravity.CENTER);
        choose.addView(new Glyph(home, Glyph.FOLDER, Color.WHITE), new LinearLayout.LayoutParams(dp(34), dp(34)));
        choose.addView(home.label("Choose a file…", 15, Color.WHITE, false), top(8));
        choose.addView(home.label("USB, Download, Pictures, Movies", 12, DIM, false), top(2));
        cards.add(card(choose, 214, 120, false, this::chooseFile));

        LinearLayout row = null;
        for (int i = 0; i < cards.size(); i++) {
            if (i % 2 == 0) {
                row = row();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = dp(14);
                pane.addView(row, lp);
            } else {
                row.addView(space(16));
            }
            row.addView(cards.get(i));
        }
    }

    private void renderSound() {
        boolean on = prefs.getBoolean(PREF_VIDEO_SOUND, false);
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout texts = new LinearLayout(home);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(home.label("Play sound of video wallpapers", 17, Color.WHITE, false));
        texts.addView(home.label(on ? "On" : "Off — videos play silently", 13, DIM, false), top(4));
        inner.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inner.addView(toggle(on), new LinearLayout.LayoutParams(dp(52), dp(30)));
        pane.addView(card(inner, -1, 84, false, () -> {
            prefs.edit().putBoolean(PREF_VIDEO_SOUND, !on).apply();
            home.applyWallpaper();
            render(0);
        }));
    }

    private View toggle(boolean on) {
        FrameLayout track = new FrameLayout(home);
        track.setBackground(home.rounded(on ? home.accent() : 0x40FFFFFF, 0, 15, 1));
        View thumb = new View(home);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Color.WHITE);
        thumb.setBackground(dot);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(24), dp(24),
                Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START));
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        track.addView(thumb, lp);
        return track;
    }

    // ---- cards and focus ----

    /**
     * A card: the body is clipped to rounded corners; the white focus ring sits
     * outside it with a small gap (as on the app tiles), and the card zooms in.
     * Selected cards get an accent border and a check mark.
     */
    private View card(View content, int wDp, int hDp, boolean selected, Runnable onClick) {
        int accent = home.accent();
        FrameLayout ring = new FrameLayout(home);
        ring.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        ring.setFocusable(true);
        ring.setClickable(true);
        ring.setClipChildren(false);
        StateListDrawable ringBg = new StateListDrawable();
        ringBg.addState(new int[]{android.R.attr.state_focused}, home.rounded(0, Color.WHITE, RADIUS + GAP, 3));
        ringBg.addState(new int[]{}, home.rounded(0, 0, RADIUS + GAP, 1));
        ring.setBackground(ringBg);

        FrameLayout body = new FrameLayout(home);
        body.setBackground(home.rounded(selected ? (accent & 0x00FFFFFF) | 0x33000000 : 0x1FFFFFFF,
                selected ? accent : 0x1AFFFFFF, RADIUS, selected ? 2 : 1));
        body.setClipToOutline(true);
        body.addView(content, match());
        if (selected) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
            lp.topMargin = dp(8);
            lp.rightMargin = dp(8);
            body.addView(new Glyph(home, Glyph.CHECK, accent), lp);
        }
        ring.addView(body, new FrameLayout.LayoutParams(
                wDp < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : dp(wDp), dp(hDp)));
        ring.setOnClickListener(v -> onClick.run());
        ring.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(120).start();
            v.setElevation(dp(has ? 12 : 0));
        });
        items.add(ring);
        return ring;
    }

    private static View.OnFocusChangeListener chain(View.OnFocusChangeListener a, View.OnFocusChangeListener b) {
        return (v, has) -> {
            if (a != null) a.onFocusChange(v, has);
            b.onFocusChange(v, has);
        };
    }

    private void focusPane(int index) {
        if (items.isEmpty()) return;
        View v = items.get(Math.max(0, Math.min(index, items.size() - 1)));
        pane.post(v::requestFocus);
    }

    private boolean inPane(View v) {
        for (ViewParent p = v == null ? null : v.getParent(); p != null; p = p.getParent()) {
            if (p == pane) return true;
        }
        return false;
    }

    private boolean isCategory(View v) {
        for (View c : cats) if (c == v) return true;
        return false;
    }

    private boolean onKey(int code, KeyEvent ev) {
        View f = dialog.getCurrentFocus();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() != KeyEvent.ACTION_UP) return true;
            if (browsing) {
                if (browseDir == null) {
                    browsing = false;
                    render(Integer.MAX_VALUE);
                } else {
                    browse(isRoot(browseDir) ? null : browseDir.getParentFile());
                }
            } else if (inPane(f)) {
                cats[category].requestFocus();
            } else {
                dialog.dismiss();
            }
            return true;
        }
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (isCategory(f)) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusPane(0);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                View next = f.focusSearch(code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
                return next == null || !isCategory(next);
            }
            return code == KeyEvent.KEYCODE_DPAD_LEFT;
        }
        if (inPane(f) && code == KeyEvent.KEYCODE_DPAD_LEFT) {
            View next = f.focusSearch(View.FOCUS_LEFT);
            if (next == null || !inPane(next)) {
                cats[category].requestFocus();
                return true;
            }
        }
        return false;
    }

    // ---- small view helpers ----

    private TextView caption(String text) {
        TextView t = home.label(text, 14, Color.WHITE, true);
        t.setPadding(dp(12), dp(18), dp(12), dp(8));
        t.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xB3000000, 0x00000000}));
        return t;
    }

    private static FrameLayout.LayoutParams bottom() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams top(int dpMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpMargin);
        return lp;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(home);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private View space(int dpWidth) {
        View s = new View(home);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(dpWidth), 1));
        return s;
    }

    private int dp(int v) {
        return home.dp(v);
    }

    private void releasePreviews() {
        for (StyleView p : previews) p.release();
        previews.clear();
    }

    /** Small still of the owner's file, decoded off the UI thread. */
    private void loadThumb(File f, ImageView into) {
        int w = dp(214);
        int h = dp(120);
        new Thread(() -> {
            try {
                Object out;
                if (MediaBackdrop.isVideo(f.getName())) {
                    out = ThumbnailUtils.createVideoThumbnail(f, new Size(w, h), null);
                } else {
                    out = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (dec, info, src) -> {
                        float s = Math.max((float) w / info.getSize().getWidth(), (float) h / info.getSize().getHeight());
                        dec.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * Math.min(1f, s))),
                                Math.max(1, Math.round(info.getSize().getHeight() * Math.min(1f, s))));
                    });
                }
                Object bitmap = out;
                ui.post(() -> into.setImageBitmap((android.graphics.Bitmap) bitmap));
            } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
                // No thumbnail; the card still shows its caption.
            }
        }, "wallpaper-thumb").start();
    }

    // ---- actions ----

    private void setClock(boolean is24) {
        prefs.edit().putBoolean(PREF_CLOCK24, is24).apply();
        home.applyClockFormats();
        render(is24 ? 0 : 1);
    }

    private void setWallpaper(String style) {
        int index = items.indexOf(dialog.getCurrentFocus());
        prefs.edit().putString(PREF_WALLPAPER, style).apply();
        home.applyWallpaper();
        render(Math.max(0, index));
    }

    File customFile() {
        String path = prefs.getString(PREF_CUSTOM_FILE, null);
        if (path == null) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    // ---- file browser (inside the panel) ----

    private void chooseFile() {
        home.withStoragePermission(() -> browse(null), () ->
                Toast.makeText(home, "Storage permission is needed to browse files", Toast.LENGTH_LONG).show());
    }

    /** dir == null lists the roots: USB drives, then Download / Pictures / Movies / DCIM. */
    private void browse(File dir) {
        browsing = true;
        browseDir = dir;
        render(0);
    }

    private void renderBrowser() {
        File dir = browseDir;
        paneTitle.setText("Choose a wallpaper");
        paneHint.setText(dir == null ? "PNG, JPEG, WebP, GIF, animated WebP, MP4 · BACK to go back" : dir.getPath());
        if (dir == null) {
            for (String[] root : roots()) {
                File f = new File(root[1]);
                pane.addView(fileRow(Glyph.FOLDER, root[0], () -> browse(f)), rowLp());
            }
            if (items.isEmpty()) pane.addView(home.label("No USB drive or media folder found.", 16, DIM, false));
            return;
        }
        boolean atRoot = isRoot(dir);
        File parent = dir.getParentFile();
        pane.addView(fileRow(Glyph.UP, "Up", () -> browse(atRoot ? null : parent)), rowLp());
        File[] children = dir.listFiles();
        int shown = 0;
        if (children != null) {
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT));
            });
            for (File f : children) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) {
                    pane.addView(fileRow(Glyph.FOLDER, f.getName(), () -> browse(f)), rowLp());
                    shown++;
                } else if (MediaBackdrop.supported(f.getName())) {
                    pane.addView(fileRow(Glyph.FILE, f.getName(), () -> pick(f)), rowLp());
                    shown++;
                }
            }
        }
        if (shown == 0) {
            TextView empty = home.label("No folders or supported files here.", 16, DIM, false);
            empty.setPadding(dp(16), dp(12), 0, 0);
            pane.addView(empty);
        }
    }

    private View fileRow(int glyph, String name, Runnable onClick) {
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(16), 0, dp(16), 0);
        inner.addView(new Glyph(home, glyph, 0xE6FFFFFF), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView t = home.label(name, 17, Color.WHITE, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(14);
        inner.addView(t, lp);
        View c = card(inner, -1, 52, false, onClick);
        c.setOnFocusChangeListener((v, has) -> v.setElevation(dp(has ? 8 : 0)));
        return c;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        return lp;
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
                browsing = false;
                category = WALLPAPER;
                render(StyleView.ALL.length);
            });
        }, "wallpaper-copy").start();
    }
}
