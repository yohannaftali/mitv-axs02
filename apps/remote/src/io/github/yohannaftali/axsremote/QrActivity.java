package io.github.yohannaftali.axsremote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
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

import java.net.Inet4Address;
import java.net.InetAddress;

/** ☰ → Phone remote: the QR code to scan, the certificate fingerprint, and status. */
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

        String url = null;
        String error = null;
        RemoteServer server = RemoteServer.get(this);
        try {
            InetAddress ip = lanAddress();
            if (ip == null) error = "The TV is not on a network.";
            else url = server.open(ip, this);
        } catch (Exception e) {
            error = "Could not start the remote: " + e.getClass().getSimpleName();
        }

        String[] steps = {
                "1   Scan the code with your phone's camera.",
                "2   Your phone warns that the connection is not private: the TV made its own certificate. "
                        + "Check the fingerprint below, then choose Show details → visit this website (iPhone) "
                        + "or Advanced → Proceed (Android).",
                "3   Use the remote. When a text box is focused on the TV, tap the text bar on the phone to type.",
        };
        for (String s : steps) left.addView(text(s, 16, Color.WHITE, false), top(18));
        TextView fp = text(url == null ? (error == null ? "" : error)
                : "Certificate fingerprint (SHA-256)   " + server.fingerprint() + "…", 14, DIM, false);
        left.addView(fp, top(26));
        phones = text("", 14, DIM, false);
        left.addView(phones, top(6));
        left.addView(text("The code changes each time this screen opens; older codes stop working. "
                + "The remote turns itself off after 10 minutes without use.", 13, DIM, false), top(18));

        if (url != null) {
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(render(Qr.encode(url)));
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

    /** The TV's IPv4 address on the active network (read at runtime, shown only in the QR code). */
    private InetAddress lanAddress() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) return null;
        for (LinkAddress a : lp.getLinkAddresses()) {
            InetAddress ip = a.getAddress();
            if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) return ip;
        }
        return null;
    }

    private static Bitmap render(boolean[][] m) {
        int quiet = 4;
        int n = m.length + quiet * 2;
        int scale = Math.max(1, 640 / n);
        Bitmap b = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m.length; x++) {
                if (!m[y][x]) continue;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        b.setPixel((x + quiet) * scale + dx, (y + quiet) * scale + dy, Color.BLACK);
                    }
                }
            }
        }
        return b;
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
