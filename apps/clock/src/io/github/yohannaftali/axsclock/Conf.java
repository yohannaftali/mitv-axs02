package io.github.yohannaftali.axsclock;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** clock.conf (built into the APK) plus the optional local.conf that apply.sh pushes to the TV. */
final class Conf {
    static final int MAX_TICKERS = 8;

    String timeFormat = "HH:mm";
    String dateFormat = "EEEE, d MMMM yyyy";
    String background = StyleView.WAVES;
    boolean weather = true;
    String weatherCity = "";
    final List<Ticker> tickers = new ArrayList<>();

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
        return conf;
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
