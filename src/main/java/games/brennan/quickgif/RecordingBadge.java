package games.brennan.quickgif;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.function.Consumer;

/**
 * A blinking red dot and the elapsed time in the top-left corner while recording ("Saving…" after).
 *
 * <p>Drawn on {@link RenderFrameEvent.Post} at LOW, after {@link GifRecorder} grabbed the frame at
 * NORMAL, so it is on screen only and never in the GIF — which is also why it stays visible with F1.
 * The GUI pass setup is the one Dungeon Train's live-feed viewfinder uses.</p>
 */
@EventBusSubscriber(modid = QuickGif.MOD_ID, value = Dist.CLIENT)
public final class RecordingBadge {

    private static final int MARGIN = 6;
    private static final int DOT = 3;
    private static final int RED = 0xFFE62E2E;
    private static final int WHITE = 0xFFFFFFFF;
    private static final long BLINK_MS = 500;

    private RecordingBadge() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        GifRecorder.State state = GifRecorder.state();
        if (state == GifRecorder.State.IDLE || Minecraft.getInstance().level == null) return;
        guiPass(g -> draw(g, state));
    }

    private static void draw(GuiGraphics g, GifRecorder.State state) {
        Font font = Minecraft.getInstance().font;
        int cy = MARGIN + font.lineHeight / 2;
        Component label;
        if (state == GifRecorder.State.RECORDING) {
            if ((System.currentTimeMillis() / BLINK_MS) % 2 == 0) {
                g.fill(MARGIN, cy - DOT, MARGIN + DOT * 2, cy + DOT, RED);
            }
            long s = GifRecorder.elapsedMs() / 1000;
            label = Component.translatable("quickgif.badge.recording", String.format("%d:%02d", s / 60, s % 60));
        } else {
            label = Component.translatable("quickgif.badge.saving");
        }
        g.drawString(font, label, MARGIN + DOT * 2 + 4, MARGIN, WHITE, true);
    }

    /** Draws straight onto the main target after the frame is finished, with the HUD's projection. */
    private static void guiPass(Consumer<GuiGraphics> painter) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, w, h, 0f, 1000f, ClientHooks.getGuiFarPlane()),
                VertexSorting.ORTHOGRAPHIC_Z);
            modelView.translation(0f, 0f, 10000f - ClientHooks.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            painter.accept(g);
            g.flush();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }
}
