package games.brennan.quickgif;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips GIFs through the JDK's own decoder: it is strict about LZW widths and block layout. */
class GifWriterTest {

    private static final int W = 320, H = 180;

    @Test
    void framesDecodeWithTheirSizeDelayAndColours() throws IOException {
        int[] flat = new int[W * H];
        java.util.Arrays.fill(flat, 0x3366CC);
        int[] gradient = gradient();
        int[] noise = noise(7);

        Quantizer q = new Quantizer();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GifWriter writer = new GifWriter(bytes, W, H);
        writer.addFrame(q.quantize(flat, 256), 8);
        writer.addFrame(q.quantize(gradient, 256), 9);
        writer.addFrame(q.quantize(noise, 256), 10);
        writer.finish();

        assertEquals(bytes.size(), writer.bytesWritten());
        assertEquals(27, writer.durationCs());

        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            reader.setInput(in);
            assertEquals(3, reader.getNumImages(true));
            BufferedImage first = reader.read(0);
            assertEquals(W, first.getWidth());
            assertEquals(H, first.getHeight());
            assertEquals(0x3366CC, first.getRGB(17, 23) & 0xFFFFFF, "a one-colour frame comes back exact");
            assertEquals(9, delayOf(reader, 1));
            assertClose(gradient, reader.read(1), 12);
            assertClose(noise, reader.read(2), 40);
        } finally {
            reader.dispose();
        }
    }

    @Test
    void loopsForever() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GifWriter writer = new GifWriter(bytes, 4, 4);
        writer.addFrame(new Quantizer().quantize(new int[16], 256), 5);
        writer.finish();
        String header = new String(bytes.toByteArray(), 0, 40, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(header.startsWith("GIF89a"));
        assertTrue(header.contains("NETSCAPE2.0"));
    }

    @Test
    void tableSizeFieldCoversThePalette() {
        assertEquals(0, GifWriter.tableSizeField(1));
        assertEquals(0, GifWriter.tableSizeField(2));
        assertEquals(1, GifWriter.tableSizeField(3));
        assertEquals(7, GifWriter.tableSizeField(129));
        assertEquals(7, GifWriter.tableSizeField(256));
    }

    /** Prints the per-frame encode cost at the default GIF size; it has to keep up with ~12 fps. */
    @Test
    void encodesADefaultSizeFrameQuickly() throws IOException {
        int w = 640, h = 360;
        int[] frame = new int[w * h];
        Random r = new Random(1);
        for (int i = 0; i < frame.length; i++) {
            int x = i % w, y = i / w;
            frame[i] = ((x * 255 / w) << 16) | ((y * 255 / h) << 8) | (r.nextInt(48) + 100);
        }
        Quantizer q = new Quantizer();
        GifWriter writer = new GifWriter(new ByteArrayOutputStream(), w, h);
        for (int i = 0; i < 5; i++) writer.addFrame(q.quantize(frame, 256), 8); // warm up
        long start = System.nanoTime();
        int n = 10;
        for (int i = 0; i < n; i++) writer.addFrame(q.quantize(frame, 256), 8);
        long msPerFrame = (System.nanoTime() - start) / n / 1_000_000;
        System.out.println("Quick GIF: " + msPerFrame + " ms per 640x360 frame, " + writer.bytesWritten() / writer.frames() + " bytes/frame");
        assertTrue(msPerFrame < 250, "encoder too slow: " + msPerFrame + " ms/frame");
    }

    private static int delayOf(ImageReader reader, int index) throws IOException {
        IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(index).getAsTree("javax_imageio_gif_image_1.0");
        IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
        return Integer.parseInt(gce.getAttribute("delayTime"));
    }

    private static void assertClose(int[] want, BufferedImage got, int tolerance) {
        long total = 0;
        int worst = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int a = want[y * W + x], b = got.getRGB(x, y);
            int d = Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
            total += d;
            worst = Math.max(worst, d);
        }
        double mean = (double) total / (W * H);
        assertTrue(mean <= tolerance, "mean channel error " + mean + " (worst " + worst + ")");
    }

    private static int[] gradient() {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            px[y * W + x] = ((x * 255 / W) << 16) | ((y * 255 / H) << 8) | (((x + y) * 255 / (W + H)));
        }
        return px;
    }

    /** Random colours: forces many LZW table resets. */
    private static int[] noise(long seed) {
        Random r = new Random(seed);
        int[] px = new int[W * H];
        for (int i = 0; i < px.length; i++) px[i] = r.nextInt(0x1000000);
        return px;
    }
}
