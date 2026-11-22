package io.github.yohannaftali.axshome;

import android.view.View;

/** Something drawn behind the home screen: a built-in style or the owner's file. */
interface Backdrop {
    View view();

    /** Start or resume animation/playback. Called only while home is on screen. */
    void start();

    /** Pause; must leave no CPU or decoder work running. */
    void stop();

    /** Free decoders and bitmaps; the backdrop is not used again. */
    void release();
}
