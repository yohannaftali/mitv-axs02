package io.github.yohannaftali.axshome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Simple line icons drawn in code for Home settings (no image assets). */
final class Glyph extends View {
    static final int CLOCK = 0;
    static final int WALLPAPER = 1;
    static final int SOUND = 2;
    static final int FOLDER = 3;
    static final int FILE = 4;
    static final int UP = 5;
    static final int CHECK = 6;
    static final int MOON = 7;

    private final int kind;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    Glyph(Context c, int kind, int color) {
        super(c);
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        tick.setColor(0xFF0B0F18);
        setColor(color);
    }

    void setColor(int color) {
        line.setColor(color);
        fill.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float x0 = (getWidth() - s) / 2f;
        float y0 = (getHeight() - s) / 2f;
        line.setStrokeWidth(Math.max(2f, s * 0.08f));
        path.reset();
        switch (kind) {
            case CLOCK:
                c.drawCircle(x0 + s / 2, y0 + s / 2, s * 0.40f, line);
                path.moveTo(x0 + s / 2, y0 + s * 0.24f);
                path.lineTo(x0 + s / 2, y0 + s / 2);
                path.lineTo(x0 + s * 0.68f, y0 + s * 0.62f);
                c.drawPath(path, line);
                break;
            case WALLPAPER:
                r.set(x0 + s * 0.10f, y0 + s * 0.18f, x0 + s * 0.90f, y0 + s * 0.82f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, line);
                path.moveTo(x0 + s * 0.18f, y0 + s * 0.72f);
                path.lineTo(x0 + s * 0.40f, y0 + s * 0.46f);
                path.lineTo(x0 + s * 0.56f, y0 + s * 0.62f);
                path.lineTo(x0 + s * 0.66f, y0 + s * 0.52f);
                path.lineTo(x0 + s * 0.82f, y0 + s * 0.72f);
                c.drawPath(path, line);
                c.drawCircle(x0 + s * 0.68f, y0 + s * 0.34f, s * 0.06f, fill);
                break;
            case SOUND:
                path.moveTo(x0 + s * 0.14f, y0 + s * 0.40f);
                path.lineTo(x0 + s * 0.30f, y0 + s * 0.40f);
                path.lineTo(x0 + s * 0.50f, y0 + s * 0.22f);
                path.lineTo(x0 + s * 0.50f, y0 + s * 0.78f);
                path.lineTo(x0 + s * 0.30f, y0 + s * 0.60f);
                path.lineTo(x0 + s * 0.14f, y0 + s * 0.60f);
                path.close();
                c.drawPath(path, line);
                r.set(x0 + s * 0.46f, y0 + s * 0.34f, x0 + s * 0.74f, y0 + s * 0.66f);
                c.drawArc(r, -50, 100, false, line);
                r.set(x0 + s * 0.42f, y0 + s * 0.20f, x0 + s * 0.88f, y0 + s * 0.80f);
                c.drawArc(r, -50, 100, false, line);
                break;
            case FOLDER:
                path.moveTo(x0 + s * 0.10f, y0 + s * 0.26f);
                path.lineTo(x0 + s * 0.40f, y0 + s * 0.26f);
                path.lineTo(x0 + s * 0.48f, y0 + s * 0.36f);
                path.lineTo(x0 + s * 0.90f, y0 + s * 0.36f);
                path.lineTo(x0 + s * 0.90f, y0 + s * 0.78f);
                path.lineTo(x0 + s * 0.10f, y0 + s * 0.78f);
                path.close();
                c.drawPath(path, line);
                break;
            case FILE:
                path.moveTo(x0 + s * 0.24f, y0 + s * 0.12f);
                path.lineTo(x0 + s * 0.58f, y0 + s * 0.12f);
                path.lineTo(x0 + s * 0.76f, y0 + s * 0.30f);
                path.lineTo(x0 + s * 0.76f, y0 + s * 0.88f);
                path.lineTo(x0 + s * 0.24f, y0 + s * 0.88f);
                path.close();
                c.drawPath(path, line);
                c.drawCircle(x0 + s * 0.42f, y0 + s * 0.46f, s * 0.06f, fill);
                path.reset();
                path.moveTo(x0 + s * 0.32f, y0 + s * 0.76f);
                path.lineTo(x0 + s * 0.46f, y0 + s * 0.60f);
                path.lineTo(x0 + s * 0.68f, y0 + s * 0.76f);
                c.drawPath(path, line);
                break;
            case UP:
                path.moveTo(x0 + s * 0.62f, y0 + s * 0.24f);
                path.lineTo(x0 + s * 0.36f, y0 + s * 0.50f);
                path.lineTo(x0 + s * 0.62f, y0 + s * 0.76f);
                c.drawPath(path, line);
                break;
            case MOON:
                // Crescent: the outer circle minus an offset one.
                path.addCircle(x0 + s * 0.48f, y0 + s * 0.52f, s * 0.36f, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(x0 + s * 0.66f, y0 + s * 0.38f, s * 0.30f, Path.Direction.CW);
                path.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(path, line);
                break;
            default: // CHECK on a filled disc
                c.drawCircle(x0 + s / 2, y0 + s / 2, s / 2, fill);
                tick.setStrokeWidth(Math.max(2f, s * 0.12f));
                path.moveTo(x0 + s * 0.28f, y0 + s * 0.52f);
                path.lineTo(x0 + s * 0.44f, y0 + s * 0.67f);
                path.lineTo(x0 + s * 0.72f, y0 + s * 0.36f);
                c.drawPath(path, tick);
                break;
        }
    }
}
