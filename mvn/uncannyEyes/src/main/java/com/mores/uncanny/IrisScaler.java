package com.mores.uncanny;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Produces organic iris-scale (pupil dilation) values over time. The Arduino original uses a recursive
 * midpoint-displacement algorithm that subdivides a time interval until the scale change per segment is less than 8.
 * This creates the same slowly-drifting, never-repeating motion as a real pupil reacting to ambient light changes.
 * Scale values range from {@link EyeConfig#IRIS_MIN} to {@link EyeConfig#IRIS_MAX} and map directly to the iScale
 * parameter of {@link IrisRenderer#getPixel}. A fresh 10-second macro-segment is subdivided each time the stack drains.
 */
public class IrisScaler {

    private final Random rng;
    private final int irisMin;
    private final int irisMax;

    // Stack of leaf segments: each long[4] = { valStart, valEnd, startUs, endUs }
    private final Deque<long[]> stack = new ArrayDeque<>();

    // Value at the boundary between the finished segment and the next
    private int boundaryVal;

    public IrisScaler(Random rng) {
        this(rng, EyeConfig.IRIS_MIN, EyeConfig.IRIS_MAX);
    }

    public IrisScaler(Random rng, int irisMin, int irisMax) {
        this.rng = rng;
        this.irisMin = irisMin;
        this.irisMax = irisMax;
        boundaryVal = (irisMin + irisMax) / 2;

        // Prime the first macro-segment immediately
        long now = microsNow();
        enqueueSegment(boundaryVal, randomScale(), now, now + 10_000_000L, irisMax - irisMin);
    }

    /**
     * Returns the iris scale for the current instant. Call once per frame; the result changes smoothly over time.
     */
    public int getScale() {
        long now = microsNow();

        // Drain finished leaf segments from the front of the deque
        while (!stack.isEmpty()) {
            long[] seg = stack.peek();
            if (now >= seg[3]) {
                // This segment is over; its end value becomes the next boundary
                boundaryVal = (int) seg[1];
                stack.pop();
            } else {
                // Interpolate within this segment
                long dt = now - seg[2];
                long dur = seg[3] - seg[2];
                return (int) (seg[0] + (seg[1] - seg[0]) * dt / dur);
            }
        }

        // Stack drained – generate a fresh macro-segment
        int next = randomScale();
        long now2 = microsNow();
        enqueueSegment(boundaryVal, next, now2, now2 + 10_000_000L, irisMax - irisMin);
        boundaryVal = next;
        return (irisMin + irisMax) / 2;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Recursively subdivide [startUs..endUs] until value-range < 8, then push leaf segments onto the deque in
     * chronological order. Using a local stack to avoid actual recursion overhead on the Pi.
     */
    private void enqueueSegment(int sv, int ev, long st, long et, int range) {
        // Iterative midpoint-displacement via an explicit work-stack
        record Seg(int sv, int ev, long st, long et, int range) {
        }
        Deque<Seg> work = new ArrayDeque<>();
        work.push(new Seg(sv, ev, st, et, range));

        // Collect leaf segments in a temporary list so we can add them in order
        java.util.List<long[]> leaves = new java.util.ArrayList<>();

        while (!work.isEmpty()) {
            Seg s = work.pop();
            if (s.range() < 8) {
                leaves.add(new long[] { s.sv(), s.ev(), s.st(), s.et() });
            } else {
                int newRange = s.range() / 2;
                long mid = (s.st() + s.et()) / 2;
                int mv = (s.sv() + s.ev() - newRange) / 2 + rng.nextInt(Math.max(1, newRange));
                mv = Math.max(irisMin, Math.min(irisMax, mv));
                // Push second half first so first half is processed first
                work.push(new Seg(mv, s.ev(), mid, s.et(), newRange));
                work.push(new Seg(s.sv(), mv, s.st(), mid, newRange));
            }
        }

        // Sort leaves by start time (midpoint displacement can interleave them)
        leaves.sort((a, b) -> Long.compare(a[2], b[2]));
        for (long[] leaf : leaves) {
            stack.addLast(leaf);
        }
    }

    private int randomScale() {
        return irisMin + rng.nextInt(irisMax - irisMin + 1);
    }

    private static long microsNow() {
        return System.nanoTime() / 1_000L;
    }
}
