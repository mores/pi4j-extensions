package com.mores.uncanny;

/**
 * Computes per-frame upper/lower eyelid thresholds and answers the per-pixel "is this covered by a lid?" question. The
 * eyelid maps are brightness-threshold images (values 0-255). A screen pixel at (x,y) is covered by the upper lid when
 * upper[y][x] <= uThreshold and by the lower lid when lower[y][x] <= lThreshold. Porting notes vs the Arduino original:
 * • The IIR filter uThreshold = (uThreshold*3 + n) / 4 is kept verbatim. • Blink blending uses the same arithmetic; we
 * just separate it into BlinkState.blinkFactor() for clarity. • TRACKING mirrors the Arduino #define of the same name.
 */
public class EyelidRenderer {

    private final int[][] upper;
    private final int[][] lower;
    private final int screenWidth;
    private final int screenHeight;

    /** Smoothed upper-lid tracking threshold (IIR state). */
    private int uThreshold = 128;

    /**
     * @param upper
     *            upper-lid map [screenHeight][screenWidth], values 0-255
     * @param lower
     *            lower-lid map [screenHeight][screenWidth], values 0-255
     * @param screenWidth
     *            display pixel width
     * @param screenHeight
     *            display pixel height
     */
    public EyelidRenderer(int[][] upper, int[][] lower, int screenWidth, int screenHeight) {
        this.upper = upper;
        this.lower = lower;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Compute the upper and lower threshold values for this frame. Call once per eye per frame, before the pixel loop.
     *
     * @param eyeX
     *            sclera X scroll offset in pixels (0 .. SCLERA_WIDTH - SCREEN_WIDTH)
     * @param eyeY
     *            sclera Y scroll offset in pixels (0 .. SCLERA_HEIGHT - SCREEN_HEIGHT)
     * @param blink
     *            this eye's blink state machine
     * @param nowUs
     *            current time in microseconds
     *
     * @return int[2] { upperThreshold, lowerThreshold }
     */
    public int[] computeThresholds(int eyeX, int eyeY, BlinkState blink, long nowUs) {
        int lThreshold;

        // Fixed open-eye thresholds – the TRACKING path is disabled because the
        // sclera scroll offsets (eyeX up to 672, eyeY up to 472) are far too large
        // to use as screen-space sample coordinates and always drove sampleX/Y
        // out of bounds, causing the lids to be permanently clamped shut.
        uThreshold = 0;
        lThreshold = 0;

        // Blend in the blink: as blinkFactor rises toward 255, lids close
        int bf = blink.blinkFactor(nowUs);
        if (bf > 0) {
            // Arduino formula: s = 256-s for ENBLINK, 1+s for DEBLINK
            // We already get 0→255 for close and 255→0 for open from blinkFactor,
            // so we just interpolate directly:
            // threshold moves from its tracking value toward 254 as bf → 255
            int uOut = uThreshold + (int) ((long) (254 - uThreshold) * bf / 255);
            int lOut = lThreshold + (int) ((long) (254 - lThreshold) * bf / 255);
            return new int[] { uOut, lOut };
        }

        return new int[] { uThreshold, lThreshold };
    }

    /**
     * Returns true if the pixel at (screenX, screenY) is covered by an eyelid and should be drawn with the lid colour
     * (usually black / 0x0000).
     */
    public boolean isEyelid(int screenX, int screenY, int uT, int lT) {
        return (upper[screenY][screenX] & 0xFF) <= uT || (lower[screenY][screenX] & 0xFF) <= lT;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
