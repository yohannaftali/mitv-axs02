package io.github.yohannaftali.axsclock;

import java.util.ArrayList;
import java.util.List;

/**
 * The tickers the owner can add in Home settings → Screensaver, with every
 * source that can provide each one. Keyless sources with clean terms come
 * first; Yahoo Finance (unofficial) and the keyed providers are opt-in.
 */
final class Catalogue {
    static final String YAHOO = "yahoo";
    static final String TWELVE = "twelvedata";
    static final String FINNHUB = "finnhub";

    /** Sources in display order: id, name, what to know about it. */
    static final String[][] SOURCES = {
            {Feed.COINGECKO, "CoinGecko", "Crypto, every 2 min (1 min with a free demo key). No account needed."},
            {Feed.GOLD, "goldprice.dev", "Gold and silver spot, 15 min delayed. No account needed."},
            {Feed.ECB, "ECB", "Euro reference rates, once per working day. No account needed."},
            {YAHOO, "Yahoo Finance", "Live FX, indices (IHSG, S&P 500, Dow, NASDAQ), oil, metals, crypto. Unofficial: may stop working."},
            {TWELVE, "Twelve Data", "FX, metals, crypto, US stocks. Free key at twelvedata.com (800 calls a day)."},
            {FINNHUB, "Finnhub", "US stocks and ETFs (SPY, QQQ). Free key at finnhub.io."},
    };

    static final class Item {
        final String label;
        final String group;
        /** "source:symbol" or "source:symbol:invert", preferred first. */
        final String[] options;

        Item(String label, String group, String... options) {
            this.label = label;
            this.group = group;
            this.options = options;
        }
    }

    static final Item[] ITEMS = {
            new Item("USD/IDR", "Currencies", "ecb:IDR", "yahoo:IDR=X", "twelvedata:USD/IDR"),
            new Item("EUR/USD", "Currencies", "ecb:EUR:invert", "yahoo:EURUSD=X", "twelvedata:EUR/USD"),
            new Item("GBP/USD", "Currencies", "ecb:GBP:invert", "yahoo:GBPUSD=X", "twelvedata:GBP/USD"),
            new Item("USD/JPY", "Currencies", "ecb:JPY", "yahoo:JPY=X", "twelvedata:USD/JPY"),
            new Item("USD/SGD", "Currencies", "ecb:SGD", "yahoo:SGD=X", "twelvedata:USD/SGD"),
            new Item("AUD/USD", "Currencies", "ecb:AUD:invert", "yahoo:AUDUSD=X", "twelvedata:AUD/USD"),
            new Item("XAU/USD", "Metals", "goldprice:XAU", "yahoo:GC=F", "twelvedata:XAU/USD"),
            new Item("XAG/USD", "Metals", "goldprice:XAG", "yahoo:SI=F", "twelvedata:XAG/USD"),
            new Item("BTC/USD", "Crypto", "coingecko:bitcoin", "yahoo:BTC-USD", "twelvedata:BTC/USD"),
            new Item("ETH/USD", "Crypto", "coingecko:ethereum", "yahoo:ETH-USD", "twelvedata:ETH/USD"),
            new Item("SOL/USD", "Crypto", "coingecko:solana", "yahoo:SOL-USD", "twelvedata:SOL/USD"),
            new Item("IHSG", "Indices", "yahoo:^JKSE"),
            new Item("S&P 500", "Indices", "yahoo:^GSPC"),
            new Item("Dow Jones", "Indices", "yahoo:^DJI"),
            new Item("NASDAQ", "Indices", "yahoo:^IXIC"),
            new Item("Nikkei 225", "Indices", "yahoo:^N225"),
            new Item("WTI oil", "Commodities", "yahoo:CL=F"),
            new Item("Brent oil", "Commodities", "yahoo:BZ=F"),
            new Item("SPY", "US ETFs", "finnhub:SPY", "twelvedata:SPY", "yahoo:SPY"),
            new Item("QQQ", "US ETFs", "finnhub:QQQ", "twelvedata:QQQ", "yahoo:QQQ"),
    };

    private Catalogue() {
    }

    static Item item(String label) {
        for (Item i : ITEMS) if (i.label.equals(label)) return i;
        return null;
    }

    static String name(String source) {
        for (String[] s : SOURCES) if (s[0].equals(source)) return s[1];
        return source;
    }

    /** The options of an item whose source is usable now. */
    static List<String> usable(Item item, Conf conf) {
        List<String> out = new ArrayList<>();
        for (String o : item.options) if (conf.available(o.split(":", 2)[0])) out.add(o);
        return out;
    }

    static Conf.Ticker ticker(String label, String option) {
        String[] f = option.split(":", -1);
        // Symbols such as "USD/IDR" contain no ':'; "source:symbol[:invert]".
        return new Conf.Ticker(label, f[0], f[1], f.length > 2 && f[2].equals("invert"));
    }
}
