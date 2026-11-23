package io.github.yohannaftali.axsremote;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * AXS Remote as the TV's keyboard. Android 11 does not let an app inject keys
 * into other apps, but the active input method can send key events and text
 * to whatever is focused (when no text field is focused the framework gives it
 * a fallback connection to the focused window). The phone's commands arrive
 * here through RemoteServer. A small on-screen keyboard covers typing with the
 * physical remote, since Gboard is not active while this is.
 */
public final class RemoteIme extends InputMethodService {
    private static RemoteIme current;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private EditorInfo editor;
    private boolean shift;
    private final List<TextView> keys = new ArrayList<>();
    private int selected;

    private static final String[] NUMBERS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "@"},
            {"z", "x", "c", "v", "b", "n", "m", ".", "-", "/"},
    };
    private static final String[][] SYMBOLS = {
            {"!", "#", "$", "%", "&", "*", "(", ")", "_", "="},
            {"+", "?", "'", "\"", ":", ";", ",", "~", "[", "]"},
            {"{", "}", "<", ">", "|", "^", "`", ".", "-", "/"},
    };
    private static final String[] BOTTOM = {"⇧", "SYM", "space", "⌫", "HIDE", "PHONE", "done"};
    private static final String[] EMAIL = {"@gmail.com", "@yahoo.com", "@outlook.com", "@icloud.com", ".com", ".co.id"};
    private static final String[] URL = {"www.", ".com", ".co.id", "https://"};

    private LinearLayout board;
    private final List<String[]> rows = new ArrayList<>();
    private boolean symbols;
    private boolean showingQr;
    private boolean backToKeys;

    static boolean active() {
        return current != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        current = this;
        // The remote is served for as long as Android keeps this keyboard bound.
        RemoteServer.get(this).ensureRunning();
    }

    @Override
    public void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    // ---- focus tracking → phone text bar ----

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        editor = attribute;
        boolean editing = attribute != null && attribute.inputType != InputType.TYPE_NULL;
        if (!editing) {
            RemoteServer.get(this).editorChanged(false, null, null);
            return;
        }
        String hint = attribute.hintText == null ? "" : attribute.hintText.toString();
        // The text is readable only once the connection is set up.
        ui.postDelayed(() -> RemoteServer.get(this).editorChanged(true, hint, currentText()), 150);
    }

    @Override
    public void onFinishInput() {
        editor = null;
        RemoteServer.get(this).editorChanged(false, null, null);
        super.onFinishInput();
    }

    private String currentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        return et == null || et.text == null ? "" : et.text.toString();
    }

    // ---- commands from the phone (main thread) ----

    static void command(Context c, String msg) {
        if (msg.startsWith("key:")) {
            key(c, msg.substring(4));
        } else if (msg.startsWith("text:")) {
            if (current != null) current.replaceText(msg.substring(5));
        } else if (msg.equals("enter")) {
            if (current != null) current.enter();
        } else if (msg.startsWith("move:") || msg.startsWith("scroll:")) {
            // Touchpad: "move:dx:dy" / "scroll:dx:dy" in phone pixels.
            String[] f = msg.split(":");
            if (f.length != 3) return;
            try {
                float dx = Math.max(-400f, Math.min(400f, Float.parseFloat(f[1])));
                float dy = Math.max(-400f, Math.min(400f, Float.parseFloat(f[2])));
                if (f[0].equals("move")) PointerService.move(dx, dy);
                else PointerService.scroll(dx, dy);
            } catch (NumberFormatException ignored) {
                // Not a number: ignore.
            }
        } else if (msg.equals("tap") || msg.equals("long")) {
            PointerService.tap(msg.equals("long"));
        }
    }

    private static void key(Context c, String name) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        switch (name) {
            case "VOLUP":
                if (am != null) am.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE,
                        AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                return;
            case "VOLDOWN":
                if (am != null) am.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                return;
            case "MUTE":
                if (am != null) am.adjustSuggestedStreamVolume(AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                return;
            case "PLAY":
                media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                return;
            case "REW":
                media(am, KeyEvent.KEYCODE_MEDIA_REWIND);
                return;
            case "FF":
                media(am, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
                return;
            case "HOME":
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    c.startActivity(home);
                } catch (RuntimeException ignored) {
                    // Background start not allowed: nothing to do.
                }
                return;
            default:
                break;
        }
        int code;
        switch (name) {
            case "UP": code = KeyEvent.KEYCODE_DPAD_UP; break;
            case "DOWN": code = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "LEFT": code = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "RIGHT": code = KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "OK": code = KeyEvent.KEYCODE_DPAD_CENTER; break;
            case "BACK": code = KeyEvent.KEYCODE_BACK; break;
            case "MENU": code = KeyEvent.KEYCODE_MENU; break;
            case "DEL": code = KeyEvent.KEYCODE_DEL; break;
            // Aerial TV (Live TV): channels, number pad, info, guide.
            case "CHUP": code = KeyEvent.KEYCODE_CHANNEL_UP; break;
            case "CHDOWN": code = KeyEvent.KEYCODE_CHANNEL_DOWN; break;
            case "INFO": code = KeyEvent.KEYCODE_INFO; break;
            case "GUIDE": code = KeyEvent.KEYCODE_GUIDE; break;
            default:
                if (name.length() == 1 && Character.isDigit(name.charAt(0))) {
                    code = KeyEvent.KEYCODE_0 + (name.charAt(0) - '0');
                    break;
                }
                return;
        }
        if (current != null) current.sendKey(code);
    }

    /** Media keys go to the active media session (YouTube, Netflix…) wherever focus is. */
    private static void media(AudioManager am, int code) {
        if (am == null) return;
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
    }

    private void sendKey(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (code == KeyEvent.KEYCODE_BACK && isInputViewShown()) {
            requestHideSelf(0);
            return;
        }
        // Look like a remote's D-pad (source DPAD, virtual device): leanback lists such as
        // TV Settings ignore soft-keyboard events with an unknown source.
        boolean dpad = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_BACK;
        int source = dpad ? android.view.InputDevice.SOURCE_DPAD : android.view.InputDevice.SOURCE_KEYBOARD;
        int flags = dpad ? 0 : KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        long now = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, source));
        ic.sendKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, source));
    }

    /** The phone sends the whole field each time; replace what is there. */
    private void replaceText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || editor == null) return;
        ic.beginBatchEdit();
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null && et.text != null) {
            int start = et.startOffset;
            ic.setSelection(start, start + et.text.length());
        } else {
            ic.performContextMenuAction(android.R.id.selectAll);
        }
        ic.commitText(text, 1);
        ic.endBatchEdit();
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int action = editor == null ? EditorInfo.IME_ACTION_NONE : editor.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendKey(KeyEvent.KEYCODE_ENTER);
        }
    }

    // ---- on-screen keyboard for the physical remote ----

    /** The TV remote registers as a hardware keyboard, which would hide the on-screen keys by default. */
    @Override
    public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();
        return true;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setBackgroundColor(0xF20B0F18);
        board.setPadding(dp(24), dp(10), dp(24), dp(16));
        build(true);
        return board;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        showingQr = false;
        if (!restarting) symbols = false;
        build(!restarting);
    }

    /** One-press endings for e-mail and web address fields; none for passwords or plain text. */
    private static String[] suggestions(EditorInfo info) {
        if (info == null || (info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return null;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) return EMAIL;
        if (variation == InputType.TYPE_TEXT_VARIATION_URI) return URL;
        return null;
    }

    /** Lays the keys out again (field type, symbols or the QR panel changed). */
    private void build(boolean resetFocus) {
        if (board == null) return;
        board.removeAllViews();
        keys.clear();
        rows.clear();
        String url = RemoteServer.get(this).url();
        if (showingQr) {
            buildQr(url);
            return;
        }
        TextView hint = text(url == null ? "Or type on your phone: ☰ → Phone remote"
                : "Or type on your phone: " + url + "   ·   📱 shows the QR code", 13, 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER);
        board.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String[] chips = suggestions(editor);
        if (chips != null) {
            String[] row = new String[chips.length];
            for (int i = 0; i < chips.length; i++) row[i] = "S:" + chips[i];
            rows.add(row);
        }
        rows.add(NUMBERS);
        for (String[] r : symbols ? SYMBOLS : LETTERS) rows.add(r);
        rows.add(BOTTOM);

        for (String[] row : rows) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER);
            for (String k : row) {
                TextView t = text("", k.startsWith("S:") ? 16 : 18, Color.WHITE);
                t.setGravity(Gravity.CENTER);
                t.setTag(k);
                LinearLayout.LayoutParams lp;
                if (k.startsWith("S:")) {
                    t.setPadding(dp(14), 0, dp(14), 0);
                    t.setMinWidth(dp(54));
                    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
                } else {
                    lp = new LinearLayout.LayoutParams(dp(width(k)), dp(40));
                }
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                r.addView(t, lp);
                keys.add(t);
            }
            board.addView(r);
        }
        if (resetFocus || selected >= keys.size()) {
            // Start on "q", below the suggestions and numbers.
            selected = (chips == null ? 0 : chips.length) + NUMBERS.length;
        }
        paint();
    }

    private static int width(String k) {
        switch (k) {
            case "space": return 220;
            case "SYM": case "HIDE": case "done": return 96;
            case "PHONE": return 64;
            default: return 54;
        }
    }

    private String label(String k) {
        switch (k) {
            case "SYM": return symbols ? "abc" : "#+=";
            case "HIDE": return "Hide ▾";
            case "PHONE": return "📱";
            default:
                if (k.startsWith("S:")) return k.substring(2);
                return shift && k.length() == 1 ? k.toUpperCase() : k;
        }
    }

    /** 📱: the phone-remote QR code in place of the keys; any key returns. */
    private void buildQr(String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        if (url != null) {
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(Qr.bitmap(url));
            qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
            qr.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(12));
            qr.setBackground(bg);
            row.addView(qr, new LinearLayout.LayoutParams(dp(200), dp(200)));
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text("Type on your phone", 24, Color.WHITE));
        TextView how = text(url == null ? "The TV is not on a network."
                : "Scan the code, or open " + url + " in the phone's browser.\n"
                + "A text bar appears on the phone while this field is focused.", 16, 0xCCFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        col.addView(how, lp);
        TextView back = text("Press OK or BACK for the keys", 14, 0x99FFFFFF);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(18);
        col.addView(back, lp2);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.leftMargin = dp(32);
        row.addView(col, cl);
        board.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(236)));
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private void paint() {
        for (int i = 0; i < keys.size(); i++) {
            TextView t = keys.get(i);
            String k = (String) t.getTag();
            t.setText(label(k));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(k.startsWith("S:") ? 17 : 8));
            int idle = k.startsWith("S:") ? 0x2E3D7BFF : 0x1AFFFFFF;
            bg.setColor(i == selected ? 0x40FFFFFF : idle);
            if (i == selected) bg.setStroke(dp(2), Color.WHITE);
            t.setBackground(bg);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isInputViewShown()) return super.onKeyDown(keyCode, event);
        if (showingQr) {
            if (isNavKey(keyCode) || keyCode == KeyEvent.KEYCODE_BACK) {
                backToKeys = keyCode == KeyEvent.KEYCODE_BACK;
                if (event.getRepeatCount() == 0) {
                    showingQr = false;
                    build(false);
                }
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (keys.isEmpty()) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                selected = Math.max(0, selected - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                selected = Math.min(keys.size() - 1, selected + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (rowOf(selected) == 0) {
                    // Off the top: get out of the way so the D-pad reaches the app again.
                    requestHideSelf(0);
                    return true;
                }
                selected = move(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                selected = move(1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                press((String) keys.get(selected).getTag());
                if (showingQr || board == null) return true;
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        paint();
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isInputViewShown() && isNavKey(keyCode)) return true;
        // BACK on the QR panel only returns to the keys (handled on key down).
        if (keyCode == KeyEvent.KEYCODE_BACK && backToKeys) {
            backToKeys = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private static boolean isNavKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
    }

    private int rowOf(int index) {
        for (int r = 0; r < rows.size(); r++) {
            if (index < rows.get(r).length) return r;
            index -= rows.get(r).length;
        }
        return rows.size() - 1;
    }

    /** Moves to the key in the row above or below, keeping roughly the same column. */
    private int move(int dir) {
        int row = rowOf(selected);
        int col = selected;
        for (int r = 0; r < row; r++) col -= rows.get(r).length;
        int target = Math.max(0, Math.min(rows.size() - 1, row + dir));
        if (target == row) return selected;
        float rel = (col + 0.5f) / rows.get(row).length;
        int newCol = Math.min(rows.get(target).length - 1, (int) (rel * rows.get(target).length));
        int index = 0;
        for (int r = 0; r < target; r++) index += rows.get(r).length;
        return index + newCol;
    }

    private void press(String k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        switch (k) {
            case "⇧":
                shift = !shift;
                break;
            case "SYM":
                symbols = !symbols;
                build(false);
                break;
            case "space":
                ic.commitText(" ", 1);
                break;
            case "⌫":
                sendKey(KeyEvent.KEYCODE_DEL);
                break;
            case "HIDE":
                requestHideSelf(0);
                break;
            case "PHONE":
                showingQr = true;
                build(false);
                break;
            case "done":
                enter();
                requestHideSelf(0);
                break;
            default:
                if (k.startsWith("S:")) {
                    suggest(ic, k.substring(2));
                    break;
                }
                ic.commitText(shift ? k.toUpperCase() : k, 1);
                if (shift) shift = false;
                break;
        }
    }

    /** "@domain" replaces a domain already typed after "@" in the current word; others are appended. */
    private static void suggest(InputConnection ic, String s) {
        if (s.startsWith("@")) {
            CharSequence before = ic.getTextBeforeCursor(128, 0);
            if (before != null) {
                String b = before.toString();
                int word = Math.max(b.lastIndexOf(' '), b.lastIndexOf('\n')) + 1;
                int at = b.lastIndexOf('@');
                if (at >= word) ic.deleteSurroundingText(b.length() - at, 0);
            }
        }
        ic.commitText(s, 1);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
