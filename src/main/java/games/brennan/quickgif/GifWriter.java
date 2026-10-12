package games.brennan.quickgif;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Streams an animated GIF89a: header and loop-forever extension up front, then one frame at a time,
 * each with its own colour table and delay, then the trailer. Counts the bytes it writes so the
 * caller can stop before a size cap.
 */
public final class GifWriter {

    private final OutputStream out;
    private final int width, height;
    private final LzwEncoder lzw = new LzwEncoder();
    private long bytes;
    private int frames;
    private long durationCs;

    public GifWriter(OutputStream out, int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > 0xFFFF || height > 0xFFFF) {
            throw new IllegalArgumentException("bad size " + width + "x" + height);
        }
        this.out = out;
        this.width = width;
        this.height = height;
        writeAscii("GIF89a");
        // Logical screen descriptor: no global colour table (every frame brings its own).
        short16(width);
        short16(height);
        write(0x00, 0x00, 0x00);
        // NETSCAPE2.0 application extension: loop count 0 = forever.
        write(0x21, 0xFF, 0x0B);
        writeAscii("NETSCAPE2.0");
        write(0x03, 0x01, 0x00, 0x00, 0x00);
    }

    public long bytesWritten() { return bytes; }

    public int frames() { return frames; }

    /** Total play time of the frames written so far, in hundredths of a second. */
    public long durationCs() { return durationCs; }

    /** Appends one full-size frame shown for {@code delayCs} hundredths of a second. */
    public void addFrame(Quantizer.Result frame, int delayCs) throws IOException {
        if (frame.indices().length != width * height) {
            throw new IllegalArgumentException("frame has " + frame.indices().length + " pixels, want " + width * height);
        }
        int colors = frame.palette().length;
        int delay = Math.max(0, Math.min(0xFFFF, delayCs));
        int sizeField = tableSizeField(colors);
        // Graphic control extension: no disposal, no transparency, the delay.
        write(0x21, 0xF9, 0x04, 0x00);
        short16(delay);
        write(0x00, 0x00);
        // Image descriptor at (0,0), full size, with a local colour table.
        write(0x2C);
        short16(0);
        short16(0);
        short16(width);
        short16(height);
        write(0x80 | sizeField);
        int[] palette = frame.palette();
        int entries = 1 << (sizeField + 1);
        for (int i = 0; i < entries; i++) {
            int c = i < palette.length ? palette[i] : 0;
            write((c >>> 16) & 0xFF, (c >>> 8) & 0xFF, c & 0xFF);
        }
        int minCode = LzwEncoder.minCodeSize(colors);
        write(minCode);
        byte[] data = lzw.encode(frame.indices(), minCode);
        for (int off = 0; off < data.length; off += 255) {
            int len = Math.min(255, data.length - off);
            write(len);
            out.write(data, off, len);
            bytes += len;
        }
        write(0x00); // block terminator
        frames++;
        durationCs += delay;
    }

    /** Writes the trailer. Does not close the stream. */
    public void finish() throws IOException {
        write(0x3B);
        out.flush();
    }

    /** GIF's colour-table size field: the table holds {@code 2^(field+1)} entries. */
    static int tableSizeField(int colors) {
        int field = 0;
        while ((1 << (field + 1)) < colors) field++;
        return field;
    }

    private void short16(int v) throws IOException {
        write(v & 0xFF, (v >>> 8) & 0xFF);
    }

    private void writeAscii(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) write(s.charAt(i));
    }

    private void write(int... values) throws IOException {
        for (int v : values) out.write(v);
        bytes += values.length;
    }
}
