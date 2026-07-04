package com.mores.uncanny;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Loads the eye-graphics PNG resources bundled under {@code src/main/resources/defaultEye/} and converts them to the
 * int[][] arrays expected by {@link IrisRenderer} and {@link EyelidRenderer}.
 * <p>
 * Texture formats:
 * <ul>
 * <li><b>sclera</b> – ARGB-32 (standard Java {@code BufferedImage} pixel)
 * <li><b>iris</b> – ARGB-32; the source PNG is a polar strip (width = angle steps, height = radius steps)
 * <li><b>lid maps</b> – each value is 0-255; a pixel is covered when {@code map[y][x] <= threshold}. We store
 * {@code 255 - alpha} so that threshold=0 means fully open and threshold=254 means fully closed, matching the semantics
 * used by {@link EyelidRenderer}.
 * </ul>
 */
public final class EyeTextures {

    private EyeTextures() {
    }

    // ── Sclera ────────────────────────────────────────────────────────────────

    /**
     * Load the sclera PNG and return it as an ARGB-32 int[][] array [height][width].
     */
    public static int[][] loadSclera() {
        return loadArgb("defaultEye/sclera.png", EyeConfig.SCLERA_WIDTH, EyeConfig.SCLERA_HEIGHT);
    }

    // ── Iris ──────────────────────────────────────────────────────────────────

    /**
     * Load the iris polar-strip PNG and return it as an ARGB-32 int[][] array [height][width]. Row 0 is the outer
     * (limbal) edge of the iris and the last row is the inner edge (adjacent to the pupil), matching the convention
     * expected by {@link IrisRenderer}.
     */
    public static int[][] loadIris() {
        return loadArgb("defaultEye/iris.png", EyeConfig.IRIS_TEX_W, EyeConfig.IRIS_TEX_H);
    }

    // ── Eyelid threshold maps ─────────────────────────────────────────────────

    /**
     * Load the upper-lid PNG and produce a threshold map [height][width]. Each value is {@code 255 - alpha}, so:
     * <ul>
     * <li>Pixels that are always covered by the lid → alpha≈255 → value≈0
     * <li>Pixels that are always open eye → alpha=0 → value=255
     * </ul>
     * A pixel is covered when {@code map[y][x] <= threshold} (threshold 0=open, 254=fully closed).
     */
    public static int[][] loadUpperLid() {
        return loadLidAlpha("defaultEye/lid-upper-symmetrical.png", EyeConfig.SCREEN_WIDTH, EyeConfig.SCREEN_HEIGHT);
    }

    /** Load the lower-lid PNG, same semantics as {@link #loadUpperLid()}. */
    public static int[][] loadLowerLid() {
        return loadLidAlpha("defaultEye/lid-lower-symmetrical.png", EyeConfig.SCREEN_WIDTH, EyeConfig.SCREEN_HEIGHT);
    }

    // ── ARGB-32 helpers ───────────────────────────────────────────────────────

    public static int argb32(int r, int g, int b) {
        return (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Load a PNG resource, scale it to the requested dimensions if necessary, and return all pixels as ARGB-32 int[][]
     * [height][width].
     */
    private static int[][] loadArgb(String resource, int expectedW, int expectedH) {
        BufferedImage img = loadImage(resource);

        // Rescale if the actual size doesn't match what EyeConfig declares
        if (img.getWidth() != expectedW || img.getHeight() != expectedH) {
            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(expectedW, expectedH,
                    BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, expectedW, expectedH, null);
            g2.dispose();
            img = scaled;
        }

        int[][] out = new int[expectedH][expectedW];
        for (int y = 0; y < expectedH; y++) {
            for (int x = 0; x < expectedW; x++) {
                // getRGB returns ARGB-32; mask to opaque RGB
                out[y][x] = img.getRGB(x, y) & 0x00FFFFFF;
            }
        }
        return out;
    }

    /**
     * Load a lid PNG (LA mode: luminance + alpha) and produce a threshold map. The alpha channel encodes lid coverage:
     * alpha=255 → always covered, alpha=0 → never covered. We store {@code 255 - alpha} so the map plugs directly into
     * {@link EyelidRenderer#isEyelid} which tests {@code map[y][x] <= threshold}.
     */
    private static int[][] loadLidAlpha(String resource, int expectedW, int expectedH) {
        BufferedImage img = loadImage(resource);

        // Convert to ARGB so getRGB() gives us a consistent alpha channel
        BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = argb.createGraphics();
        g2.drawImage(img, 0, 0, null);
        g2.dispose();

        // Rescale to screen dimensions if needed
        if (argb.getWidth() != expectedW || argb.getHeight() != expectedH) {
            BufferedImage scaled = new BufferedImage(expectedW, expectedH, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D gs = scaled.createGraphics();
            gs.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gs.drawImage(argb, 0, 0, expectedW, expectedH, null);
            gs.dispose();
            argb = scaled;
        }

        int[][] out = new int[expectedH][expectedW];
        for (int y = 0; y < expectedH; y++) {
            for (int x = 0; x < expectedW; x++) {
                int pixel = argb.getRGB(x, y); // ARGB-32
                int alpha = (pixel >>> 24) & 0xFF; // 0=transparent, 255=opaque lid
                out[y][x] = 255 - alpha; // 0=always covered, 255=always open
            }
        }
        return out;
    }

    private static BufferedImage loadImage(String resource) {
        InputStream is = EyeTextures.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) {
            throw new RuntimeException("Cannot find resource: " + resource);
        }
        try {
            return ImageIO.read(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load image: " + resource, e);
        }
    }
}
