package com.mores.uncanny;

import java.util.Random;

/**
 * The central engine that owns all eye state, runs the frame loop, and pushes completed frames to the physical
 * displays. Lifecycle:
 *
 * <pre>
 *   UncannyEyesEngine engine = UncannyEyesEngine.builder()
 *       .addDisplay(leftDisplay)
 *       .addDisplay(rightDisplay)
 *       .build();
 *   engine.start();           // starts background render thread
 *   ...
 *   engine.stop();            // graceful shutdown
 * </pre>
 *
 * The engine is deliberately single-threaded for rendering (no parallel pixel writes) because the Pi's CPU can sustain
 * ~30 fps at 128×128 on a single core, and multi-threading would require lock-stepping the SPI buses which adds
 * latency. If you add a Pi4J SPI implementation that supports DMA or double-buffering you can sub-class this and
 * override {@link #sendFrame(Eye, EyeDisplay)} to kick off the DMA transfer asynchronously while computing the next
 * frame.
 */
public class UncannyEyesEngine {

    private static final long TARGET_FRAME_US = 33_333L; // ~30 fps

    private final Eye[] eyes;
    private final com.pi4j.drivers.display.graphics.Graphics[] displays;
    private volatile boolean running;
    private Thread renderThread;

    // ── Builder ───────────────────────────────────────────────────────────────

