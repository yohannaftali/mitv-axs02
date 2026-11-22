package io.github.yohannaftali.axshome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.View;

/**
 * Built-in animated wallpapers, all original designs. Anything expensive (paths,
 * gradients) is rendered once off the UI thread into half-resolution bitmaps;
 * each frame only moves, rotates or fades them, which the GPU does almost for
 * free. Drawing paths every frame cost over a full CPU core on this TV.
 * Runs at most 20 fps and only while started.
 */
final class StyleView extends View implements Choreographer.FrameCallback, Backdrop {
    static final String WAVES = "waves";
    static final String ORBIT = "orbit";
    static final String POP = "pop";
    static final String BLOOM = "bloom";
    static final String[] ALL = {WAVES, ORBIT, POP, BLOOM};

    private static final long FRAME_NS = 50_000_000L;
    private static final float SCALE = 0.5f;

    private final String style;
    private final int top;
    private final int accent;
    private final Paint bg = new Paint();
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private volatile Bitmap[] layers;
    private Shader orbitGlow;
    private boolean running;
    private long lastNs;
    private float seconds;

    static boolean known(String s) {
        for (String a : ALL) if (a.equals(s)) return true;
        return false;
    }

    static String title(String s) {
        switch (s) {
            case ORBIT: return "Orbit";
            case POP: return "Pop";
            case BLOOM: return "Bloom";
            default: return "Waves";
        }
    }

    /** Colours: Waves may take its two colours from home.conf; the others have fixed palettes. */
    StyleView(Context c, String style, int wavesTop, int wavesAccent) {
        super(c);
        this.style = known(style) ? style : WAVES;
        switch (this.style) {
            case ORBIT: top = 0xFF06261A; accent = 0xFF3BD16F; break;
            case POP: top = 0xFFC8141F; accent = 0xFFFFFFFF; break;
            case BLOOM: top = 0xFF0B2A6B; accent = 0xFF6FB6FF; break;
            default: top = wavesTop; accent = wavesAccent; break;
        }
    }

    @Override public View view() { return this; }

    @Override
    public void start() {
        if (running) return;
        running = true;
        lastNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void release() {
        stop();
        Bitmap[] ls = layers;
        layers = null;
        if (ls != null) for (Bitmap b : ls) b.recycle();
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
        int bottom = style.equals(POP) ? 0xFF3A0508 : 0xFF02040A;
        bg.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        if (w == 0 || h == 0) return;
        layers = null;
        new Thread(() -> {
            Bitmap[] out;
            switch (style) {
                case ORBIT: out = orbitLayers(w, h); break;
                case BLOOM: out = bloomLayers(w, h); break;
                case POP: out = new Bitmap[0]; break;
                default: out = waveLayers(w, h); break;
            }
            layers = out;
            postInvalidate();
        }, "wallpaper").start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bg);
        Bitmap[] ls = layers;
        if (ls == null) return;
        switch (style) {
            case ORBIT: drawOrbit(canvas, ls, w, h); break;
            case POP: drawPop(canvas, w, h); break;
            case BLOOM: drawBloom(canvas, ls, w, h); break;
            default: drawWaves(canvas, ls, w, h); break;
        }
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    // ---- Waves: layered seamless waves sliding sideways ----

    private Bitmap[] waveLayers(int w, int h) {
        Bitmap[] out = new Bitmap[4];
        for (int i = 0; i < out.length; i++) {
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
            // Step 4 px, but always end exactly on the right edge so the tiled copies meet without a gap.
            for (int x = 0; ; x = Math.min(x + 4, bw)) {
                float y = (float) (mid + amp * Math.sin(x * k + i * 1.3) + amp * 0.3 * Math.sin(x * k2));
                area.lineTo(x, y);
                if (x == 0) edge.moveTo(x, y); else edge.lineTo(x, y);
                if (x == bw) break;
            }
            area.lineTo(bw, bh);
            area.close();
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setShader(new LinearGradient(0, mid - amp, 0, bh,
                    withAlpha(accent, 0x46 - i * 0x09), withAlpha(accent, 0), Shader.TileMode.CLAMP));
            c.drawPath(area, fill);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1.2f);
            line.setColor(0x2EFFFFFF);
            c.drawPath(edge, line);
            out[i] = b;
        }
        return out;
    }

