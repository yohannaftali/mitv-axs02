package io.github.yohannaftali.axshome;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Home settings": clock format, wallpaper style, own wallpaper file, video
 * sound, input card names, and the AXS Clock screensaver (tickers, data sources, refresh). Console-style layout: the live wallpaper behind a dimmed screen,
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
    private static final int INPUTS = 3;
    private static final int SCREENSAVER = 4;
    private static final String[] CATEGORY = {"Clock", "Wallpaper", "Sound", "Inputs", "Screensaver"};
    private static final int[] CATEGORY_ICON = {Glyph.CLOCK, Glyph.WALLPAPER, Glyph.SOUND, Glyph.INPUT, Glyph.MOON};
    private static final String[] CATEGORY_HINT = {
            "How the time is shown on the home screen.",
            "Moves slowly behind the home screen. Pick one to see it here at once.",
            "Sound of video wallpapers you add yourself.",
            "Names, icons and colours of the input cards at the bottom of the home screen.",
            "AXS Clock: what it shows, where the prices come from, how often they refresh.",
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
        saverView = null;
        inputView = null;
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
                if (has && (category != index || browsing || saverView != null || inputView != null)) {
                    category = index;
                    browsing = false;
                    saverView = null;
                    inputView = null;
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
            else if (category == SOUND) renderSound();
            else if (category == INPUTS) renderInputs();
            else renderScreensaver();
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
            if (category == SCREENSAVER && saverView != null && !browsing) {
                saverView = null;
                render(0);
            } else if (category == INPUTS && inputView != null && !browsing) {
                String back = inputView;
                inputView = null;
                render(0);
                for (int i = 0; i < home.inputList().size(); i++) {
                    if (home.inputList().get(i).id.equals(back)) focusPane(i);
                }
            } else if (browsing) {
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

    // ---- inputs (names, icons, colours of the cards on the home screen) ----

    private static final String[] ICONS = {"gamepad", "hdmi", "av", "antenna"};
    private static final String[] ICON_NAMES = {"Game console", "HDMI", "AV", "Antenna"};
    private static final String[] COLORS = {"#0070D1", "#107C10", "#E60012", "#3BB3FF", "#FF9F0A", "#AF52DE", "#5A6478"};
    private static final String[] COLOR_NAMES = {"Blue", "Green", "Red", "Sky", "Orange", "Purple", "Grey"};

    /** null: the list; otherwise the id of the input being edited. */
    private String inputView;

    private void renderInputs() {
        if (inputView != null) {
            renderInputEdit();
            return;
        }
        List<HomeActivity.Input> list = home.inputList();
        if (list.isEmpty()) {
            pane.addView(home.label("No inputs from home.conf exist on this TV.", 16, DIM, false));
            return;
        }
        for (HomeActivity.Input in : list) {
            String from = in.nameFrom.equals("yours") ? "your name" : in.nameFrom.equals("tv") ? "TV's name" : "default name";
            View c = card(inputRow(in, in.port + "   ·   " + from + (in.hidden ? "   ·   hidden" : "")), -1, 72, false, () -> {
                inputView = in.id;
                render(0);
            });
            if (in.hidden) c.setAlpha(0.55f);
            pane.addView(c, rowLp());
        }
        TextView note = home.label("Names set on the TV (Settings → Inputs) are used unless you set one here.", 13, DIM, false);
        note.setPadding(dp(8), dp(6), 0, 0);
        pane.addView(note);
    }

    private View inputRow(HomeActivity.Input in, String sub) {
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(18), 0, 0, 0);
        inner.addView(new IconView(home, IconView.parse(in.icon, in.port), home.color(in.color, 0xFF5A6478)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout texts = twoLines(in.name, sub);
        texts.setPadding(dp(16), 0, dp(20), 0);
        inner.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return inner;
    }

    private HomeActivity.Input editedInput() {
        for (HomeActivity.Input in : home.inputList()) if (in.id.equals(inputView)) return in;
        return null;
    }

    private void putInput(String key, String value, int focus) {
        SharedPreferences.Editor e = prefs.edit();
        if (value == null) e.remove(HomeActivity.PREF_INPUT + inputView + "." + key);
        else e.putString(HomeActivity.PREF_INPUT + inputView + "." + key, value);
        e.apply();
        home.applyInputs();
        render(focus);
    }

    private void renderInputEdit() {
        HomeActivity.Input in = editedInput();
        if (in == null) {
            inputView = null;
            renderInputs();
            return;
        }
        paneTitle.setText(in.name);
        paneHint.setText(in.port + " · BACK to go back");
        pane.addView(card(inputRow(in, in.port), -1, 72, false, () -> { }), rowLp());

        EditText field = new EditText(home);
        field.setSingleLine(true);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(DIM);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        field.setPadding(dp(18), 0, dp(18), 0);
        field.setBackground(home.rounded(0x1FFFFFFF, Color.WHITE, RADIUS, 2));
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setText(in.nameFrom.equals("yours") ? in.name : "");
        field.setHint("New name (now: " + in.name + ")");
        Runnable save = () -> {
            String v = field.getText().toString().trim();
            if (v.length() > 40) v = v.substring(0, 40);
            putInput("name", v.isEmpty() ? null : v, 2);
        };
        field.setOnEditorActionListener((v, action, ev) -> {
            save.run();
            return true;
        });
        items.add(field);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        flp.topMargin = dp(8);
        pane.addView(field, flp);
        pane.addView(card(twoLines("Save name", "Empty uses the TV's name"), -1, 64, false, save), rowLpTop());

        int icon = Math.max(0, Arrays.asList(ICONS).indexOf(in.icon));
        pane.addView(card(twoLines("Icon: " + ICON_NAMES[icon], "OK to change"), -1, 64, false, () ->
                putInput("icon", ICONS[(icon + 1) % ICONS.length], 3)), rowLp());
        int color = Math.max(0, Arrays.asList(COLORS).indexOf(in.color.toUpperCase(Locale.ROOT)));
        pane.addView(card(twoLines("Colour: " + COLOR_NAMES[color], "OK to change"), -1, 64, false, () ->
                putInput("color", COLORS[(color + 1) % COLORS.length], 4)), rowLp());
        pane.addView(toggleRow("Show on the home screen", in.hidden ? "Hidden" : "Shown", !in.hidden, () -> {
            prefs.edit().putBoolean(HomeActivity.PREF_INPUT + inputView + ".hidden", !in.hidden).apply();
            home.applyInputs();
            render(5);
        }), rowLp());
        if (in.nameFrom.equals("yours") || prefs.contains(HomeActivity.PREF_INPUT + inputView + ".icon")
                || prefs.contains(HomeActivity.PREF_INPUT + inputView + ".color")) {
            String tv = !in.tvName.isEmpty() ? in.tvName : !in.confName.isEmpty() ? in.confName : in.port;
            pane.addView(card(twoLines("Reset", "Back to \"" + tv + "\" with the default icon and colour"), -1, 64, false, () -> {
                prefs.edit().remove(HomeActivity.PREF_INPUT + inputView + ".name")
                        .remove(HomeActivity.PREF_INPUT + inputView + ".icon")
                        .remove(HomeActivity.PREF_INPUT + inputView + ".color").apply();
                home.applyInputs();
                render(2);
            }), rowLp());
        }
    }

    // ---- screensaver (AXS Clock's settings, through its provider) ----

    private static final Uri CLOCK_SETTINGS = Uri.parse("content://io.github.yohannaftali.axsclock.settings");
    private static final String CLOCK_PKG = "io.github.yohannaftali.axsclock";
    private static final int[] REFRESH = {1, 2, 5, 15};
    private static final String[] BACKGROUNDS = {"waves", "orbit", "pop", "bloom", "black"};
    private static final String[] KEYED = {"twelvedata", "finnhub", "coingecko"};

    /** null: main page; "add": catalogue; "key:<source>": key entry; "city": weather city entry. */
    private String saverView;
    private Bundle saver;
    private final List<String[]> catalogue = new ArrayList<>();
    private final Map<String, String[]> sources = new HashMap<>();

    private Bundle clock(String method, Bundle extras) {
        try {
            return home.getContentResolver().call(CLOCK_SETTINGS, method, null, extras);
        } catch (RuntimeException e) {
            // AXS Clock missing, or not signed with the same key.
            return null;
        }
    }

    private boolean loadSaver() {
        saver = clock("get", null);
        if (saver == null) return false;
        if (catalogue.isEmpty()) {
            Bundle c = clock("catalogue", null);
            if (c == null) return false;
            for (String line : c.getString("items", "").split("\n")) {
                String[] f = line.split("\\|", -1);
                if (f.length == 3) catalogue.add(f);
            }
            for (String line : c.getString("sources", "").split("\n")) {
                String[] f = line.split("\\|", -1);
                if (f.length == 3) sources.put(f[0], f);
            }
        }
        return true;
    }

    private void putSaver(Bundle extras, int focus) {
        Bundle b = clock("put", extras);
        if (b != null) saver = b;
        render(focus);
    }

    private int focusedIndex() {
        return Math.max(0, items.indexOf(dialog.getCurrentFocus()));
    }

    private List<String[]> tickers() {
        List<String[]> out = new ArrayList<>();
        for (String line : saver.getString("tickers", "").split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length >= 3) out.add(f);
        }
        return out;
    }

    private void putTickers(List<String[]> list, int focus) {
        StringBuilder b = new StringBuilder();
        for (String[] t : list) {
            if (b.length() > 0) b.append('\n');
            b.append(String.join("|", t));
        }
        Bundle e = new Bundle();
        e.putString("tickers", b.toString());
        putSaver(e, focus);
    }

    private String[] item(String label) {
        for (String[] i : catalogue) if (i[0].equals(label)) return i;
        return null;
    }

    private String sourceName(String id) {
        String[] s = sources.get(id);
        return s == null ? id : s[1];
    }

    private boolean available(String source) {
        return saver.getBoolean("available." + source, false);
    }

    /** "source:symbol[:invert]" options of a catalogue item that can be used now. */
    private List<String> usable(String[] item) {
        List<String> out = new ArrayList<>();
        for (String o : item[2].split(",")) if (available(o.split(":", 2)[0])) out.add(o);
        return out;
    }

    private static String[] ticker(String label, String option) {
        String[] f = option.split(":", -1);
        return f.length > 2 ? new String[]{label, f[0], f[1], f[2]} : new String[]{label, f[0], f[1]};
    }

    private static String option(String[] t) {
        return t[1] + ":" + t[2] + (t.length > 3 && t[3].equals("invert") ? ":invert" : "");
    }

    private void renderScreensaver() {
        if (!loadSaver()) {
            TextView t = home.label("AXS Clock is not installed or is an older build. Apply "
                    + "changes/33-market-panel to use these settings.", 16, DIM, false);
            pane.addView(t);
            return;
        }
        if ("add".equals(saverView)) {
            renderAddTicker();
            return;
        }
        if (saverView != null) {
            renderEntry();
            return;
        }
        List<String[]> list = tickers();
        pane.addView(section("Tickers  ·  " + list.size() + " of 8  ·  OK on a ticker changes its source"));
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            String[] t = list.get(i);
            String[] item = item(t[0]);
            List<String> options = item == null ? new ArrayList<>() : usable(item);
            LinearLayout row = row();
            String sub = sourceName(t[1]) + (options.size() > 1 ? "   ·   " + (options.size() - 1) + " more source"
                    + (options.size() > 2 ? "s" : "") : "");
            View main = card(twoLines(t[0], sub), -1, 64, false, () -> {
                int at = options.indexOf(option(t));
                if (options.size() < 2) return;
                list.set(index, ticker(t[0], options.get((at + 1) % options.size())));
                putTickers(list, focusedIndex());
            });
            row.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(space(8));
            row.addView(iconCard("▲", index > 0, () -> {
                if (index == 0) return;
                list.add(index - 1, list.remove(index));
                putTickers(list, (index - 1) * 3 + 1);
            }));
            row.addView(space(8));
            row.addView(iconCard("✕", true, () -> {
                list.remove(index);
                putTickers(list, Math.min(index, list.size()) * 3);
            }));
            pane.addView(row, rowLp());
        }
        if (list.size() < 8) {
            pane.addView(card(twoLines("+  Add a ticker", "Currencies, metals, crypto, indices, oil"), -1, 64, false, () -> {
                saverView = "add";
                render(0);
            }), rowLp());
        }

        pane.addView(section("Data sources"));
        boolean yahoo = saver.getBoolean("yahoo", false);
        pane.addView(toggleRow("Yahoo Finance", sources.containsKey("yahoo") ? sources.get("yahoo")[2] : "", yahoo, () -> {
            Bundle e = new Bundle();
            e.putBoolean("yahoo", !yahoo);
            putSaver(e, focusedIndex());
        }), rowLp());
        for (String s : KEYED) {
            String masked = saver.getString("key." + s, "");
            String name = s.equals("coingecko") ? "CoinGecko demo key (optional)" : sourceName(s);
            String note = (masked.isEmpty() ? "No key" : "Key " + masked) + "   ·   "
                    + (sources.containsKey(s) ? sources.get(s)[2] : "");
            pane.addView(card(twoLines(name, note), -1, 64, false, () -> {
                saverView = "key:" + s;
                render(0);
            }), rowLp());
        }
        TextView always = home.label("Always on, no account: CoinGecko, goldprice.dev, ECB.", 13, DIM, false);
        always.setPadding(dp(8), dp(2), 0, 0);
        pane.addView(always);

        pane.addView(section("Display"));
        int refresh = saver.getInt("refresh", 2);
        pane.addView(card(twoLines("Refresh every " + refresh + " min",
                "OK to change · each source is held to what its free plan allows"), -1, 64, false, () -> {
            int next = REFRESH[0];
            for (int i = 0; i < REFRESH.length; i++) if (REFRESH[i] == refresh) next = REFRESH[(i + 1) % REFRESH.length];
            Bundle e = new Bundle();
            e.putInt("refresh", next);
            putSaver(e, focusedIndex());
        }), rowLp());
        String bg = saver.getString("background", "waves");
        pane.addView(card(twoLines("Background: " + (bg.equals("black") ? "Black" : StyleView.title(bg)),
                "OK to change · dimmed behind the clock"), -1, 64, false, () -> {
            int at = Arrays.asList(BACKGROUNDS).indexOf(bg);
            Bundle e = new Bundle();
            e.putString("background", BACKGROUNDS[(at + 1) % BACKGROUNDS.length]);
            putSaver(e, focusedIndex());
        }), rowLp());
        boolean weather = saver.getBoolean("weather", true);
        pane.addView(toggleRow("Weather", "From the TV's location (Open-Meteo)", weather, () -> {
            Bundle e = new Bundle();
            e.putBoolean("weather", !weather);
            putSaver(e, focusedIndex());
        }), rowLp());
        String city = saver.getString("weather_city", "");
        pane.addView(card(twoLines("City when the TV has no location", city.isEmpty() ? "Not set" : city),
                -1, 64, false, () -> {
                    saverView = "city";
                    render(0);
                }), rowLp());
        pane.addView(card(twoLines("▶  Preview the screensaver", "Any key returns here"), -1, 64, false, () -> {
            try {
                home.startActivity(new Intent().setClassName(CLOCK_PKG, CLOCK_PKG + ".PreviewActivity"));
            } catch (RuntimeException e) {
                Toast.makeText(home, "Cannot open the preview", Toast.LENGTH_LONG).show();
            }
        }), rowLp());
    }

    /** The catalogue, grouped; items without a usable source say what they need. */
    private void renderAddTicker() {
        paneTitle.setText("Add a ticker");
        paneHint.setText("OK adds it to the screensaver · BACK to go back");
        List<String[]> list = tickers();
        String group = "";
        for (String[] item : catalogue) {
            boolean shown = false;
            for (String[] t : list) if (t[0].equals(item[0])) shown = true;
            if (shown) continue;
            if (!item[1].equals(group)) {
                group = item[1];
                pane.addView(section(group));
            }
            List<String> options = usable(item);
            List<String> names = new ArrayList<>();
            for (String o : item[2].split(",")) names.add(sourceName(o.split(":", 2)[0]));
            String sub = options.isEmpty() ? "Needs " + String.join(" or ", names) + " (Data sources)"
                    : "From " + sourceName(options.get(0).split(":", 2)[0]);
            pane.addView(card(twoLines(item[0], sub), -1, 60, false, () -> {
                if (options.isEmpty()) {
                    Toast.makeText(home, "Turn on " + String.join(" or ", names) + " first", Toast.LENGTH_LONG).show();
                    return;
                }
                list.add(ticker(item[0], options.get(0)));
                saverView = null;
                putTickers(list, (list.size() - 1) * 3);
            }), rowLp());
        }
    }

    /** Text entry for an API key or the weather city; the value goes straight to AXS Clock. */
    private void renderEntry() {
        boolean city = saverView.equals("city");
        String source = city ? null : saverView.substring(4);
        paneTitle.setText(city ? "Weather city" : (source.equals("coingecko") ? "CoinGecko demo key" : sourceName(source) + " key"));
        paneHint.setText(city ? "Used only when the TV cannot tell its location · BACK to cancel"
                : "Stored only in AXS Clock on this TV · the phone remote keyboard makes typing easy");
        EditText field = new EditText(home);
        field.setSingleLine(true);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(DIM);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        field.setPadding(dp(18), 0, dp(18), 0);
        field.setBackground(home.rounded(0x1FFFFFFF, Color.WHITE, RADIUS, 2));
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (city) {
            field.setText(saver.getString("weather_city", ""));
            field.setHint("e.g. Bandung");
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        } else {
            String masked = saver.getString("key." + source, "");
            field.setHint(masked.isEmpty() ? "Paste or type the key" : "Current key " + masked + " · type a new one");
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        Runnable save = () -> {
            String v = field.getText().toString().trim();
            if (!city && v.isEmpty()) return;
            Bundle e = new Bundle();
            e.putString(city ? "weather_city" : "key." + source, v);
            Bundle b = clock("put", e);
            if (b != null) saver = b;
            if (!city && saver.getString("key." + source, "").isEmpty()) {
                Toast.makeText(home, "That does not look like a key (8–100 letters, digits, - or _)", Toast.LENGTH_LONG).show();
                return;
            }
            saverView = null;
            render(0);
        };
        field.setOnEditorActionListener((v, action, ev) -> {
            save.run();
            return true;
        });
        items.add(field);
        pane.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        pane.addView(card(twoLines("Save", ""), -1, 56, false, save), rowLpTop());
        boolean has = city ? !saver.getString("weather_city", "").isEmpty() : !saver.getString("key." + source, "").isEmpty();
        if (has) {
            pane.addView(card(twoLines(city ? "Clear the city" : "Remove the key",
                    city ? "Weather then needs the TV's location" : "Tickers from this source switch to another one"),
                    -1, 64, false, () -> {
                        Bundle e = new Bundle();
                        e.putString(city ? "weather_city" : "key." + source, "");
                        Bundle b = clock("put", e);
                        if (b != null) saver = b;
                        saverView = null;
                        render(0);
                    }), rowLp());
        }
    }

    private TextView section(String text) {
        TextView t = home.label(text, 13, DIM, true);
        t.setLetterSpacing(0.05f);
        t.setPadding(dp(8), dp(16), 0, dp(8));
        return t;
    }

    private LinearLayout twoLines(String title, String sub) {
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(20), 0, dp(20), 0);
        TextView a = home.label(title, 17, Color.WHITE, false);
        a.setSingleLine(true);
        inner.addView(a);
        if (!sub.isEmpty()) {
            TextView b = home.label(sub, 13, DIM, false);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.END);
            inner.addView(b, top(3));
        }
        return inner;
    }

    private View toggleRow(String title, String sub, boolean on, Runnable flip) {
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = twoLines(title, sub);
        inner.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(30));
        lp.rightMargin = dp(20);
        inner.addView(toggle(on), lp);
        return card(inner, -1, 64, false, flip);
    }

    private View iconCard(String glyph, boolean enabled, Runnable onClick) {
        TextView t = home.label(glyph, 20, enabled ? Color.WHITE : 0x40FFFFFF, false);
        t.setGravity(Gravity.CENTER);
        return card(t, 64, 64, false, onClick);
    }

    private LinearLayout.LayoutParams rowLpTop() {
        LinearLayout.LayoutParams lp = rowLp();
        lp.topMargin = dp(10);
        return lp;
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
