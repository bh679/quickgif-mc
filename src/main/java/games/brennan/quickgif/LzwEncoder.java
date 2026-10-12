package games.brennan.quickgif;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * GIF's variable-width LZW, LSB-first, as in the GIF89a spec (Appendix F).
 *
 * <p>Code-width growth follows the classic encoder (Welch/Thomas/Weiner): a code is written at the
 * current width, and only then does the width grow if the next free code no longer fits — which is
 * the one-entry lag every GIF decoder expects. When the table reaches 4096 codes a clear code is
 * written and the table starts again. The string table is an open-addressed hash of
 * {@code (suffix << 12) | prefix} keys, so a reset is one small {@code fill}.</p>
 *
 * <p>Not thread-safe; reusable for one frame after another.</p>
 */
public final class LzwEncoder {

    private static final int MAX_BITS = 12;
    private static final int MAX_CODES = 1 << MAX_BITS;
    private static final int HASH_SIZE = 8192;          // power of two, > MAX_CODES
    private static final int HASH_MASK = HASH_SIZE - 1;

    private final int[] keys = new int[HASH_SIZE];
    private final short[] codes = new short[HASH_SIZE];

    private ByteArrayOutputStream out;
    private int bitBuffer;
    private int bitCount;
    private int width;
    private int initWidth;
    private int nextCode;
    private int clearCode;
    private boolean clearPending;

    /** Smallest LZW minimum code size for a palette of {@code colors} entries (GIF requires ≥ 2). */
    public static int minCodeSize(int colors) {
        int bits = 2;
        while ((1 << bits) < colors) bits++;
        return bits;
    }

    /** Compresses {@code indices} (each below {@code 1 << minCodeSize}) into raw LZW bytes. */
    public byte[] encode(byte[] indices, int minCodeSize) {
        out = new ByteArrayOutputStream(indices.length / 2 + 16);
        bitBuffer = 0;
        bitCount = 0;
        clearCode = 1 << minCodeSize;
        initWidth = minCodeSize + 1;
        width = initWidth;
        resetTable();
        clearPending = false;
        write(clearCode);

        if (indices.length > 0) {
            int prefix = indices[0] & 0xFF;
            for (int i = 1; i < indices.length; i++) {
                int k = indices[i] & 0xFF;
                int key = (k << MAX_BITS) | prefix;
                int slot = find(key);
                if (keys[slot] == key) {
                    prefix = codes[slot];
                    continue;
                }
                write(prefix);
                if (nextCode < MAX_CODES) {
                    keys[slot] = key;
                    codes[slot] = (short) nextCode++;
                } else {
                    resetTable();
                    clearPending = true;
                    write(clearCode);
                }
                prefix = k;
            }
            write(prefix);
        }
        write(clearCode + 1); // end of information
        if (bitCount > 0) out.write(bitBuffer & 0xFF);
        return out.toByteArray();
    }

    private void resetTable() {
        Arrays.fill(keys, -1);
        nextCode = clearCode + 2;
    }

    /** The slot holding {@code key}, or the empty slot where it would go. */
    private int find(int key) {
        int slot = (key * 0x9E3779B1) >>> 19 & HASH_MASK;
        while (keys[slot] != -1 && keys[slot] != key) slot = (slot + 1) & HASH_MASK;
        return slot;
    }

    private void write(int code) {
        bitBuffer |= code << bitCount;
        bitCount += width;
        while (bitCount >= 8) {
            out.write(bitBuffer & 0xFF);
            bitBuffer >>>= 8;
            bitCount -= 8;
        }
        if (clearPending) {
            width = initWidth;
            clearPending = false;
        } else if (nextCode > (1 << width) - 1 && width < MAX_BITS) {
            width++;
        }
    }
}
