package io.github.yohannaftali.axshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.View;

/**
 * Layered flowing waves. Each layer is rendered once, off the UI thread, into a
 * half-resolution seamless bitmap; animating only slides those bitmaps, which
 * the GPU does almost for free. Path drawing every frame cost over a full CPU
 * core on this TV. Runs at most 20 fps (the drift is slow) and only while started.
 */
final class WaveView extends View implements Choreographer.FrameCallback {
    private static final int LAYERS = 4;
    private static final long FRAME_NS = 50_000_000L;
    private static final float SCALE = 0.5f;

    private final int top;
    private final int accent;
    private final Paint bg = new Paint();
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private volatile Bitmap[] layers;
    private boolean running;
    private long lastNs;
    private float seconds;

    WaveView(Context c, int top, int accent) {
        super(c);
        this.top = top;
        this.accent = accent;
    }

    void start() {
        if (running) return;
        running = true;
        lastNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long now) {
        if (!running) return;
        if (lastNs == 0) {
            lastNs = now;
            invalidate();
        } else if (now - lastNs >= FRAME_NS) {
            seconds += (now - lastNs) / 1e9f;
            lastNs = now;
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h, top, 0xFF02040A, Shader.TileMode.CLAMP));
        if (w == 0 || h == 0) return;
        layers = null;
        new Thread(() -> {
            Bitmap[] out = new Bitmap[LAYERS];
            for (int i = 0; i < LAYERS; i++) out[i] = renderLayer(i, w, h);
            layers = out;
            postInvalidate();
        }, "waves").start();
    }

    /** One seamless layer: an integer number of wave periods across the bitmap width. */
    private Bitmap renderLayer(int i, int w, int h) {
        int bw = Math.max(1, Math.round(w * SCALE));
        int bh = Math.max(1, Math.round(h * 0.55f * SCALE));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float amp = bh * (0.10f + 0.04f * i);
        float mid = amp * 1.4f;
        int periods = 1 + (i % 2);
        double k = Math.PI * 2 * periods / bw;
        double k2 = Math.PI * 2 * (periods + 2) / bw;
        Path area = new Path();
        Path edge = new Path();
        area.moveTo(0, bh);
        for (int x = 0; x <= bw; x += 4) {
            float y = (float) (mid + amp * Math.sin(x * k + i * 1.3) + amp * 0.3 * Math.sin(x * k2));
            area.lineTo(x, y);
            if (x == 0) edge.moveTo(x, y); else edge.lineTo(x, y);
        }
        area.lineTo(bw, bh);
        area.close();
        int alpha = 0x46 - i * 0x09;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, mid - amp, 0, bh,
                (alpha << 24) | (accent & 0x00FFFFFF), accent & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(area, fill);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.2f);
        line.setColor(0x2EFFFFFF);
        c.drawPath(edge, line);
        return b;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bg);
        Bitmap[] ls = layers;
        if (ls == null) return;
        for (int i = 0; i < ls.length; i++) {
            Bitmap b = ls[i];
            src.set(0, 0, b.getWidth(), b.getHeight());
            float layerH = b.getHeight() / SCALE;
            float speed = 0.012f + 0.008f * i * (i % 2 == 0 ? 1 : -1);
            float x = (seconds * speed * w) % w;
            if (x < 0) x += w;
            float bob = (float) Math.sin(seconds * (0.18 + 0.05 * i) + i) * h * 0.012f;
            float y = h * (0.40f + 0.07f * i) + bob;
            dst.set(x - w, y, x, y + layerH);
            canvas.drawBitmap(b, src, dst, blit);
            dst.set(x, y, x + w, y + layerH);
            canvas.drawBitmap(b, src, dst, blit);
        }
    }
}
