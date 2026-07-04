package com.mores.uncanny;

/**
 * All compile-time constants for the uncanny eyes renderer. Dimensions are calibrated for the defaultEye PNG resources:
 * sclera.png 375×375 (the full white-of-eye texture) iris.png 256×64 (polar strip: width=angles, height=radius bands)
 * lid PNGs 240×240 (same as screen) IMPORTANT: there are two separate iris dimension concepts: IRIS_CIRCLE_* – the SIZE
 * of the circular iris region in sclera pixel space. This is what gets positioned over the sclera and what the polar
 * lookup table covers. Must be square (circle). IRIS_TEX_* – the dimensions of the iris.png texture strip (256×64). The
 * polar table output (dist, angle) indexes into this texture.
 */
public final class EyeConfig {

    private EyeConfig() {
    }

    // ── Display ───────────────────────────────────────────────────────────────
    /** Width of one TFT/OLED display in pixels. */
    public static final int SCREEN_WIDTH = 240;
    /** Height of one TFT/OLED display in pixels. */
    public static final int SCREEN_HEIGHT = 240;

    // ── Sclera (white of eye) texture ─────────────────────────────────────────
    public static final int SCLERA_WIDTH = 375;
    public static final int SCLERA_HEIGHT = 375;

    // ── Iris CIRCLE – the circular region overlaid on the sclera ─────────────
    /**
     * Diameter in sclera pixels of the circular iris/pupil region. This matches the dark opening visible in sclera.png
     * (~148 px diameter). The polar lookup table is built at this size.
     */
    public static final int IRIS_CIRCLE_SIZE = 148;

    // ── Iris TEXTURE – the polar-strip PNG (iris.png) ─────────────────────────
    /** Width of the iris polar-strip texture (= number of angle steps = 256). */
    public static final int IRIS_TEX_W = 256;
    /** Height of the iris polar-strip texture (= number of radius bands = 64). */
    public static final int IRIS_TEX_H = 64;

    // ── Pupil / iris scale ────────────────────────────────────────────────────
    /** Iris scale value that gives the smallest iris / largest pupil (dark room). */
    public static final int IRIS_MIN = 120;
    /** Iris scale value that gives the largest iris / smallest pupil (bright room). */
    public static final int IRIS_MAX = 720;

    // ── Eye motion ────────────────────────────────────────────────────────────
    /** Minimum saccade duration (µs) – ~1/14 second. */
    public static final long MOVE_MIN_US = 72_000L;
    /** Maximum saccade duration (µs) – ~1/7 second. */
    public static final long MOVE_MAX_US = 144_000L;
    /** Maximum hold time between saccades (µs) – 3 seconds. */
    public static final long HOLD_MAX_US = 3_000_000L;

    // ── Blinking ──────────────────────────────────────────────────────────────
    /** Average time between blink starts (µs). */
    public static final long BLINK_INTERVAL_US = 4_000_000L;
    /** ± random spread around blink interval (µs). */
    public static final long BLINK_JITTER_US = 2_000_000L;
    /** Minimum blink close/open half-duration (µs). */
    public static final long BLINK_MIN_HALF_US = 35_000L;
    /** Maximum blink close/open half-duration (µs). */
    public static final long BLINK_MAX_HALF_US = 150_000L;

    // ── Eyelid tracking ───────────────────────────────────────────────────────
    /**
     * When true the upper eyelid droops slightly when the eye looks down, matching the Arduino TRACKING #define.
     */
    public static final boolean TRACKING = false;

    // ── Dual-eye convergence ──────────────────────────────────────────────────
    /**
     * Pixel offset added to the inner eye so both eyes appear to converge at a natural conversational distance. Only
     * meaningful when NUM_EYES > 1.
     */
    public static final int CONVERGENCE_OFFSET = 4;

    // ── Number of eyes ────────────────────────────────────────────────────────
    public static final int NUM_EYES = 2;
}