    private void drawWaves(Canvas canvas, Bitmap[] ls, int w, int h) {
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

    // ---- Orbit: broken rings turning at different speeds ----

    private Bitmap[] orbitLayers(int w, int h) {
        Bitmap[] out = new Bitmap[3];
        int size = Math.max(1, Math.round(h * 1.3f * SCALE));
        for (int i = 0; i < out.length; i++) {
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            float cx = size / 2f;
            float r = size * (0.22f + 0.12f * i);
            RectF oval = new RectF(cx - r, cx - r, cx + r, cx + r);
            p.setStrokeWidth(size * (0.018f - 0.004f * i));
            p.setColor(withAlpha(accent, 0x70 - i * 0x18));
            int arcs = 3 + i;
            float sweep = 360f / arcs * 0.62f;
            for (int a = 0; a < arcs; a++) c.drawArc(oval, a * 360f / arcs + i * 20, sweep, false, p);
            p.setStrokeWidth(size * 0.003f);
            p.setColor(withAlpha(accent, 0x30));
            c.drawCircle(cx, cx, r + size * 0.035f, p);
            out[i] = b;
        }
        return out;
    }

    private void drawOrbit(Canvas canvas, Bitmap[] ls, int w, int h) {
        float cx = w * 0.70f;
        float cy = h * 0.58f;
        if (orbitGlow == null) {
            orbitGlow = new RadialGradient(cx, cy, h * 0.55f, withAlpha(accent, 0x30), withAlpha(accent, 0), Shader.TileMode.CLAMP);
        }
        shape.setShader(orbitGlow);
        canvas.drawCircle(cx, cy, h * 0.55f, shape);
        shape.setShader(null);
        for (int i = 0; i < ls.length; i++) {
            Bitmap b = ls[i];
            float half = b.getWidth() / SCALE / 2f;
            float deg = seconds * (6f + 4f * i) * (i % 2 == 0 ? 1 : -1);
            canvas.save();
            canvas.rotate(deg, cx, cy);
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set(cx - half, cy - half, cx + half, cy + half);
            canvas.drawBitmap(b, src, dst, blit);
            canvas.restore();
        }
    }

    // ---- Pop: rounded shapes drifting (native GPU shapes, no bitmaps) ----

    private static final float[][] POP_SHAPES = {
            // x, y, size (fraction of height), kind (0 circle, 1 rounded square, 2 ring), speed, phase
            {0.12f, 0.70f, 0.22f, 0, 0.30f, 0.0f},
            {0.30f, 0.30f, 0.10f, 1, 0.45f, 1.1f},
            {0.48f, 0.78f, 0.14f, 2, 0.35f, 2.3f},
            {0.66f, 0.40f, 0.26f, 0, 0.25f, 3.1f},
            {0.82f, 0.80f, 0.12f, 1, 0.40f, 4.2f},
            {0.92f, 0.22f, 0.16f, 2, 0.32f, 5.0f},
            {0.40f, 0.55f, 0.07f, 0, 0.55f, 0.7f},
    };

    private void drawPop(Canvas canvas, int w, int h) {
        for (int i = 0; i < POP_SHAPES.length; i++) {
            float[] s = POP_SHAPES[i];
            double t = seconds * s[4] + s[5];
            float x = (float) (w * s[0] + Math.sin(t) * h * 0.04);
            float y = (float) (h * s[1] + Math.cos(t * 0.8) * h * 0.05);
            float r = h * s[2] / 2f * (1f + 0.05f * (float) Math.sin(t * 1.3));
            boolean light = i % 2 == 0;
            shape.setColor(light ? withAlpha(accent, 0x2E) : 0x40000000);
            switch ((int) s[3]) {
                case 1:
                    canvas.save();
                    canvas.rotate((float) (t * 12), x, y);
                    dst.set(x - r, y - r, x + r, y + r);
                    canvas.drawRoundRect(dst, r * 0.35f, r * 0.35f, shape);
                    canvas.restore();
                    break;
                case 2:
                    shape.setStyle(Paint.Style.STROKE);
                    shape.setStrokeWidth(r * 0.22f);
                    canvas.drawCircle(x, y, r, shape);
                    shape.setStyle(Paint.Style.FILL);
                    break;
                default:
                    canvas.drawCircle(x, y, r, shape);
                    break;
            }
        }
    }

    // ---- Bloom: soft petals slowly turning and breathing ----

    private Bitmap[] bloomLayers(int w, int h) {
        int size = Math.max(1, Math.round(h * 1.2f * SCALE));
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float cx = size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int petals = 7;
        for (int i = 0; i < petals; i++) {
            c.save();
            c.rotate(i * 360f / petals, cx, cx);
            RectF petal = new RectF(cx - size * 0.11f, cx - size * 0.48f, cx + size * 0.11f, cx);
            p.setShader(new LinearGradient(cx, cx - size * 0.48f, cx, cx,
                    withAlpha(accent, 0x00), withAlpha(accent, 0x5A), Shader.TileMode.CLAMP));
            c.drawOval(petal, p);
            c.restore();
        }
        p.setShader(new RadialGradient(cx, cx, size * 0.18f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cx, size * 0.18f, p);
        return new Bitmap[]{b};
    }

    private void drawBloom(Canvas canvas, Bitmap[] ls, int w, int h) {
        Bitmap b = ls[0];
        float cx = w * 0.64f;
        float cy = h * 0.62f;
        src.set(0, 0, b.getWidth(), b.getHeight());
        for (int i = 0; i < 2; i++) {
            float breathe = 1f + 0.04f * (float) Math.sin(seconds * 0.4 + i * 2);
            float half = b.getWidth() / SCALE / 2f * breathe * (i == 0 ? 1f : 0.7f);
            canvas.save();
            canvas.rotate(seconds * (i == 0 ? 3f : -5f) + i * 25, cx, cy);
            blit.setAlpha(i == 0 ? 255 : 150);
            dst.set(cx - half, cy - half, cx + half, cy + half);
            canvas.drawBitmap(b, src, dst, blit);
            canvas.restore();
        }
        blit.setAlpha(255);
    }
}
