package games.brennan.quickgif;

import java.util.Arrays;

/**
 * Reduces one frame to at most 256 colours: median cut over a 5-bit-per-channel histogram.
 *
 * <p>Every pixel falls into one of 32,768 histogram cells. Boxes of cells are split, the most
 * populous box with the longest side first, at the median of its longest side, until there are
 * {@code maxColors} boxes or none can be split. Each box's colour is the average of the pixels in it,
 * and every cell maps to the colour of the box that holds it, so mapping a pixel is one table lookup.
 * Cost is linear in pixels plus a fixed ~32k-cell overhead — fast enough to keep up with live capture,
 * which an object-per-pixel quantiser was not.</p>
 *
 * <p>Not thread-safe: one instance per encoder thread; its tables are reused frame to frame.</p>
 */
public final class Quantizer {

    static final int BITS = 5;
    static final int SIDE = 1 << BITS;
    static final int CELLS = SIDE * SIDE * SIDE;

    private final int[] count = new int[CELLS];
    private final long[] sumR = new long[CELLS];
    private final long[] sumG = new long[CELLS];
    private final long[] sumB = new long[CELLS];
    private final short[] cellToIndex = new short[CELLS];

    /** The result of {@link #quantize}: a palette of 0xRRGGBB colours and one index per pixel. */
    public record Result(int[] palette, byte[] indices) {}

    public Result quantize(int[] rgb, int maxColors) {
        if (maxColors < 1 || maxColors > 256) throw new IllegalArgumentException("maxColors: " + maxColors);
        histogram(rgb);
        Box[] boxes = split(maxColors);
        int[] palette = new int[boxes.length];
        for (int i = 0; i < boxes.length; i++) palette[i] = boxes[i].fill(i);
        byte[] indices = new byte[rgb.length];
        for (int i = 0; i < rgb.length; i++) indices[i] = (byte) cellToIndex[cell(rgb[i])];
        return new Result(palette, indices);
    }

    static int cell(int rgb) {
        int r = (rgb >>> 19) & 0x1F, g = (rgb >>> 11) & 0x1F, b = (rgb >>> 3) & 0x1F;
        return (r << 10) | (g << 5) | b;
    }

    private void histogram(int[] rgb) {
        Arrays.fill(count, 0);
        Arrays.fill(sumR, 0);
        Arrays.fill(sumG, 0);
        Arrays.fill(sumB, 0);
        for (int p : rgb) {
            int c = cell(p);
            count[c]++;
            sumR[c] += (p >>> 16) & 0xFF;
            sumG[c] += (p >>> 8) & 0xFF;
            sumB[c] += p & 0xFF;
        }
    }

    private Box[] split(int maxColors) {
        Box[] boxes = new Box[maxColors];
        Box all = new Box(0, SIDE - 1, 0, SIDE - 1, 0, SIDE - 1);
        all.shrink();
        boxes[0] = all;
        int n = 1;
        while (n < maxColors) {
            int pick = -1;
            long best = 0;
            for (int i = 0; i < n; i++) {
                long score = boxes[i].splitScore();
                if (score > best) { best = score; pick = i; }
            }
            if (pick < 0) break; // every box is a single cell
            Box[] halves = boxes[pick].halve();
            boxes[pick] = halves[0];
            boxes[n++] = halves[1];
        }
        return Arrays.copyOf(boxes, n);
    }

    /** An inclusive range of histogram cells, kept shrunk to the cells that hold pixels. */
    private final class Box {
        int r0, r1, g0, g1, b0, b1;
        long pixels;

        Box(int r0, int r1, int g0, int g1, int b0, int b1) {
            this.r0 = r0; this.r1 = r1; this.g0 = g0; this.g1 = g1; this.b0 = b0; this.b1 = b1;
        }

        /** Population times longest side: big, spread-out boxes split first; a one-cell box never. */
        long splitScore() {
            int side = Math.max(r1 - r0, Math.max(g1 - g0, b1 - b0));
            return side == 0 ? 0 : pixels * side;
        }

        void shrink() {
            int nr0 = SIDE, nr1 = -1, ng0 = SIDE, ng1 = -1, nb0 = SIDE, nb1 = -1;
            long total = 0;
            for (int r = r0; r <= r1; r++) for (int g = g0; g <= g1; g++) for (int b = b0; b <= b1; b++) {
                int n = count[(r << 10) | (g << 5) | b];
                if (n == 0) continue;
                total += n;
                nr0 = Math.min(nr0, r); nr1 = Math.max(nr1, r);
                ng0 = Math.min(ng0, g); ng1 = Math.max(ng1, g);
                nb0 = Math.min(nb0, b); nb1 = Math.max(nb1, b);
            }
            pixels = total;
            if (total == 0) return; // only for an empty frame; keeps the box as it was
            r0 = nr0; r1 = nr1; g0 = ng0; g1 = ng1; b0 = nb0; b1 = nb1;
        }

        /** Splits at the median pixel along the longest side; both halves hold pixels. */
        Box[] halve() {
            int dr = r1 - r0, dg = g1 - g0, db = b1 - b0;
            int axis = dr >= dg && dr >= db ? 0 : dg >= db ? 1 : 2;
            int lo = axis == 0 ? r0 : axis == 1 ? g0 : b0;
            int hi = axis == 0 ? r1 : axis == 1 ? g1 : b1;
            long half = pixels / 2, seen = 0;
            int cut = lo;
            for (int v = lo; v < hi; v++) {
                seen += slice(axis, v);
                cut = v;
                if (seen >= half) break;
            }
            Box a = copy(), b = copy();
            a.setHigh(axis, cut);
            b.setLow(axis, cut + 1);
            a.shrink();
            b.shrink();
            return new Box[] {a, b};
        }

        private long slice(int axis, int v) {
            long n = 0;
            for (int r = axis == 0 ? v : r0; r <= (axis == 0 ? v : r1); r++)
                for (int g = axis == 1 ? v : g0; g <= (axis == 1 ? v : g1); g++)
                    for (int b = axis == 2 ? v : b0; b <= (axis == 2 ? v : b1); b++)
                        n += count[(r << 10) | (g << 5) | b];
            return n;
        }

        private Box copy() { return new Box(r0, r1, g0, g1, b0, b1); }

        private void setHigh(int axis, int v) { if (axis == 0) r1 = v; else if (axis == 1) g1 = v; else b1 = v; }

        private void setLow(int axis, int v) { if (axis == 0) r0 = v; else if (axis == 1) g0 = v; else b0 = v; }

        /** Points every cell in the box at palette entry {@code index}; returns the box's mean colour. */
        int fill(int index) {
            long n = 0, r = 0, g = 0, b = 0;
            for (int ri = r0; ri <= r1; ri++) for (int gi = g0; gi <= g1; gi++) for (int bi = b0; bi <= b1; bi++) {
                int c = (ri << 10) | (gi << 5) | bi;
                cellToIndex[c] = (short) index;
                n += count[c]; r += sumR[c]; g += sumG[c]; b += sumB[c];
            }
            if (n == 0) return 0;
            return (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
        }
    }
}
