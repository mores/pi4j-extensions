package com.mores.uncanny;

/**
 * Holds all runtime state for one physical eye. Each eye has: • its own {@link IrisRenderer} (different texture
 * references per eye) • its own {@link EyelidRenderer} • its own {@link BlinkState} (eyes can blink independently) • a
 * shared {@link EyeMotion} (both eyes track the same target) • a shared {@link IrisScaler} (both pupils dilate
 * together) • a flat RGB-565 frame buffer used to batch-write to the display • a mirror flag for the right eye (sclera
 * X is horizontally flipped)
 */
public class Eye {

    // ── Renderers ─────────────────────────────────────────────────────────────
    public final IrisRenderer irisRenderer;
    public final EyelidRenderer eyelidRenderer;
    public final BlinkState blink;

    // ── Shared with sibling eye ───────────────────────────────────────────────
    public final EyeMotion motion;
    public final IrisScaler irisScaler;

    // ── Per-eye autonomous squint animator ────────────────────────────────────
    /** Drives random open/squint cycles independently per eye. Null in SKELETON_MODE. */
    public final SquintState squint;

    // ── Frame buffer ──────────────────────────────────────────────────────────
    /** Flat RGB-565 pixel buffer, row-major, length = SCREEN_WIDTH * SCREEN_HEIGHT. */
    public final int[] frameBuf;

    /**
     * When true, the sclera X offset is mirrored so this eye's texture scrolls in the opposite direction – required for
     * a right eye that is a mirror image of the left.
     */
    public final boolean mirror;

    /** Index: 0 for left eye, 1 for right eye. Used for convergence offset. */
    public final int index;

    public Eye(int index, boolean mirror, IrisRenderer irisRenderer, EyelidRenderer eyelidRenderer, BlinkState blink,
            EyeMotion motion, IrisScaler irisScaler, SquintState squint) {
        this.index = index;
        this.mirror = mirror;
        this.irisRenderer = irisRenderer;
        this.eyelidRenderer = eyelidRenderer;
        this.blink = blink;
        this.motion = motion;
        this.irisScaler = irisScaler;
        this.squint = squint;
        this.frameBuf = new int[EyeConfig.SCREEN_WIDTH * EyeConfig.SCREEN_HEIGHT];
    }
}
