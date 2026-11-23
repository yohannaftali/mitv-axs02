package io.github.yohannaftali.axsclock;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** Home settings → Screensaver → Preview: the screensaver as it will look; any key closes it. */
public final class PreviewActivity extends Activity {
    private Face face;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        face = new Face(this);
        setContentView(face.build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        face.start();
    }

    @Override
    protected void onPause() {
        face.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        face.release();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
