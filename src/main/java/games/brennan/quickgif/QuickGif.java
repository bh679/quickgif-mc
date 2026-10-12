package games.brennan.quickgif;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/** Quick GIF: press F7 to record a GIF of the screen, F7 again to save it. Client only. */
@Mod(value = QuickGif.MOD_ID, dist = Dist.CLIENT)
public final class QuickGif {

    public static final String MOD_ID = "quickgif";

    public QuickGif(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, QuickGifConfig.SPEC, "quickgif-client.toml");
    }
}
