package com.mores.uncanny;

import java.util.Random;

/**
 * Manages the blink state machine for one eye, faithfully porting the Arduino original's three-state model: NOBLINK →
 * (timer expires) → ENBLINK (closing) ENBLINK → (half done) → DEBLINK (opening) DEBLINK → (done) → NOBLINK All time
 * values are in microseconds.
 */
public class BlinkState {

    public enum Phase {
        NOBLINK, ENBLINK, DEBLINK
    }

    private Phase phase = Phase.NOBLINK;
    private long startUs = 0; // when current phase began
    private long halfUs = 0; // half-duration of this blink
    private long nextBlink = 0; // absolute time of next blink trigger

    private final Random rng;
    private final long intervalUs;
    private final long jitterUs;
    private final long minHalfUs;
    private final long maxHalfUs;

    public BlinkState(Random rng, long intervalUs, long jitterUs, long minHalfUs, long maxHalfUs) {
        this.rng = rng;
        this.intervalUs = intervalUs;
        this.jitterUs = jitterUs;
        this.minHalfUs = minHalfUs;
        this.maxHalfUs = maxHalfUs;
    }

    /** Convenience constructor using {@link EyeConfig} defaults. */
    public BlinkState(Random rng) {
        this(rng, EyeConfig.BLINK_INTERVAL_US, EyeConfig.BLINK_JITTER_US, EyeConfig.BLINK_MIN_HALF_US,
                EyeConfig.BLINK_MAX_HALF_US);
        // Stagger first blink so both eyes don't blink simultaneously at t=0
        nextBlink = randomInterval();
    }

    /**
     * Advance the state machine.
     *
     * @param nowUs
     *            current time in microseconds (e.g. System.nanoTime()/1000)
     */
    public void update(long nowUs) {
        switch (phase) {

            case NOBLINK:
                if (nowUs >= nextBlink) {
                    phase = Phase.ENBLINK;
                    startUs = nowUs;
                    halfUs = minHalfUs + (long) (rng.nextDouble() * (maxHalfUs - minHalfUs));
                }
                break;

            case ENBLINK:
                if (nowUs - startUs >= halfUs) {
                    // Eye is now fully closed; start opening
                    phase = Phase.DEBLINK;
                    startUs = nowUs;
                    // Opening is slightly faster than closing (natural feel)
                    halfUs = (long) (halfUs * 0.75);
                }
                break;

            case DEBLINK:
                if (nowUs - startUs >= halfUs) {
                    phase = Phase.NOBLINK;
                    nextBlink = nowUs + randomInterval();
                }
                break;
        }
    }

    /**
     * Returns the upper/lower eyelid threshold modifiers for this blink frame. Returned as int[2] { deltaUpper,
     * deltaLower } to be added on top of the tracking-based thresholds in {@link EyelidRenderer}. When fully open →
     * both deltas = 0. When fully closed → both deltas = 254 (lids meet at screen centre).
     *
     * @param nowUs
     *            current time in microseconds
     */
    public int blinkFactor(long nowUs) {
        if (phase == Phase.NOBLINK)
            return 0;

        long elapsed = nowUs - startUs;
        int s;

        if (phase == Phase.ENBLINK) {
            // Closing: 0 → 255 over halfUs
            s = (elapsed >= halfUs) ? 255 : (int) (255L * elapsed / halfUs);
        } else {
            // Opening: 255 → 0 over halfUs
            s = (elapsed >= halfUs) ? 0 : 255 - (int) (255L * elapsed / halfUs);
        }
        return s;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isOpen() {
        return phase == Phase.NOBLINK;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private long randomInterval() {
        long j = (long) ((rng.nextDouble() * 2.0 - 1.0) * jitterUs);
        return intervalUs + j;
    }
}
