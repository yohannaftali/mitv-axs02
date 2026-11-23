package io.github.yohannaftali.axsremote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** ☰ → Phone remote: the QR code and address to open, and status. */
public final class QrActivity extends Activity implements RemoteServer.Listener {
    private static final int DIM = 0x99FFFFFF;

    private TextView status;
    private TextView phones;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070D);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setPadding(dp(72), dp(48), dp(72), dp(48));
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        main.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView over = text("AXS HOME", 13, DIM, true);
        over.setLetterSpacing(0.2f);
        left.addView(over);
        TextView title = text("Phone remote", 34, Color.WHITE, false);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        left.addView(title);
        status = text("", 15, 0xFFFFC233, false);
        left.addView(status, top(10));

        RemoteServer server = RemoteServer.get(this);
        server.ensureRunning();
        String url = server.url();
        String error = url == null ? "The TV is not on a network." : server.running() ? null : "Could not start the remote.";

        String[] steps = {
                "1   Scan the code with your phone's camera, or type the address below into its browser.",
                "2   Use the remote. When a text box is focused on the TV, tap the text bar on the phone to type.",
                "3   Bookmark the page or add it to the home screen; the address stays the same.",
        };
        for (String s : steps) left.addView(text(s, 16, Color.WHITE, false), top(18));
        TextView addr = text(error != null ? error : url, 24, error != null ? 0xFFFFC233 : Color.WHITE, true);
        left.addView(addr, top(26));
        phones = text("", 14, DIM, false);
        left.addView(phones, top(6));
        left.addView(text("Any phone on this Wi-Fi can use the remote while AXS Remote is the TV's keyboard. "
                + "Typed text travels over your home network unencrypted.", 13, DIM, false), top(18));

        if (url != null && error == null) {
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(Qr.bitmap(url));
            qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
            qr.setPadding(dp(20), dp(20), dp(20), dp(20));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(20));
            qr.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300), dp(300));
            lp.leftMargin = dp(56);
            main.addView(qr, lp);
        }
        setContentView(root);
        refreshStatus();
        onPhonesChanged(0);
        server.setListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        RemoteServer.get(this).setListener(null);
        super.onDestroy();
    }

    @Override
    public void onPhonesChanged(int count) {
        phones.setText(count == 0 ? "No phone connected" : count == 1 ? "1 phone connected" : count + " phones connected");
    }

    private void refreshStatus() {
        status.setText(isCurrentKeyboard() ? ""
                : "AXS Remote is not the TV's keyboard, so the phone cannot send keys. "
                        + "Run changes/40-phone-remote/apply.sh, or Settings → Keyboard → AXS Remote.");
    }

    private boolean isCurrentKeyboard() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null || id == null) return false;
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (info.getId().equals(id) && info.getPackageName().equals(getPackageName())) return true;
        }
        return false;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(m);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
