package com.mores.uncanny;

import java.util.Random;

/**
 * Autonomous squint animator for one eye.
 * <p>
 * Independently from blinking, the eye drifts between open and squinted states with random timing and smooth cubic
 * easing. This gives characters a subtle, unsettling expressiveness — particularly effective for skeletons, zombies, or
 * any non-human creature that should feel alive but wrong.
 * <p>
 * State machine:
 *
 * <pre>
 *   HOLD ──(timer expires)──► EASING ──(ease done)──► HOLD ──► …
 *         pick target squint           pick new hold
 * </pre>
 * <p>
 * Usage — call {@link #update(long)} once per frame, then read {@link #getUpperThreshold()} and
 * {@link #getLowerThreshold()} and add them to the base lid thresholds in {@link EyelidRenderer}.
 */
public class SquintState {

    private enum Phase {
        HOLD, EASING
    }

    /** Cubic ease in/out: y = 3t²−2t³, index 0-255 → value 0-255. */
    private static final int[] EASE;
    static {
        EASE = new int[256];
        for (int i = 0; i < 256; i++) {
            double t = i / 255.0;
            EASE[i] = (int) Math.round((3 * t * t - 2 * t * t * t) * 255);
        }
    }

    // ── Config (read once from EyeConfig) ────────────────────────────────────
    private final int squintMax;
    private final float lowerRatio;
    private final long holdMinUs, holdMaxUs;
    private final long easeMinUs, easeMaxUs;
    private final float squintProbability;
    private final float squintBaseline; // minimum level even when "open"
    private final Random rng;

    // ── State machine ─────────────────────────────────────────────────────────
    private Phase phase = Phase.HOLD;
    private long phaseEndUs = 0; // absolute µs when current phase ends

    /** Squint level at the start of the current ease (0.0 = open, 1.0 = full squint). */
    private float fromLevel = 0f;
    /** Squint level being eased toward. */
    private float toLevel = 0f;
    /** Current instantaneous squint level (updated each frame). */
    private float curLevel = 0f;

    private long easeStartUs = 0;
    private long easeDurationUs = 1;

    // ── Public API ────────────────────────────────────────────────────────────

    public SquintState(Random rng) {
        this.rng = rng;
        this.squintMax = EyeConfig.SQUINT_MAX_THRESHOLD;
        this.lowerRatio = EyeConfig.SQUINT_LOWER_RATIO;
        this.holdMinUs = EyeConfig.SQUINT_HOLD_MIN_US;
        this.holdMaxUs = EyeConfig.SQUINT_HOLD_MAX_US;
        this.easeMinUs = EyeConfig.SQUINT_EASE_MIN_US;
        this.easeMaxUs = EyeConfig.SQUINT_EASE_MAX_US;
        this.squintProbability = EyeConfig.SQUINT_PROBABILITY;
        this.squintBaseline = EyeConfig.SQUINT_BASELINE;

        // Start at baseline squint level so the eye opens into the right state
        phaseEndUs = randomHoldUs();
        fromLevel = squintBaseline;
        toLevel = squintBaseline;
        curLevel = squintBaseline;
    }

    /**
     * Advance the squint state machine.
     *
     * @param nowUs
     *            current time in microseconds
     */
    public void update(long nowUs) {
        switch (phase) {

            case HOLD:
                if (nowUs >= phaseEndUs) {
                    // Choose a new target squint level
                    fromLevel = curLevel;
                    if (rng.nextFloat() < squintProbability) {
                        // Deeply squinted: somewhere between 70% and 100% of max
                        toLevel = 0.7f + rng.nextFloat() * 0.3f;
                    } else {
                        // "Relaxed" — still squinted at baseline, never fully open
                        toLevel = squintBaseline + rng.nextFloat() * 0.15f;
                    }

                    easeStartUs = nowUs;
                    easeDurationUs = easeMinUs + (long) (rng.nextFloat() * (easeMaxUs - easeMinUs));
                    phaseEndUs = nowUs + easeDurationUs;
                    phase = Phase.EASING;
                }
                break;

            case EASING:
                if (nowUs >= phaseEndUs) {
                    curLevel = toLevel;
                    fromLevel = toLevel;
                    phaseEndUs = nowUs + randomHoldUs();
                    phase = Phase.HOLD;
                } else {
                    long elapsed = nowUs - easeStartUs;
                    int easeIdx = (int) Math.min(255, 255L * elapsed / easeDurationUs);
                    float t = EASE[easeIdx] / 255f;
                    curLevel = fromLevel + (toLevel - fromLevel) * t;
                }
                break;
        }
    }

    /**
     * Eyelid threshold delta for the upper lid this frame. Add this to the base upper threshold in
     * {@link EyelidRenderer}. A larger value pushes the upper lid further down.
     */
    public int getUpperThreshold() {
        return Math.round(curLevel * squintMax);
    }

    /**
     * Eyelid threshold delta for the lower lid this frame. Add this to the base lower threshold in
     * {@link EyelidRenderer}. A larger value pushes the lower lid further up.
     */
    public int getLowerThreshold() {
        return Math.round(curLevel * squintMax * lowerRatio);
    }

    /** Current squint level: 0.0 = fully open, 1.0 = maximum squint. */
    public float getLevel() {
        return curLevel;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private long randomHoldUs() {
        return holdMinUs + (long) (rng.nextFloat() * (holdMaxUs - holdMinUs));
    }
}
