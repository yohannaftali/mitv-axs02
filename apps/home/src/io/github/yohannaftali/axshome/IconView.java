package io.github.yohannaftali.axshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Original input icons drawn in code: gamepad, HDMI plug, AV (RCA) jacks. */
final class IconView extends View {
    static final int GAMEPAD = 0;
    static final int HDMI = 1;
    static final int AV = 2;

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
