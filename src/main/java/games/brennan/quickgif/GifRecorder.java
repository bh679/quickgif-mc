package games.brennan.quickgif;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * One recording at a time: IDLE → RECORDING → SAVING → IDLE. Client (render) thread only, apart from
 * the {@link GifEncoderWorker} it starts.
 *
 * <p>Frames are grabbed on {@link RenderFrameEvent.Post} at NORMAL priority — the finished frame,
 * after Iris/shader compositing and the HUD — and {@link RecordingBadge} draws at LOW, after the grab,
 * so the badge is on screen but never in the GIF.</p>
 */
@EventBusSubscriber(modid = QuickGif.MOD_ID, value = Dist.CLIENT)
public final class GifRecorder {

    private static final Logger LOGGER = LogUtils.getLogger();

    enum State { IDLE, RECORDING, SAVING }

    private static State state = State.IDLE;
    private static FrameGrabber grabber;
    private static GifEncoderWorker worker;
    private static long periodNs;
    private static long lastGrabNs;
    private static long startedMs;

    private GifRecorder() {}

    static State state() { return state; }

    static long elapsedMs() { return state == State.RECORDING ? System.currentTimeMillis() - startedMs : 0; }

    /** The hotkey: start, stop, or say the last one is still saving. */
    static void toggle() {
        switch (state) {
            case IDLE -> start();
            case RECORDING -> stop();
            case SAVING -> notice(Component.translatable("quickgif.still_saving"), true);
        }
    }

    private static void start() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int[] size = gifSize(QuickGifConfig.WIDTH.get(), main.width, main.height);
        int fps = QuickGifConfig.FPS.get();
        Path file;
        try {
            file = nextFile(mc.gameDirectory.toPath().resolve("gifs"));
        } catch (IOException e) {
            LOGGER.error("Quick GIF: cannot create the gifs folder", e);
            notice(Component.translatable("quickgif.failed", e.getMessage()).withStyle(ChatFormatting.RED), false);
            return;
        }
        grabber = new FrameGrabber(size[0], size[1]);
        worker = new GifEncoderWorker(grabber,
            new FrameTiming(fps, QuickGifConfig.MAX_SECONDS.get(), QuickGifConfig.MAX_MEGABYTES.get()), file);
        worker.start();
        periodNs = 1_000_000_000L / fps;
        lastGrabNs = 0;
        startedMs = System.currentTimeMillis();
        state = State.RECORDING;
        LOGGER.info("Quick GIF: recording {}x{} at {} fps to {}", size[0], size[1], fps, file);
        notice(Component.translatable("quickgif.recording", GifHotkey.KEY.getTranslatedKeyMessage()), true);
    }

    private static void stop() {
        worker.requestStop();
        state = State.SAVING;
        notice(Component.translatable("quickgif.saving"), true);
    }

    /** Even width and height, no wider than the window or the config, keeping the window's shape. */
    static int[] gifSize(int wantWidth, int windowWidth, int windowHeight) {
        int w = Math.max(2, Math.min(wantWidth, windowWidth)) & ~1;
        int h = Math.max(2, (int) Math.round((double) w * windowHeight / Math.max(1, windowWidth))) & ~1;
        return new int[] {w, h};
    }

    /** {@code gifs/<date>.gif}, or {@code <date>_2.gif} … when two land in the same second. */
    private static Path nextFile(Path dir) throws IOException {
        Files.createDirectories(dir);
        String base = Util.getFilenameFormattedDateTime();
        Path file = dir.resolve(base + ".gif");
        for (int i = 2; Files.exists(file); i++) file = dir.resolve(base + "_" + i + ".gif");
        return file;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        if (state != State.RECORDING) return;
        long now = System.nanoTime();
        if (now - lastGrabNs < periodNs * 9 / 10) return;
        lastGrabNs = now;
        grabber.grab(Minecraft.getInstance().getMainRenderTarget());
    }

    /** The worker ends on a stop request or on its own at a cap; either way, report and clean up here. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (worker == null || !worker.done().isDone()) return;
        GifEncoderWorker finished = worker;
        worker = null;
        grabber.close();
        grabber = null;
        state = State.IDLE;
        report(finished);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (state == State.RECORDING) stop();
    }

    private static void report(GifEncoderWorker finished) {
        GifEncoderWorker.Result result;
        try {
            result = finished.done().get();
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            notice(Component.translatable("quickgif.failed", String.valueOf(cause.getMessage()))
                .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (result.file() == null) {
            notice(Component.translatable("quickgif.empty"), false);
            return;
        }
        LOGGER.info("Quick GIF: saved {} ({} frames, {} bytes, {} cs, stop={})",
            result.file(), result.frames(), result.bytes(), result.durationCs(), result.stop());
        String path = result.file().toAbsolutePath().toString();
        MutableComponent name = Component.literal(result.file().getFileName().toString())
            .withStyle(ChatFormatting.UNDERLINE)
            .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
        MutableComponent msg = Component.translatable("quickgif.saved", name,
            String.format(Locale.ROOT, "%.1f", result.bytes() / (1024.0 * 1024.0)),
            String.format(Locale.ROOT, "%.1f", result.durationCs() / 100.0));
        switch (result.stop()) {
            case SIZE -> msg.append(" ").append(Component.translatable("quickgif.stopped_size"));
            case TIME -> msg.append(" ").append(Component.translatable("quickgif.stopped_time"));
            case NONE -> { }
        }
        notice(msg, false);
    }

    private static void notice(Component message, boolean actionBar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, actionBar);
        } else if (!actionBar) {
            mc.gui.getChat().addMessage(message);
        }
    }
}
