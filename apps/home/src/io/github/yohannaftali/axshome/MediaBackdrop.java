package io.github.yohannaftali.axshome;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.ImageDecoder;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.Locale;

/**
 * The owner's own wallpaper: a still image, an animated GIF/WebP (Android's
 * AnimatedImageDrawable, no library) or a looping video. Decoding happens off
 * the UI thread at screen size; nothing plays while stopped.
 */
final class MediaBackdrop extends FrameLayout implements Backdrop {
    static final String[] IMAGE_EXT = {"png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif"};
    static final String[] VIDEO_EXT = {"mp4", "m4v", "webm", "mkv", "3gp"};

    private final File file;
    private final boolean sound;
    private final Runnable onError;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ImageView image;
    private AnimatedImageDrawable animation;
    private TextureView video;
    private MediaPlayer player;
    private Surface surface;
    private boolean prepared;
    private boolean running;
    private boolean released;

    static boolean supported(String name) {
        return isVideo(name) || has(IMAGE_EXT, ext(name));
    }

    static boolean isVideo(String name) {
        return has(VIDEO_EXT, ext(name));
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean has(String[] list, String e) {
        for (String s : list) if (s.equals(e)) return true;
        return false;
    }

    MediaBackdrop(Context c, File file, boolean sound, Runnable onError) {
        super(c);
        this.file = file;
        this.sound = sound;
        this.onError = onError;
        setBackgroundColor(0xFF05070D);
        if (isVideo(file.getName())) setUpVideo(); else setUpImage();
    }

    @Override public View view() { return this; }

    private void fail() {
        if (released) return;
        ui.post(onError);
    }

    // ---- images and animations ----

    private void setUpImage() {
        image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(image, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        new Thread(() -> {
            try {
                Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file), (decoder, info, src) -> {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    float s = Math.min(1f, Math.max((float) sw / w, (float) sh / h));
                    decoder.setTargetSize(Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)));
                });
                ui.post(() -> {
                    if (released) return;
                    image.setImageDrawable(d);
                    if (d instanceof AnimatedImageDrawable) {
                        animation = (AnimatedImageDrawable) d;
                        if (running) animation.start();
                    }
                });
            } catch (Exception | OutOfMemoryError e) {
                fail();
            }
        }, "wallpaper-decode").start();
    }

    // ---- video ----

    private void setUpVideo() {
        video = new TextureView(getContext());
        addView(video, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                openPlayer(st);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                closePlayer();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
    }

    private void openPlayer(SurfaceTexture st) {
        if (released) return;
        try {
            surface = new Surface(st);
            player = new MediaPlayer();
            player.setDataSource(file.getPath());
            player.setSurface(surface);
            player.setLooping(true);
            float v = sound ? 1f : 0f;
            player.setVolume(v, v);
            player.setOnVideoSizeChangedListener((mp, vw, vh) -> centerCrop(vw, vh));
            player.setOnErrorListener((mp, what, extra) -> {
                fail();
                return true;
            });
            player.setOnPreparedListener(mp -> {
                prepared = true;
                if (running) mp.start();
            });
            player.prepareAsync();
        } catch (Exception e) {
            fail();
        }
    }

    private void centerCrop(int vw, int vh) {
        int w = video.getWidth();
        int h = video.getHeight();
        if (vw == 0 || vh == 0 || w == 0 || h == 0) return;
        float scale = Math.max((float) w / vw, (float) h / vh);
        Matrix m = new Matrix();
        m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f);
        video.setTransform(m);
    }

    private void closePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        prepared = false;
    }

    // ---- lifecycle ----

    @Override
    public void start() {
        running = true;
        if (animation != null) animation.start();
        if (player != null && prepared && !player.isPlaying()) player.start();
    }

    @Override
    public void stop() {
        running = false;
        if (animation != null) animation.stop();
        if (player != null && prepared && player.isPlaying()) player.pause();
    }

    @Override
    public void release() {
        stop();
        released = true;
        closePlayer();
        if (image != null) image.setImageDrawable(null);
        animation = null;
    }
}
