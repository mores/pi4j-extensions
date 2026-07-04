package com.mores.uncanny;

import java.util.Random;

/**
 * Autonomous eye-motion controller. Implements the same saccade model as the Arduino original: 1. Hold at a position
 * for a random time (up to HOLD_MAX_US). 2. Pick a new target within the unit circle (so the eye doesn't strain into
 * corners). 3. Ease to that target using the cubic 3t²-2t³ lookup table over a random duration (MOVE_MIN_US ..
 * MOVE_MAX_US). 4. Repeat. Positions are in the 0–1023 range on both axes (matching the Arduino's 10-bit joystick
 * space). The caller maps these to sclera pixel offsets. For two eyes, construct two EyeMotion instances; pass the same
 * Random so they stay correlated (they should look in the same direction), but give the second one a small time offset
 * by calling update() once at construction so they don't move in perfect lockstep.
 */
public class EyeMotion {

    /**
     * Cubic ease in/out lookup table. Index 0-255 maps to a smoothed output 0-255 following y = 3t²-2t³. Verbatim from
     * the Arduino source.
     */
    private static final int[] EASE = { 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 7,
            8, 9, 9, 10, 10, 11, 12, 12, 13, 14, 15, 15, 16, 17, 18, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 27, 28, 29,
            30, 31, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 44, 45, 46, 47, 48, 50, 51, 52, 53, 54, 56, 57, 58, 60, 61,
            62, 63, 65, 66, 67, 69, 70, 72, 73, 74, 76, 77, 78, 80, 81, 83, 84, 85, 87, 88, 90, 91, 93, 94, 96, 97, 98,
            100, 101, 103, 104, 106, 107, 109, 110, 112, 113, 115, 116, 118, 119, 121, 122, 124, 125, 127, 128, 130,
            131, 133, 134, 136, 137, 139, 140, 142, 143, 145, 146, 148, 149, 151, 152, 154, 155, 157, 158, 159, 161,
            162, 164, 165, 167, 168, 170, 171, 172, 174, 175, 177, 178, 179, 181, 182, 183, 185, 186, 188, 189, 190,
            192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 204, 205, 207, 208, 209, 210, 211, 213, 214, 215, 216,
            217, 218, 219, 220, 221, 222, 224, 225, 226, 227, 228, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237,
            237, 238, 239, 240, 240, 241, 242, 243, 243, 244, 245, 245, 246, 246, 247, 248, 248, 249, 249, 250, 250,
            251, 251, 251, 252, 252, 252, 253, 253, 253, 254, 254, 254, 254, 254, 255, 255, 255, 255, 255, 255, 255 };

    private final Random rng;

    // Current and target positions (0-1023)
    private int oldX, oldY;
    private int newX, newY;
    private int curX, curY;

    // Motion timing
    private boolean moving;
    private long moveStartUs;
    private long moveDurationUs;

    public EyeMotion(Random rng) {
        this.rng = rng;
        this.curX = this.oldX = this.newX = 512;
        this.curY = this.oldY = this.newY = 512;
        this.moving = false;
        this.moveStartUs = microsNow();
        // Start with a random hold before first move
        this.moveDurationUs = (long) (rng.nextDouble() * EyeConfig.HOLD_MAX_US);
    }

    /**
     * Advance the motion model one tick and return the current position.
     *
     * @return int[2] { x, y } in 0-1023 range
     */
    public int[] update() {
        long now = microsNow();
        long dt = now - moveStartUs;

        if (moving) {
            if (dt >= moveDurationUs) {
                // Arrived – snap to destination and start hold
                curX = newX;
                curY = newY;
                oldX = newX;
                oldY = newY;
                moving = false;
                moveStartUs = now;
                moveDurationUs = randomHold();
            } else {
                // Ease through the saccade
                int easeIdx = (int) (255L * dt / moveDurationUs);
                if (easeIdx > 255)
                    easeIdx = 255;
                int e = EASE[easeIdx] + 1; // 1-256
                curX = oldX + (newX - oldX) * e / 256;
                curY = oldY + (newY - oldY) * e / 256;
            }
        } else {
            curX = oldX;
            curY = oldY;
            if (dt >= moveDurationUs) {
                // Choose a new target inside the inscribed circle so the eye
                // never strains to an unreachable corner.
                pickTarget();
                moveDurationUs = EyeConfig.MOVE_MIN_US
                        + (long) (rng.nextDouble() * (EyeConfig.MOVE_MAX_US - EyeConfig.MOVE_MIN_US));
                moveStartUs = now;
                moving = true;
            }
        }
        return new int[] { curX, curY };
    }

    /** Current X position (0-1023), valid between update() calls. */
    public int getX() {
        return curX;
    }

    /** Current Y position (0-1023), valid between update() calls. */
    public int getY() {
        return curY;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Pick a new (newX, newY) target guaranteed to lie within the unit circle. */
    private void pickTarget() {
        int dx, dy;
        do {
            newX = rng.nextInt(1024);
            newY = rng.nextInt(1024);
            dx = newX * 2 - 1023;
            dy = newY * 2 - 1023;
        } while ((long) dx * dx + (long) dy * dy > 1023L * 1023L);
    }

    private long randomHold() {
        return (long) (rng.nextDouble() * EyeConfig.HOLD_MAX_US);
    }

    private static long microsNow() {
        return System.nanoTime() / 1_000L;
    }
}
