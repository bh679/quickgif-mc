package games.brennan.quickgif;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameTimingTest {

    private static final long MS = 1_000_000L;

    @Test
    void steadyFramesGetTheirGapAndNeverDrift() {
        FrameTiming t = new FrameTiming(12, 20, 9.5);
        long total = 0;
        for (int i = 0; i < 120; i++) total += t.delayFor(i * 83 * MS); // ~12 fps for ~10 s
        // Every delay is real time since the last frame, plus the first frame's nominal 8 cs.
        assertEquals(Math.round(119 * 83 / 10.0) + 8, total);
    }

    @Test
    void aDroppedFrameStretchesTheNextDelay() {
        FrameTiming t = new FrameTiming(12, 20, 9.5);
        assertEquals(8, t.delayFor(0));
        assertEquals(8, t.delayFor(83 * MS));
        assertEquals(17, t.delayFor(250 * MS));
    }

    @Test
    void delaysNeverDropBelowTwoCentiseconds() {
        FrameTiming t = new FrameTiming(30, 20, 9.5);
        t.delayFor(0);
        assertEquals(FrameTiming.MIN_DELAY_CS, t.delayFor(1 * MS));
    }

    @Test
    void stopsAtTheTimeCap() {
        FrameTiming t = new FrameTiming(12, 2, 9.5);
        t.delayFor(0);
        assertEquals(FrameTiming.Stop.NONE, t.check(1999 * MS, 0, 1));
        assertEquals(FrameTiming.Stop.TIME, t.check(2000 * MS, 0, 1));
    }

    @Test
    void stopsBeforeOneMoreAverageFramePassesTheSizeCap() {
        FrameTiming t = new FrameTiming(12, 20, 1.0); // 1 MiB
        long mib = 1024 * 1024;
        assertEquals(FrameTiming.Stop.NONE, t.check(0, mib / 2, 2));          // next ~256 KiB fits
        assertEquals(FrameTiming.Stop.SIZE, t.check(0, mib * 9 / 10, 3));     // next ~300 KiB does not
        assertEquals(FrameTiming.Stop.NONE, t.check(0, 0, 0));
    }

    @Test
    void glRowsAreFlippedAndAlphaDropped() {
        // 2x2 RGBA, bottom row first as GL reads it.
        ByteBuffer gl = ByteBuffer.wrap(new byte[] {
            1, 2, 3, 9,   4, 5, 6, 9,      // bottom row
            7, 8, 9, 9,   10, 11, 12, 9,   // top row
        });
        int[] out = new int[4];
        GifEncoderWorker.toTopDownRgb(gl, 2, 2, out);
        assertArrayEquals(new int[] {0x070809, 0x0A0B0C, 0x010203, 0x040506}, out);
    }
}
