package io.github.yohannaftali.axshome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Tells AXS Remote which wallpaper the home screen uses and its accent colour,
 * so the phone remote page can match it. Only apps signed with the same key
 * hold the signature permission it checks. Nothing else is shared.
 */
public final class ThemeProvider extends ContentProvider {
    static final String PERMISSION = "io.github.yohannaftali.axshome.permission.THEME";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        if (c == null || !"theme".equals(method)) return null;
        c.enforceCallingPermission(PERMISSION, "The theme is for AXS Remote only");
        String style = c.getSharedPreferences("home", Context.MODE_PRIVATE)
                .getString(SettingsPanel.PREF_WALLPAPER, StyleView.WAVES);
        Bundle b = new Bundle();
        b.putString("wallpaper", style);
        b.putInt("accent", accent(style));
        return b;
    }

    /** Same accents as HomeActivity.accent() for the built-in wallpapers. */
    static int accent(String style) {
        switch (style) {
            case StyleView.ORBIT: return 0xFF3BD16F;
            case StyleView.POP: return 0xFFFF5A64;
            case StyleView.BLOOM: return 0xFF6FB6FF;
            default: return 0xFF3D7BFF;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
