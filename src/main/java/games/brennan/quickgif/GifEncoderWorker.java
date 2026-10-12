package games.brennan.quickgif;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

/**
 * The encoder thread for one recording. Takes the newest frame the {@link FrameGrabber} has read
 * back, turns it the right way up, quantises it and appends it to a {@code .gif.part} file, until
 * asked to stop or a cap is reached; then writes the trailer and renames the file into place.
 *
 * <p>If it falls behind, the grabber simply overwrites frames it has not taken — the GIF gets
 * choppier, never slower, because each frame's delay is measured when it is taken.</p>
 */
final class GifEncoderWorker implements Runnable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long IDLE_SLEEP_MS = 3;

    /** {@code file} is null when the recording ended before any frame arrived. */
    record Result(@Nullable Path file, int frames, long bytes, long durationCs, FrameTiming.Stop stop) {}

    private final FrameGrabber grabber;
    private final FrameTiming timing;
    private final Path part;
    private final Path target;
    private final CompletableFuture<Result> done = new CompletableFuture<>();
    private volatile boolean stopRequested;

    GifEncoderWorker(FrameGrabber grabber, FrameTiming timing, Path target) {
        this.grabber = grabber;
        this.timing = timing;
        this.target = target;
        this.part = target.resolveSibling(target.getFileName() + ".part");
    }

    void start() {
        Thread t = new Thread(this, "Quick GIF encoder");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    /** Finish with the frames taken so far. */
    void requestStop() { stopRequested = true; }

    CompletableFuture<Result> done() { return done; }

    @Override
    public void run() {
        try {
            done.complete(encode());
        } catch (Throwable t) {
            LOGGER.error("Quick GIF: recording to {} failed", target, t);
            deleteQuietly(part);
            done.completeExceptionally(t);
        }
    }

    private Result encode() throws IOException, InterruptedException {
        int w = grabber.width(), h = grabber.height();
        int[] rgb = new int[w * h];
        Quantizer quantizer = new Quantizer();
        FrameTiming.Stop stop = FrameTiming.Stop.NONE;
        GifWriter writer;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(part), 1 << 16)) {
            writer = new GifWriter(out, w, h);
            while (true) {
                ByteBuffer frame = grabber.latest();
                if (frame == null) {
                    if (stopRequested) break;
                    Thread.sleep(IDLE_SLEEP_MS);
                    continue;
                }
                long now = System.nanoTime();
                stop = timing.check(now, writer.bytesWritten(), writer.frames());
                if (stop != FrameTiming.Stop.NONE) {
                    grabber.recycle(frame);
                    break;
                }
                try {
                    toTopDownRgb(frame, w, h, rgb);
                } finally {
                    grabber.recycle(frame);
                }
                writer.addFrame(quantizer.quantize(rgb, 256), timing.delayFor(now));
                if (stopRequested) break;
            }
            writer.finish();
        }
        if (writer.frames() == 0) {
            deleteQuietly(part);
            return new Result(null, 0, 0, 0, stop);
        }
        moveIntoPlace();
        return new Result(target, writer.frames(), writer.bytesWritten(), writer.durationCs(), stop);
    }

    /** GL rows run bottom-up in RGBA; GIF rows run top-down. */
    static void toTopDownRgb(ByteBuffer rgba, int w, int h, int[] out) {
        for (int y = 0; y < h; y++) {
            int src = (h - 1 - y) * w * 4;
            int dst = y * w;
            for (int x = 0; x < w; x++, src += 4) {
                out[dst + x] = (rgba.get(src) & 0xFF) << 16 | (rgba.get(src + 1) & 0xFF) << 8 | (rgba.get(src + 2) & 0xFF);
            }
        }
    }

    private void moveIntoPlace() throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            LOGGER.warn("Quick GIF: could not delete {}", p, e);
        }
    }
}
