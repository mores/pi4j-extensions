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
     * <p>
     * The returned thresholds combine three independent effects:
     * <ol>
     * <li><b>Base tracking</b> – currently fixed at 0 (eyelids fully open).</li>
     * <li><b>Squint</b> – upper lid droops down and lower lid rises up, driven by {@link SquintState} deltas passed in
     * as parameters.</li>
     * <li><b>Blink</b> – overrides everything; lids slam fully shut on top of whatever squint level is active, then
     * reopen.</li>
     * </ol>
     *
     * @param eyeX
     *            sclera X scroll offset in pixels
     * @param eyeY
     *            sclera Y scroll offset in pixels
     * @param blink
     *            this eye's blink state machine
     * @param nowUs
     *            current time in microseconds
     * @param squintUpper
     *            upper-lid squint delta from {@link SquintState#getUpperThreshold()} (0 = open)
     * @param squintLower
     *            lower-lid squint delta from {@link SquintState#getLowerThreshold()} (0 = open)
     *
     * @return int[2] { upperThreshold, lowerThreshold }
     */
    public int[] computeThresholds(int eyeX, int eyeY, BlinkState blink, long nowUs, int squintUpper, int squintLower) {
        // Base tracking threshold – fixed open (see earlier comments about sclera
        // scroll offsets being too large to use as screen-space sample coordinates).
        uThreshold = 0;
        int lThreshold = 0;

        // ── Apply squint ──────────────────────────────────────────────────────
        // Squint pushes both lids inward from their open position.
        // Clamp to 253 so a subsequent blink can always push them past the squint.
        int uSq = Math.min(253, uThreshold + squintUpper);
        int lSq = Math.min(253, lThreshold + squintLower);

        // ── Blend in blink ────────────────────────────────────────────────────
        // Blink drives thresholds toward 254 (fully closed) regardless of squint.
        // When the eye is squinted, the blink still fully closes from the squinted
        // position, then reopens back to the squinted position.
        int bf = blink.blinkFactor(nowUs);
        if (bf > 0) {
            int uOut = uSq + (int) ((long) (254 - uSq) * bf / 255);
            int lOut = lSq + (int) ((long) (254 - lSq) * bf / 255);
            return new int[] { uOut, lOut };
        }

        return new int[] { uSq, lSq };
    }

    /**
     * Convenience overload with no squint (squint deltas = 0). Use this in SKELETON_MODE or when squinting is disabled.
     */
    public int[] computeThresholds(int eyeX, int eyeY, BlinkState blink, long nowUs) {
        return computeThresholds(eyeX, eyeY, blink, nowUs, 0, 0);
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
