package io.github.yohannaftali.axsclock;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * clock.conf (built into the APK), then the optional local.conf that apply.sh
 * pushes to the TV, then what the owner chose in Home settings → Screensaver
 * (SharedPreferences "settings", written through SettingsProvider). API keys
 * live in the separate private "secrets" file and never leave the app.
 */
final class Conf {
    static final int MAX_TICKERS = 8;

    String timeFormat = "HH:mm";
    String dateFormat = "EEEE, d MMMM yyyy";
    String background = StyleView.WAVES;
    boolean weather = true;
    String weatherCity = "";
    /** Refresh interval the owner picked, in minutes; each source is clamped to its own minimum. */
    int refreshMin = 2;
    boolean yahoo;
    String keyTwelve = "";
    String keyFinnhub = "";
    String keyCoingecko = "";
    final List<Ticker> tickers = new ArrayList<>();

    static final String PREFS = "settings";
    static final String SECRETS = "secrets";

    static final class Ticker {
        final String label;
        final String source;
        final String symbol;
        final boolean invert;

        Ticker(String label, String source, String symbol, boolean invert) {
            this.label = label;
            this.source = source;
            this.symbol = symbol;
            this.invert = invert;
        }
    }

    static Conf load(Context c) {
        Conf conf = new Conf();
        try (InputStream in = c.getAssets().open("clock.conf")) {
            conf.read(in);
        } catch (IOException ignored) {
            // Defaults.
        }
        File dir = c.getExternalFilesDir(null);
        File local = dir == null ? null : new File(dir, "local.conf");
        if (local != null && local.isFile()) {
            try (InputStream in = new FileInputStream(local)) {
                conf.read(in);
            } catch (IOException ignored) {
                // Keep clock.conf values.
            }
        }
        conf.applySettings(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                c.getSharedPreferences(SECRETS, Context.MODE_PRIVATE));
        return conf;
    }

    private void applySettings(SharedPreferences p, SharedPreferences secrets) {
        background = p.getString("background", background);
        if (p.contains("weather")) weather = p.getBoolean("weather", weather);
        if (p.contains("weather_city")) weatherCity = p.getString("weather_city", "").trim();
        refreshMin = Math.max(1, p.getInt("refresh", refreshMin));
        yahoo = p.getBoolean("yahoo", false);
        keyTwelve = secrets.getString("key." + Catalogue.TWELVE, "");
        keyFinnhub = secrets.getString("key." + Catalogue.FINNHUB, "");
        keyCoingecko = secrets.getString("key." + Feed.COINGECKO, "");
        if (p.contains("tickers")) {
            tickers.clear();
            for (String line : p.getString("tickers", "").split("\n")) {
                String[] f = line.split("\\|", -1);
                if (f.length >= 3 && tickers.size() < MAX_TICKERS) {
                    tickers.add(new Ticker(f[0], f[1], f[2], f.length > 3 && f[3].equals("invert")));
                }
            }
        }
        // A source turned off: use another one for the same ticker, or drop it.
        for (int i = tickers.size() - 1; i >= 0; i--) {
            Ticker t = tickers.get(i);
            if (available(t.source)) continue;
            Catalogue.Item item = Catalogue.item(t.label);
            List<String> usable = item == null ? new ArrayList<>() : Catalogue.usable(item, this);
            if (usable.isEmpty()) tickers.remove(i);
            else tickers.set(i, Catalogue.ticker(t.label, usable.get(0)));
        }
    }

    /** Keyless sources always; Yahoo when switched on; keyed providers when a key is set. */
    boolean available(String source) {
        switch (source) {
            case Catalogue.YAHOO: return yahoo;
            case Catalogue.TWELVE: return !keyTwelve.isEmpty();
            case Catalogue.FINNHUB: return !keyFinnhub.isEmpty();
            default: return true;
        }
    }

    /** One line per ticker, as stored under "tickers". */
    static String encode(List<Ticker> list) {
        StringBuilder b = new StringBuilder();
        for (Ticker t : list) {
            if (b.length() > 0) b.append('\n');
            b.append(t.label).append('|').append(t.source).append('|').append(t.symbol);
            if (t.invert) b.append("|invert");
        }
        return b.toString();
    }

    private void read(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\|", -1);
            switch (f[0]) {
                case "clock":
                    if (f.length >= 3) {
                        if (!f[1].isEmpty()) timeFormat = f[1];
                        if (!f[2].isEmpty()) dateFormat = f[2];
                    }
                    break;
                case "background":
                    if (f.length >= 2 && !f[1].isEmpty()) background = f[1];
                    break;
                case "weather":
                    if (f.length >= 2) weather = !f[1].equals("off");
                    break;
                case "weather_city":
                    if (f.length >= 2) weatherCity = f[1].trim();
                    break;
                case "ticker":
                    if (f.length >= 4 && tickers.size() < MAX_TICKERS) {
                        tickers.add(new Ticker(f[1], f[2], f[3], f.length >= 5 && f[4].equals("invert")));
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
