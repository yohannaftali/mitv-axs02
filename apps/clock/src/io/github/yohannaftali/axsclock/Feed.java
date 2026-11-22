package io.github.yohannaftali.axsclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * Fetches prices and weather on a background thread, only between start()
 * and stop() (while the screensaver shows). Results go to SharedPreferences,
 * so the panel shows the last values instantly next time and while offline.
 * Requests carry no identifier: no key, no account, no device id.
 */
final class Feed {
    static final String ECB = "ecb";
    static final String GOLD = "goldprice";
    static final String COINGECKO = "coingecko";

    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    private static final Map<String, Long> EVERY = new HashMap<>();
    static {
        EVERY.put(COINGECKO, 2 * MIN);
        EVERY.put(GOLD, 5 * MIN);
        EVERY.put(ECB, HOUR);
        EVERY.put("weather", 15 * MIN);
    }
    private static final int TIMEOUT_MS = 8000;
    private static final String AGENT = "AXSClock/1.1";

    /** One cached value; NaN when unknown. */
    static final class Value {
        double price = Double.NaN;
        double change = Double.NaN;
        long time;
    }

    static final class Weather {
        double temp = Double.NaN;
        double max = Double.NaN;
        double min = Double.NaN;
        int code = -1;
        long time;
        String place = "";
    }

