package io.github.yohannaftali.axshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Network status glyph: Ethernet port, Wi-Fi arcs with bars, or offline. */
final class NetIcon extends View {
    static final int OFFLINE = 0;
    static final int ETHERNET = 1;
    static final int WIFI = 2;

    private static final int ONLINE_COLOR = 0xFFFFFFFF;
    private static final int LIMITED_COLOR = 0xFFFFC233;
    private static final int OFF_COLOR = 0x80FFFFFF;
    private static final int DIM = 0x40FFFFFF;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int kind = OFFLINE;
    private boolean validated;
    private int bars;

    NetIcon(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void set(int kind, boolean validated, int bars) {
        if (this.kind == kind && this.validated == validated && this.bars == bars) return;
        this.kind = kind;
        this.validated = validated;
        this.bars = bars;
        String state = kind == OFFLINE ? "offline" : validated ? "online" : "no internet";
        String type = kind == ETHERNET ? "Ethernet" : kind == WIFI ? "Wi-Fi" : "Network";
        setContentDescription(type + ", " + state);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w, h);
        int color = kind == OFFLINE ? OFF_COLOR : validated ? ONLINE_COLOR : LIMITED_COLOR;
        if (kind == ETHERNET) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.08f);
            p.setColor(color);
            r.set(w * 0.2f, h * 0.22f, w * 0.8f, h * 0.72f);
            c.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(w * 0.4f, h * 0.72f, w * 0.6f, h * 0.84f, p);
            for (int i = 0; i < 4; i++) {
                float x = w * (0.32f + i * 0.12f);
                c.drawRect(x, h * 0.32f, x + w * 0.05f, h * 0.46f, p);
            }
            return;
        }
        // Wi-Fi arcs (also used, greyed and slashed, for offline).
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.09f);
        float cx = w / 2f;
        float cy = h * 0.80f;
        for (int i = 1; i <= 3; i++) {
            float rad = s * (0.18f + 0.18f * i);
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            boolean lit = kind == WIFI && bars >= i + 1;
            p.setColor(lit ? color : (kind == WIFI ? DIM : OFF_COLOR));
            c.drawArc(r, 225, 90, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(kind == WIFI && bars >= 1 ? color : (kind == WIFI ? DIM : OFF_COLOR));
        c.drawCircle(cx, cy - s * 0.04f, s * 0.08f, p);
        if (kind == OFFLINE) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.08f);
            p.setColor(OFF_COLOR);
            c.drawLine(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.86f, p);
        }
    }
}
