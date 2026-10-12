package games.brennan.quickgif;

/**
 * Frame delays and the stop caps for one recording. Pure logic, no Minecraft.
 *
 * <p>A frame's delay is the wall time since the frame before it, kept against the whole recording's
 * elapsed time rather than gap by gap, so 10 ms rounding never drifts and the GIF plays back at
 * real speed even when frames were dropped. Delays never go under 2 cs: browsers show anything
 * shorter at 10 cs.</p>
 */
public final class FrameTiming {

    static final int MIN_DELAY_CS = 2;

    public enum Stop { NONE, TIME, SIZE }

    private final int nominalCs;
    private final long maxNs;
    private final long maxBytes;
    private long firstNs = -1;
    private long emittedCs;

    public FrameTiming(int fps, double maxSeconds, double maxMegabytes) {
        this.nominalCs = Math.max(MIN_DELAY_CS, Math.round(100f / Math.max(1, fps)));
        this.maxNs = (long) (maxSeconds * 1e9);
        this.maxBytes = (long) (maxMegabytes * 1024 * 1024);
    }

    /** The delay, in hundredths of a second, of the frame taken at {@code nowNs}. */
    public int delayFor(long nowNs) {
        if (firstNs < 0) firstNs = nowNs;
        long target = Math.round((nowNs - firstNs) / 1e7) + nominalCs;
        int delay = (int) Math.max(MIN_DELAY_CS, target - emittedCs);
        emittedCs += delay;
        return delay;
    }

    /**
     * Whether to stop before taking another frame: the time cap is reached, or one more frame of the
     * average size so far would pass the size cap.
     */
    public Stop check(long nowNs, long bytes, int frames) {
        if (firstNs >= 0 && nowNs - firstNs >= maxNs) return Stop.TIME;
        if (frames > 0 && bytes + bytes / frames > maxBytes) return Stop.SIZE;
        return Stop.NONE;
    }
}
