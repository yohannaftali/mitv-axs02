package io.github.yohannaftali.axsremote;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal QR code encoder (ISO/IEC 18004): byte mode, error correction level
 * M, versions 1–10 (up to 213 bytes), best of the 8 masks. Enough for the
 * remote's URL; no library needed.
 */
final class Qr {
    /** Per version 1..10 at level M: EC codewords per block, then {blocks, data codewords} for group 1 and 2. */
    private static final int[][] BLOCKS = {
            {10, 1, 16, 0, 0}, {16, 1, 28, 0, 0}, {26, 1, 44, 0, 0}, {18, 2, 32, 0, 0}, {24, 2, 43, 0, 0},
            {16, 4, 27, 0, 0}, {18, 4, 31, 0, 0}, {22, 2, 38, 2, 39}, {22, 3, 36, 2, 37}, {26, 4, 43, 1, 44},
    };
    private static final int[][] ALIGN = {
            {}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}, {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50},
    };
    private static final int[] REMAINDER_BITS = {0, 7, 7, 7, 7, 7, 0, 0, 0, 0};

    private final int size;
    private final boolean[][] dark;
    private final boolean[][] fixed;

    private Qr(int version) {
        size = 17 + 4 * version;
        dark = new boolean[size][size];
        fixed = new boolean[size][size];
    }

    /** Returns modules[y][x], true = dark. */
    static boolean[][] encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (version <= 10 && capacity(version) < data.length) version++;
        if (version > 10) throw new IllegalArgumentException("text too long for QR version 10-M");
        byte[] codewords = interleave(version, dataCodewords(version, data));

        int best = 0;
        int bestPenalty = Integer.MAX_VALUE;
        Qr bestQr = null;
        for (int mask = 0; mask < 8; mask++) {
            Qr q = new Qr(version);
            q.drawFunctions(version);
            q.drawCodewords(codewords, REMAINDER_BITS[version - 1]);
            q.applyMask(mask);
            q.drawFormat(mask);
            int p = q.penalty();
            if (p < bestPenalty) {
                bestPenalty = p;
                best = mask;
                bestQr = q;
            }
        }
        return bestQr.dark;
    }

    private static int dataLength(int version) {
        int[] b = BLOCKS[version - 1];
        return b[1] * b[2] + b[3] * b[4];
    }

    /** Bytes that fit: data codewords minus the 4-bit mode and 8/16-bit count. */
    private static int capacity(int version) {
        int countBits = version < 10 ? 8 : 16;
        return (dataLength(version) * 8 - 4 - countBits) / 8;
    }

    private static byte[] dataCodewords(int version, byte[] data) {
        int total = dataLength(version);
        BitBuffer bb = new BitBuffer();
        bb.append(0b0100, 4);
        bb.append(data.length, version < 10 ? 8 : 16);
        for (byte b : data) bb.append(b & 0xFF, 8);
        bb.append(0, Math.min(4, total * 8 - bb.size()));
        bb.append(0, (8 - bb.size() % 8) % 8);
        for (int pad = 0xEC; bb.size() < total * 8; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);
        return bb.toBytes();
    }

    private static byte[] interleave(int version, byte[] data) {
        int[] b = BLOCKS[version - 1];
        int ec = b[0];
        List<byte[]> blocks = new ArrayList<>();
        List<byte[]> eccs = new ArrayList<>();
        byte[] divisor = rsDivisor(ec);
        int k = 0;
        for (int g = 0; g < 2; g++) {
            int count = b[1 + 2 * g];
            int len = b[2 + 2 * g];
            for (int i = 0; i < count; i++) {
                byte[] block = new byte[len];
                System.arraycopy(data, k, block, 0, len);
                k += len;
                blocks.add(block);
                eccs.add(rsRemainder(block, divisor));
            }
        }
        int maxLen = Math.max(b[2], b[4]);
        byte[] out = new byte[data.length + ec * blocks.size()];
        int o = 0;
        for (int i = 0; i < maxLen; i++) {
            for (byte[] block : blocks) if (i < block.length) out[o++] = block[i];
        }
        for (int i = 0; i < ec; i++) {
            for (byte[] e : eccs) out[o++] = e[i];
        }
        return out;
    }

    // ---- Reed–Solomon over GF(256), polynomial 0x11D ----

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMul(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) gfMul(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    // ---- matrix ----

    private void set(int x, int y, boolean d) {
        dark[y][x] = d;
        fixed[y][x] = true;
    }

    private void drawFunctions(int version) {
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        int[] pos = ALIGN[version - 1];
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormat(0);
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            long bits = (long) version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3;
                int b = i / 3;
                set(a, b, bit);
                set(b, a, bit);
            }
        }
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                set(x, y, dist != 2 && dist != 4);
            }
        }
    }

    /** Level M (format bits 00) and the mask, BCH-protected, in both copies. */
    private void drawFormat(int mask) {
        int data = mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private void drawCodewords(byte[] data, int remainderBits) {
        int total = data.length * 8 + remainderBits;
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (fixed[y][x] || i >= total) continue;
                    if (i < data.length * 8) dark[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                    i++;
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (fixed[y][x]) continue;
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (invert) dark[y][x] = !dark[y][x];
            }
        }
    }

    /** Rules 1, 2 and 4 of the standard's mask penalty (rule 3 matters little for screen display). */
    private int penalty() {
        int p = 0;
        for (int y = 0; y < size; y++) {
            int run = 1;
            for (int x = 1; x < size; x++) {
                if (dark[y][x] == dark[y][x - 1]) {
                    run++;
                    if (run == 5) p += 3; else if (run > 5) p++;
                } else {
                    run = 1;
                }
            }
        }
        for (int x = 0; x < size; x++) {
            int run = 1;
            for (int y = 1; y < size; y++) {
                if (dark[y][x] == dark[y - 1][x]) {
                    run++;
                    if (run == 5) p += 3; else if (run > 5) p++;
                } else {
                    run = 1;
                }
            }
        }
        int darkCount = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (dark[y][x]) darkCount++;
                if (x < size - 1 && y < size - 1) {
                    boolean c = dark[y][x];
                    if (c == dark[y][x + 1] && c == dark[y + 1][x] && c == dark[y + 1][x + 1]) p += 3;
                }
            }
        }
        int total = size * size;
        int k = (Math.abs(darkCount * 20 - total * 10) + total - 1) / total - 1;
        return p + Math.max(0, k) * 10;
    }

    private static final class BitBuffer {
        private final List<Boolean> bits = new ArrayList<>();

        void append(int value, int len) {
            for (int i = len - 1; i >= 0; i--) bits.add(((value >>> i) & 1) != 0);
        }

        int size() {
            return bits.size();
        }

        byte[] toBytes() {
            byte[] out = new byte[bits.size() / 8];
            for (int i = 0; i < bits.size(); i++) if (bits.get(i)) out[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
            return out;
        }
    }

    /** The code for {@code text} as a black-on-white bitmap with the standard 4-module quiet zone. */
    static Bitmap bitmap(String text) {
        boolean[][] m = encode(text);
        int quiet = 4;
        int n = m.length + quiet * 2;
        int scale = Math.max(1, 640 / n);
        Bitmap b = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m.length; x++) {
                if (!m[y][x]) continue;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        b.setPixel((x + quiet) * scale + dx, (y + quiet) * scale + dy, Color.BLACK);
                    }
                }
            }
        }
        return b;
    }
}
