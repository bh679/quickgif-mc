package games.brennan.quickgif;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Dev testing only, inert unless {@code -Dquickgif.autoRecord=<seconds>} is set (Gradle:
 * {@code -PautoRecord=<seconds>}): once in a world, waits that long for the world to settle, records
 * for that long, then stops — the F7 round trip, for a dev client nobody can type into.
 */
@EventBusSubscriber(modid = QuickGif.MOD_ID, value = Dist.CLIENT)
public final class DevAutoRecord {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SECONDS = parse(System.getProperty("quickgif.autoRecord"));
    private static long inWorldSinceMs = -1;
    private static int step; // 0 waiting, 1 recording, 2 done

    private DevAutoRecord() {}

    private static long parse(String s) {
        if (s == null) return 0;
        try {
            return Math.max(0, Long.parseLong(s.trim()));
        } catch (NumberFormatException e) {
            LOGGER.warn("Quick GIF: ignoring quickgif.autoRecord={}", s);
            return 0;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (SECONDS == 0 || step == 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (inWorldSinceMs < 0) inWorldSinceMs = now;
        long due = inWorldSinceMs + SECONDS * 1000 * (step + 1);
        if (now < due) return;
        if (step == 0 && GifRecorder.state() == GifRecorder.State.IDLE) {
            LOGGER.info("Quick GIF: auto-record start");
            GifRecorder.toggle();
            step = 1;
        } else if (step == 1) {
            if (GifRecorder.state() == GifRecorder.State.RECORDING) {
                LOGGER.info("Quick GIF: auto-record stop");
                GifRecorder.toggle();
            }
            step = 2;
        }
    }
}
