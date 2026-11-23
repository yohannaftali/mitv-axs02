package io.github.yohannaftali.axsclock;

import android.service.dreams.DreamService;

/** The screensaver service; what it shows is in Face. */
public final class ClockDream extends DreamService {
    private Face face;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        face = new Face(this);
        setContentView(face.build());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        face.start();
    }

    @Override
    public void onDreamingStopped() {
        face.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (face != null) face.release();
        super.onDetachedFromWindow();
    }
}