    /** Use {@link #builder()} to construct the engine. */
    private UncannyEyesEngine(Eye[] eyes, com.pi4j.drivers.display.graphics.Graphics[] displays) {
        this.eyes = eyes;
        this.displays = displays;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final java.util.List<com.pi4j.drivers.display.graphics.Graphics> displayList = new java.util.ArrayList<>();
        private long seed = System.nanoTime();

        public Builder addDisplay(com.pi4j.drivers.display.graphics.Graphics d) {
            displayList.add(d);
            return this;
        }

        public Builder randomSeed(long seed) {
            this.seed = seed;
            return this;
        }

        public UncannyEyesEngine build() {
            if (displayList.isEmpty())
                throw new IllegalStateException("No displays added");

            Random rng = new Random(seed);
            int[][] sclera = EyeTextures.loadSclera();
            int[][] irisTex = EyeTextures.loadIris();
            int[][] polar = PolarTable.build(EyeConfig.IRIS_CIRCLE_SIZE, EyeConfig.IRIS_CIRCLE_SIZE);

            // Motion, iris scaling, blinking, and squinting are all shared across
            // eyes so both eyes always look in the same direction, blink together,
            // and squint together.
            EyeMotion motion = new EyeMotion(rng);
            IrisScaler irisScaler = new IrisScaler(rng);
            BlinkState sharedBlink = EyeConfig.SKELETON_MODE ? new BlinkState.NoOp() : new BlinkState(rng);
            SquintState sharedSquint = EyeConfig.SKELETON_MODE ? null : new SquintState(rng);

            int numEyes = displayList.size();
            Eye[] eyes = new Eye[numEyes];

            // In SKELETON_MODE the eyelids are completely disabled: load blank
            // (all-transparent) maps so isEyelid() never fires, and skip blink/squint.
            int[][] upperLid = EyeConfig.SKELETON_MODE ? blankLidMap() : EyeTextures.loadUpperLid();
            int[][] lowerLid = EyeConfig.SKELETON_MODE ? blankLidMap() : EyeTextures.loadLowerLid();

            for (int i = 0; i < numEyes; i++) {
                boolean mirror = (numEyes > 1 && i == 1);

                EyelidRenderer eyelidRenderer = new EyelidRenderer(upperLid, lowerLid, EyeConfig.SCREEN_WIDTH,
                        EyeConfig.SCREEN_HEIGHT);

                IrisRenderer irisRenderer = new IrisRenderer(polar, irisTex, sclera, eyelidRenderer);

                eyes[i] = new Eye(i, mirror, irisRenderer, eyelidRenderer, sharedBlink, motion, irisScaler,
                        sharedSquint);
            }

            return new UncannyEyesEngine(eyes, displayList.toArray(new com.pi4j.drivers.display.graphics.Graphics[0]));
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Start the background render thread. Runs at max priority: this loop paces itself to ~30fps with tight
     * Thread.sleep windows, and on constrained hardware (e.g. a Raspberry Pi) it will compete for CPU with anything
     * else in the same JVM -- a TUI's input/redraw thread, GC, etc. Elevating priority helps the scheduler favor frame
     * pacing over that other work so eye motion stays smooth instead of stuttering when the process has other threads
     * active.
     */
    public void start() {
        running = true;
        renderThread = new Thread(this::renderLoop, "uncanny-render");
        renderThread.setDaemon(true);
        renderThread.setPriority(Thread.MAX_PRIORITY);
        renderThread.start();
    }

    /** Stop the render thread and wait for it to finish. */
    public void stop() {
        running = false;
        if (renderThread != null) {
            try {
                renderThread.join(2_000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    // ── Render loop ───────────────────────────────────────────────────────────

    private void renderLoop() {
        while (running) {
            long frameStart = System.nanoTime() / 1_000L; // µs

            renderFrame();

            // Pace to TARGET_FRAME_US
            long elapsed = System.nanoTime() / 1_000L - frameStart;
            long sleepUs = TARGET_FRAME_US - elapsed;
            if (sleepUs > 1_000L) {
                try {
                    Thread.sleep(sleepUs / 1_000L, (int) ((sleepUs % 1_000L) * 1_000));
                } catch (InterruptedException e) {
                    running = false;
                }
            }
        }
    }

    /**
     * Compute and send one frame for every eye. This is the hot path. Every method called here must be as cheap as
     * possible; no allocations inside the pixel loop.
     */
    void renderFrame() {
        long nowUs = System.nanoTime() / 1_000L;

        // ── Motion ────────────────────────────────────────────────────────────
        // All eyes share one motion model; the first eye drives it.
        int[] pos = eyes[0].motion.update();
        int rawX = pos[0]; // 0-1023
        int rawY = pos[1]; // 0-1023

        // ── Iris scale ────────────────────────────────────────────────────────
        int iScale = eyes[0].irisScaler.getScale();

        // ── Shared blink + squint (advance once, apply to all eyes) ──────────
        Eye primary = eyes[0];
        primary.blink.update(nowUs);
        int squintU = 0, squintL = 0;
        if (primary.squint != null) {
            primary.squint.update(nowUs);
            squintU = primary.squint.getUpperThreshold();
            squintL = primary.squint.getLowerThreshold();
        }

        // ── Per-eye rendering ─────────────────────────────────────────────────
        for (int e = 0; e < eyes.length; e++) {
            Eye eye = eyes[e];
            com.pi4j.drivers.display.graphics.Graphics display = displays[e];

            // Map joystick 0-1023 to sclera scroll offsets (pixels).
            // Constrain to the range that keeps the iris centred on screen
            // with only a small wander, so the pupil/iris are always visible.
            // Iris centre in sclera space: (SCLERA_WIDTH/2, SCLERA_HEIGHT/2)
            // Screen centre scroll offset: centres the iris circle on screen.
            int irisCentreX = (EyeConfig.SCLERA_WIDTH - EyeConfig.SCREEN_WIDTH) / 2;
            int irisCentreY = (EyeConfig.SCLERA_HEIGHT - EyeConfig.SCREEN_HEIGHT) / 2;
            int wanderX = (EyeConfig.SCREEN_WIDTH - EyeConfig.IRIS_CIRCLE_SIZE) / 2 - 4;
            int wanderY = (EyeConfig.SCREEN_HEIGHT - EyeConfig.IRIS_CIRCLE_SIZE) / 2 - 4;
            int eyeX = map(rawX, 0, 1023, irisCentreX - wanderX, irisCentreX + wanderX);
            int eyeY = map(rawY, 0, 1023, irisCentreY - wanderY, irisCentreY + wanderY);

            // Slight inward convergence so both eyes appear to focus at a natural
            // distance. Left eye (index 0) nudges right (+), right eye nudges left (−).
            // Both eyes still scroll in the SAME direction for gaze — only the small
            // convergence offset differs.
            if (eyes.length > 1) {
                int converge = (eye.index == 0) ? EyeConfig.CONVERGENCE_OFFSET : -EyeConfig.CONVERGENCE_OFFSET;
                eyeX = Math.max(0, Math.min(eyeX + converge, EyeConfig.SCLERA_WIDTH - EyeConfig.SCREEN_WIDTH));
            }

            // NOTE: the mirror flag on the right eye flips the sclera TEXTURE so
            // the two eye images look like a matched pair (veins, etc.), but we
            // must NOT flip the scroll offset — that would make the right eye look
            // in the opposite direction (exotropia). Texture mirroring is handled
            // inside IrisRenderer/EyeTextures if needed; the scroll offsets are
            // always the same for both eyes (apart from the small convergence delta).

            // Compute eyelid thresholds for this frame (base + squint + blink).
            // Blink and squint were already advanced once above for all eyes.
            int[] thresh = eye.eyelidRenderer.computeThresholds(eyeX, eyeY, eye.blink, nowUs, squintU, squintL);

            // Rasterise full frame into the eye's own buffer
            eye.irisRenderer.fillFrame(eye.frameBuf, eyeX, eyeY, iScale, thresh[0], thresh[1]);

            // Push to display
            sendFrame(eye, display);
        }
    }

    /**
     * Transfer a completed frame buffer to the display. Override to use DMA / async SPI if your driver supports it.
     */
    protected void sendFrame(Eye eye, com.pi4j.drivers.display.graphics.Graphics display) {
        display.drawRgb(0, 0, EyeConfig.SCREEN_WIDTH, EyeConfig.SCREEN_HEIGHT, eye.frameBuf);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Arduino-style map() function. */
    static int map(int v, int inMin, int inMax, int outMin, int outMax) {
        return outMin + (int) ((long) (v - inMin) * (outMax - outMin) / (inMax - inMin));
    }

    /**
     * Returns a lid map where every value is 255 (fully transparent / never covered). Used in SKELETON_MODE so
     * {@link EyelidRenderer#isEyelid} never returns true.
     */
    private static int[][] blankLidMap() {
        int[][] map = new int[EyeConfig.SCREEN_HEIGHT][EyeConfig.SCREEN_WIDTH];
        for (int[] row : map)
            java.util.Arrays.fill(row, 255);
        return map;
    }
}
