package games.brennan.quickgif;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * F7 (rebindable under Controls → Quick GIF) starts a recording, and pressing it again saves it.
 * F7 is free in vanilla and stays clear of F2 (screenshot) and the F3 combinations.
 */
@EventBusSubscriber(modid = QuickGif.MOD_ID, value = Dist.CLIENT)
public final class GifHotkey {

    static final KeyMapping KEY = new KeyMapping(
        "key.quickgif.record",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F7,
        "key.categories.quickgif"
    );

    private GifHotkey() {}

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    /** Ticks in their own, uniquely named class (NeoForge dedupes inner subscribers that share a simple name). */
    @EventBusSubscriber(modid = QuickGif.MOD_ID, value = Dist.CLIENT)
    public static final class GifHotkeyTicks {

        private GifHotkeyTicks() {}

        /** Drain every queued press so none is banked; any press this tick toggles once. */
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            int presses = 0;
            while (KEY.consumeClick()) presses++;
            if (presses == 0 || Minecraft.getInstance().level == null) return;
            GifRecorder.toggle();
        }
    }
}
