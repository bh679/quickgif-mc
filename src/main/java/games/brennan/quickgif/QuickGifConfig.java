package games.brennan.quickgif;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/quickgif-client.toml}. Read when a recording starts, so edits apply to the next one. */
public final class QuickGifConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue WIDTH;
    public static final ModConfigSpec.IntValue FPS;
    public static final ModConfigSpec.DoubleValue MAX_SECONDS;
    public static final ModConfigSpec.DoubleValue MAX_MEGABYTES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        WIDTH = b.comment("GIF width in pixels; the height follows the window's shape. Never wider than the window.")
            .defineInRange("width", 640, 64, 1920);
        FPS = b.comment("Frames per second. More is smoother but makes a bigger file.")
            .defineInRange("fps", 12, 1, 30);
        MAX_SECONDS = b.comment("A recording stops on its own after this many seconds.")
            .defineInRange("maxSeconds", 20.0, 1.0, 300.0);
        MAX_MEGABYTES = b.comment("A recording stops on its own before the file passes this size. 9.5 fits Discord's 10 MB limit.")
            .defineInRange("maxMegabytes", 9.5, 0.5, 500.0);
        SPEC = b.build();
    }

    private QuickGifConfig() {}
}