    private final Context context;
    private final Conf conf;
    private final SharedPreferences cache;
    private final Runnable onUpdate;
    private final Map<String, Long> attempted = new HashMap<>();
    private HandlerThread thread;
    private boolean fromCity;
    private Handler worker;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            boolean changed = false;
            for (String source : EVERY.keySet()) {
                if (!due(source)) continue;
                attempted.put(source, System.currentTimeMillis());
                try {
                    changed |= fetch(source);
                } catch (Exception ignored) {
                    // Offline or the provider failed: keep the cached values.
                }
            }
            if (changed) onUpdate.run();
            if (worker != null) worker.postDelayed(this, 30_000);
        }
    };

    Feed(Context context, Conf conf, Runnable onUpdate) {
        this.context = context;
        this.conf = conf;
        this.onUpdate = onUpdate;
        this.cache = context.getSharedPreferences("feed", Context.MODE_PRIVATE);
    }

    void start() {
        if (thread != null) return;
        thread = new HandlerThread("feed");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(tick);
    }

    void stop() {
        if (thread == null) return;
        worker.removeCallbacksAndMessages(null);
        worker = null;
        thread.quitSafely();
        thread = null;
    }

    private boolean due(String source) {
        if (source.equals("weather") ? !conf.weather : !used(source)) return false;
        long now = System.currentTimeMillis();
        long every = EVERY.get(source);
        Long tried = attempted.get(source);
        if (tried != null && now - tried < every) return false;
        return now - cache.getLong("ok." + source, 0) >= every;
    }

    private boolean used(String source) {
        for (Conf.Ticker t : conf.tickers) if (t.source.equals(source)) return true;
        return false;
    }

    // ---- reading the cache (any thread) ----

    Value value(Conf.Ticker t) {
        Value v = new Value();
        String s = cache.getString("t." + t.source + "." + t.symbol, null);
        if (s == null) return v;
        String[] f = s.split(";");
        v.price = Double.parseDouble(f[0]);
        v.change = Double.parseDouble(f[1]);
        v.time = Long.parseLong(f[2]);
        if (t.invert && v.price != 0) {
            v.price = 1 / v.price;
            v.change = 1 / (1 + v.change) - 1;
        }
        return v;
    }

    Weather weather() {
        Weather w = new Weather();
        String s = cache.getString("weather", null);
        if (s == null) return w;
        String[] f = s.split(";");
        w.temp = Double.parseDouble(f[0]);
        w.code = Integer.parseInt(f[1]);
        w.max = Double.parseDouble(f[2]);
        w.min = Double.parseDouble(f[3]);
        w.time = Long.parseLong(f[4]);
        if (f.length > 5) w.place = f[5];
        return w;
    }

    /** Publication date (yyyy-MM-dd) of the cached ECB rates, "" if none. */
    String ecbDate() {
        return cache.getString("date." + ECB, "");
    }

    /** Newest successful update of anything, 0 if none. */
    long lastUpdate() {
        long t = 0;
        for (String source : EVERY.keySet()) t = Math.max(t, cache.getLong("ok." + source, 0));
        return t;
    }

    // ---- fetching (worker thread) ----

    private boolean fetch(String source) throws Exception {
        switch (source) {
            case COINGECKO: return coingecko();
            case GOLD: return gold();
            case ECB: return ecb();
            default: return weatherNow();
        }
    }

    private List<String> symbols(String source) {
        List<String> out = new ArrayList<>();
        for (Conf.Ticker t : conf.tickers) if (t.source.equals(source) && !out.contains(t.symbol)) out.add(t.symbol);
        return out;
    }

    private boolean coingecko() throws Exception {
        List<String> ids = symbols(COINGECKO);
        JSONObject o = new JSONObject(get("https://api.coingecko.com/api/v3/simple/price?vs_currencies=usd"
                + "&include_24hr_change=true&ids=" + enc(String.join(",", ids))));
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = cache.edit();
        for (String id : ids) {
            JSONObject c = o.optJSONObject(id);
            if (c == null || !c.has("usd")) continue;
            put(e, COINGECKO, id, c.getDouble("usd"), c.optDouble("usd_24h_change", Double.NaN) / 100, now);
        }
        e.putLong("ok." + COINGECKO, now).apply();
        return true;
    }

    /** goldprice.dev free tier has no daily history, so the change is against our own sample from ~24 h ago. */
    private boolean gold() throws Exception {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = cache.edit();
        for (String symbol : symbols(GOLD)) {
            JSONObject o = new JSONObject(get("https://api.goldprice.dev/v1/prices?symbol=" + enc(symbol)));
            JSONArray list = o.getJSONArray("symbols");
            if (list.length() == 0) continue;
            double price = Double.parseDouble(list.getJSONObject(0).getString("price"));
            put(e, GOLD, symbol, price, dayChange(e, symbol, price, now), now);
        }
        e.putLong("ok." + GOLD, now).apply();
        return true;
    }

    private double dayChange(SharedPreferences.Editor e, String symbol, double price, long now) {
        TreeMap<Long, Double> samples = new TreeMap<>();
        String s = cache.getString("hist." + symbol, "");
        for (String pair : s.split(",")) {
            int i = pair.indexOf(':');
            if (i > 0) samples.put(Long.parseLong(pair.substring(0, i)), Double.parseDouble(pair.substring(i + 1)));
        }
        samples.headMap(now - 30 * HOUR).clear();
        if (samples.isEmpty() || now - samples.lastKey() >= HOUR) samples.put(now, price);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Long, Double> m : samples.entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(m.getKey()).append(':').append(m.getValue());
        }
        e.putString("hist." + symbol, b.toString());
        Map.Entry<Long, Double> ref = samples.floorEntry(now - 20 * HOUR);
        if (ref == null || now - ref.getKey() > 28 * HOUR) return Double.NaN;
        return price / ref.getValue() - 1;
    }

    /** ECB reference rates (published once per working day) via Frankfurter; change is against the previous publication. */
    private boolean ecb() throws Exception {
        List<String> symbols = symbols(ECB);
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        day.setTimeZone(TimeZone.getTimeZone("UTC"));
        String from = day.format(new Date(System.currentTimeMillis() - 10 * 24 * HOUR));
        JSONObject rates = new JSONObject(get("https://api.frankfurter.dev/v1/" + from + "..?base=USD&symbols="
                + enc(String.join(",", symbols)))).getJSONObject("rates");
        TreeMap<String, JSONObject> days = new TreeMap<>();
        for (Iterator<String> it = rates.keys(); it.hasNext(); ) {
            String d = it.next();
            days.put(d, rates.getJSONObject(d));
        }
        if (days.isEmpty()) return false;
        JSONObject last = days.lastEntry().getValue();
        Map.Entry<String, JSONObject> prevEntry = days.lowerEntry(days.lastKey());
        JSONObject prev = prevEntry == null ? null : prevEntry.getValue();
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = cache.edit();
        for (String symbol : symbols) {
            if (!last.has(symbol)) continue;
            double p = last.getDouble(symbol);
            double change = prev != null && prev.has(symbol) ? p / prev.getDouble(symbol) - 1 : Double.NaN;
            put(e, ECB, symbol, p, change, now);
        }
        e.putString("date." + ECB, days.lastKey()).putLong("ok." + ECB, now).apply();
        return true;
    }

    private boolean weatherNow() throws Exception {
        double[] at = location();
        if (at == null) return false;
        String place = place(at).replace(";", ",");
        JSONObject o = new JSONObject(get(String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                        + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=1", at[0], at[1])));
        JSONObject cur = o.getJSONObject("current");
        JSONObject daily = o.getJSONObject("daily");
        long now = System.currentTimeMillis();
        cache.edit().putString("weather", cur.getDouble("temperature_2m") + ";" + cur.getInt("weather_code") + ";"
                + daily.getJSONArray("temperature_2m_max").getDouble(0) + ";"
                + daily.getJSONArray("temperature_2m_min").getDouble(0) + ";" + now + ";" + place)
                .putLong("ok.weather", now).apply();
        return true;
    }

    /** The TV's approximate location, else the configured city; rounded to ~1 km before it leaves the TV. */
    private double[] location() throws Exception {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            for (String p : new String[]{LocationManager.NETWORK_PROVIDER, "fused", LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null) {
                        fromCity = false;
                        return round(l.getLatitude(), l.getLongitude());
                    }
                } catch (SecurityException | IllegalArgumentException ignored) {
                    // No permission or no such provider: try the next one, then the city.
                }
            }
        }
        if (conf.weatherCity.isEmpty()) return null;
        fromCity = true;
        String key = "geo." + conf.weatherCity;
        String hit = cache.getString(key, null);
        if (hit != null) {
            String[] f = hit.split(";");
            return new double[]{Double.parseDouble(f[0]), Double.parseDouble(f[1])};
        }
        JSONArray results = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                + enc(conf.weatherCity))).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        double[] at = round(r.getDouble("latitude"), r.getDouble("longitude"));
        cache.edit().putString(key, at[0] + ";" + at[1]).apply();
        return at;
    }

    /**
     * City shown on the weather line: the configured city when the TV has no
     * location, otherwise the city from Android's own geocoder (the same
     * service that provides the TV's location), cached per rounded position.
     */
    private String place(double[] at) {
        if (fromCity) return conf.weatherCity;
        String key = "city." + at[0] + "," + at[1];
        String hit = cache.getString(key, null);
        if (hit != null) return hit;
        String name = "";
        try {
            if (Geocoder.isPresent()) {
                List<Address> list = new Geocoder(context, Locale.getDefault()).getFromLocation(at[0], at[1], 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    // City level, not the district: sub-admin area (regency/city) first.
                    name = a.getSubAdminArea() != null ? a.getSubAdminArea()
                            : a.getLocality() != null ? a.getLocality()
                            : a.getAdminArea() != null ? a.getAdminArea() : "";
                    name = name.replaceFirst("^(Kota|Kabupaten|City of) ", "");
                }
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // No geocoder backend or offline: show the weather without a name.
        }
        if (!name.isEmpty()) cache.edit().putString(key, name).apply();
        return name;
    }

    private static double[] round(double lat, double lon) {
        return new double[]{Math.round(lat * 100) / 100.0, Math.round(lon * 100) / 100.0};
    }

    private static void put(SharedPreferences.Editor e, String source, String symbol, double price, double change, long now) {
        e.putString("t." + source + "." + symbol, price + ";" + change + ";" + now);
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", AGENT);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 256 * 1024) throw new IOException("response too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
