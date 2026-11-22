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

    private static final String[][] ROWS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l", "@"},
            {"z", "x", "c", "v", "b", "n", "m", ".", "-", "/"},
            {"⇧", "space", "⌫", "done"},
    };

    static boolean active() {
        return current != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        current = this;
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
        keys.clear();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xF20B0F18);
        root.setPadding(dp(24), dp(12), dp(24), dp(16));
        TextView hint = text("Or type on your phone: ☰ → Phone remote", 13, 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (String[] row : ROWS) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER);
            for (String k : row) {
                TextView t = text(k, 18, Color.WHITE);
                t.setGravity(Gravity.CENTER);
                t.setTag(k);
                int w = k.equals("space") ? 300 : k.length() > 1 ? 110 : 54;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(40));
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                r.addView(t, lp);
                keys.add(t);
            }
            root.addView(r);
        }
        selected = 10;
        paint();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        selected = Math.min(selected, keys.size() - 1);
        paint();
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
            t.setText(shift && k.length() == 1 ? k.toUpperCase() : k);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            bg.setColor(i == selected ? 0x40FFFFFF : 0x1AFFFFFF);
            if (i == selected) bg.setStroke(dp(2), Color.WHITE);
            t.setBackground(bg);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isInputViewShown() || keys.isEmpty()) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                selected = Math.max(0, selected - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                selected = Math.min(keys.size() - 1, selected + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                selected = move(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                selected = move(1);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                press((String) keys.get(selected).getTag());
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        paint();
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isInputViewShown() && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Moves to the key in the row above or below, keeping roughly the same column. */
    private int move(int dir) {
        int row = 0;
        int col = selected;
        for (int r = 0; r < ROWS.length; r++) {
            if (col < ROWS[r].length) {
                row = r;
                break;
            }
            col -= ROWS[r].length;
        }
        int target = Math.max(0, Math.min(ROWS.length - 1, row + dir));
        if (target == row) return selected;
        float rel = (col + 0.5f) / ROWS[row].length;
        int newCol = Math.min(ROWS[target].length - 1, (int) (rel * ROWS[target].length));
        int index = 0;
        for (int r = 0; r < target; r++) index += ROWS[r].length;
        return index + newCol;
    }

    private void press(String k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        switch (k) {
            case "⇧":
                shift = !shift;
                break;
            case "space":
                ic.commitText(" ", 1);
                break;
            case "⌫":
                sendKey(KeyEvent.KEYCODE_DEL);
                break;
            case "done":
                enter();
                requestHideSelf(0);
                break;
            default:
                ic.commitText(shift ? k.toUpperCase() : k, 1);
                if (shift) shift = false;
                break;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
