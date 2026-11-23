package io.github.yohannaftali.axsclock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets AXS Home's Screensaver settings read and change AXS Clock's settings.
 * Only apps signed with the same key hold the signature permission it checks,
 * so other apps and `adb shell content` are refused. API keys can be set or
 * cleared but are only ever returned masked (last four characters).
 *
 * call("get") → current settings; call("catalogue") → tickers and sources;
 * call("put", extras) → stores the given fields and returns "get".
 */
public final class SettingsProvider extends ContentProvider {
    static final String PERMISSION = "io.github.yohannaftali.axsclock.permission.SETTINGS";
    private static final int[] REFRESH = {1, 2, 5, 15};
    private static final String[] KEYED = {Catalogue.TWELVE, Catalogue.FINNHUB, Feed.COINGECKO};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        if (c == null) return null;
        c.enforceCallingPermission(PERMISSION, "AXS Clock settings are for AXS Home only");
        switch (method) {
            case "catalogue": return catalogue();
            case "put":
                if (extras != null) put(c, extras);
                return get(c);
            case "get": return get(c);
            default: return null;
        }
    }

    private static Bundle get(Context c) {
        Conf conf = Conf.load(c);
        Bundle b = new Bundle();
        b.putString("tickers", Conf.encode(conf.tickers));
        b.putString("background", conf.background);
        b.putBoolean("weather", conf.weather);
        b.putString("weather_city", conf.weatherCity);
        b.putInt("refresh", conf.refreshMin);
        b.putBoolean("yahoo", conf.yahoo);
        SharedPreferences secrets = c.getSharedPreferences(Conf.SECRETS, Context.MODE_PRIVATE);
        for (String s : KEYED) {
            String k = secrets.getString("key." + s, "");
            b.putString("key." + s, k.isEmpty() ? "" : "••••" + k.substring(Math.max(0, k.length() - 4)));
        }
        for (String[] s : Catalogue.SOURCES) b.putBoolean("available." + s[0], conf.available(s[0]));
        return b;
    }

    private static Bundle catalogue() {
        StringBuilder items = new StringBuilder();
        for (Catalogue.Item i : Catalogue.ITEMS) {
            items.append(i.label).append('|').append(i.group).append('|').append(String.join(",", i.options)).append('\n');
        }
        StringBuilder sources = new StringBuilder();
        for (String[] s : Catalogue.SOURCES) sources.append(s[0]).append('|').append(s[1]).append('|').append(s[2]).append('\n');
        Bundle b = new Bundle();
        b.putString("items", items.toString());
        b.putString("sources", sources.toString());
        return b;
    }

    /** Stores only known fields with valid values; anything else is ignored. */
    private static void put(Context c, Bundle in) {
        SharedPreferences.Editor e = c.getSharedPreferences(Conf.PREFS, Context.MODE_PRIVATE).edit();
        if (in.containsKey("tickers")) {
            List<Conf.Ticker> list = new ArrayList<>();
            for (String line : in.getString("tickers", "").split("\n")) {
                String[] f = line.split("\\|", -1);
                if (f.length < 3 || list.size() >= Conf.MAX_TICKERS) continue;
                boolean invert = f.length > 3 && f[3].equals("invert");
                if (known(f[0], f[1] + ":" + f[2] + (invert ? ":invert" : ""))) {
                    list.add(new Conf.Ticker(f[0], f[1], f[2], invert));
                }
            }
            e.putString("tickers", Conf.encode(list));
        }
        if (in.containsKey("background") && (StyleView.known(in.getString("background"))
                || "black".equals(in.getString("background")))) {
            e.putString("background", in.getString("background"));
        }
        if (in.containsKey("weather")) e.putBoolean("weather", in.getBoolean("weather"));
        if (in.containsKey("weather_city")) {
            String city = in.getString("weather_city", "").trim();
            if (city.length() <= 60 && !city.contains("|") && !city.contains("\n")) e.putString("weather_city", city);
        }
        if (in.containsKey("refresh")) {
            int r = in.getInt("refresh");
            for (int ok : REFRESH) if (r == ok) e.putInt("refresh", r);
        }
        if (in.containsKey("yahoo")) e.putBoolean("yahoo", in.getBoolean("yahoo"));
        e.commit();

        SharedPreferences.Editor s = c.getSharedPreferences(Conf.SECRETS, Context.MODE_PRIVATE).edit();
        for (String source : KEYED) {
            if (!in.containsKey("key." + source)) continue;
            String k = in.getString("key." + source, "").trim();
            if (k.isEmpty()) s.remove("key." + source);
            else if (k.matches("[A-Za-z0-9_\\-]{8,100}")) s.putString("key." + source, k);
        }
        s.commit();
    }

    private static boolean known(String label, String option) {
        Catalogue.Item item = Catalogue.item(label);
        if (item == null) return false;
        for (String o : item.options) if (o.equals(option)) return true;
        return false;
    }

    // Settings travel through call() only.

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
