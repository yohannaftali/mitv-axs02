package io.github.yohannaftali.axshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Original input icons drawn in code: gamepad, HDMI plug, AV (RCA) jacks, aerial antenna. */
final class IconView extends View {
    static final int GAMEPAD = 0;
    static final int HDMI = 1;
    static final int AV = 2;
    static final int ANTENNA = 3;

    private final int type;
    private final int color;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    IconView(Context c, int type, int color) {
        super(c);
        this.type = type;
        this.color = color;
    }

    static int parse(String s, String port) {
        if ("gamepad".equals(s)) return GAMEPAD;
        if ("av".equals(s)) return AV;
        if ("hdmi".equals(s)) return HDMI;
        if ("antenna".equals(s)) return ANTENNA;
        return port.toUpperCase().startsWith("HDMI") ? HDMI : AV;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w, h);
        p.setStyle(Paint.Style.FILL);
        switch (type) {
            case GAMEPAD: {
                p.setColor(color);
                r.set(w * 0.08f, h * 0.30f, w * 0.92f, h * 0.70f);
                c.drawRoundRect(r, h * 0.2f, h * 0.2f, p);
                c.drawCircle(w * 0.26f, h * 0.66f, h * 0.17f, p);
                c.drawCircle(w * 0.74f, h * 0.66f, h * 0.17f, p);
                p.setColor(0xFFFFFFFF);
                float cx = w * 0.30f, cy = h * 0.49f, a = s * 0.13f, b = s * 0.045f;
                c.drawRect(cx - a, cy - b, cx + a, cy + b, p);
                c.drawRect(cx - b, cy - a, cx + b, cy + a, p);
                c.drawCircle(w * 0.66f, h * 0.44f, s * 0.05f, p);
                c.drawCircle(w * 0.76f, h * 0.54f, s * 0.05f, p);
                break;
            }
            case HDMI: {
                path.reset();
                path.moveTo(w * 0.12f, h * 0.34f);
                path.lineTo(w * 0.88f, h * 0.34f);
                path.lineTo(w * 0.88f, h * 0.52f);
                path.lineTo(w * 0.74f, h * 0.68f);
                path.lineTo(w * 0.26f, h * 0.68f);
                path.lineTo(w * 0.12f, h * 0.52f);
                path.close();
                p.setColor(color);
                c.drawPath(path, p);
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 5; i++) {
                    float x = w * (0.27f + i * 0.115f);
                    c.drawRect(x, h * 0.44f, x + w * 0.05f, h * 0.52f, p);
                }
                break;
            }
            case ANTENNA: {
                // A roof antenna: mast, three cross-bars, and a small TV-wave arc.
                p.setColor(color);
                float mx = w * 0.5f;
                c.drawRect(mx - s * 0.03f, h * 0.22f, mx + s * 0.03f, h * 0.86f, p);
                for (int i = 0; i < 3; i++) {
                    float y = h * (0.28f + i * 0.13f);
                    float half = s * (0.34f - i * 0.07f);
                    c.drawRoundRect(mx - half, y, mx + half, y + s * 0.05f, s * 0.03f, s * 0.03f, p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.05f);
                p.setStrokeCap(Paint.Cap.ROUND);
                r.set(mx - s * 0.16f, h * 0.06f, mx + s * 0.16f, h * 0.30f);
                c.drawArc(r, 210, 120, false, p);
                p.setStyle(Paint.Style.FILL);
                break;
            }
            default: {
                int[] jacks = {0xFFF2C230, 0xFFEDEDED, 0xFFD8342C};
                for (int i = 0; i < 3; i++) {
                    float x = w * (0.22f + i * 0.28f);
                    p.setColor(jacks[i]);
                    c.drawCircle(x, h * 0.5f, s * 0.13f, p);
                    p.setColor(0xFF1A1D24);
                    c.drawCircle(x, h * 0.5f, s * 0.05f, p);
                }
                break;
            }
        }
    }
}
