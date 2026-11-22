package io.github.yohannaftali.axsclock;

import android.view.View;

/** Something drawn behind the screensaver. */
interface Backdrop {
    View view();

    /** Start or resume animation/playback. Called only while the screensaver shows. */
    void start();

    /** Pause; must leave no CPU or decoder work running. */
    void stop();

    /** Free decoders and bitmaps; the backdrop is not used again. */
    void release();
}
