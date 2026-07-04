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

            // Motion and iris scaling are shared across all eyes
            EyeMotion motion = new EyeMotion(rng);
            IrisScaler irisScaler = new IrisScaler(rng);

            int numEyes = displayList.size();
            Eye[] eyes = new Eye[numEyes];

            for (int i = 0; i < numEyes; i++) {
                boolean mirror = (numEyes > 1 && i == 1);

                int[][] upper = EyeTextures.loadUpperLid();
                int[][] lower = EyeTextures.loadLowerLid();

                EyelidRenderer eyelidRenderer = new EyelidRenderer(upper, lower, EyeConfig.SCREEN_WIDTH,
                        EyeConfig.SCREEN_HEIGHT);

                IrisRenderer irisRenderer = new IrisRenderer(polar, irisTex, sclera, eyelidRenderer);

                // Stagger blink timing so both eyes don't always blink together
                BlinkState blink = new BlinkState(rng);

                eyes[i] = new Eye(i, mirror, irisRenderer, eyelidRenderer, blink, motion, irisScaler);
            }

            return new UncannyEyesEngine(eyes, displayList.toArray(new com.pi4j.drivers.display.graphics.Graphics[0]));
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Start the background render thread. */
    public void start() {
        running = true;
        renderThread = new Thread(this::renderLoop, "uncanny-render");
        renderThread.setDaemon(true);
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

            // Horizontal convergence: both eyes look slightly inward
            if (eyes.length > 1) {
                eyeX += EyeConfig.CONVERGENCE_OFFSET;
                eyeX = Math.min(eyeX, EyeConfig.SCLERA_WIDTH - EyeConfig.SCREEN_WIDTH);
            }

            // Right eye has mirrored sclera X
            if (eye.mirror) {
                eyeX = (EyeConfig.SCLERA_WIDTH - EyeConfig.SCREEN_WIDTH) - eyeX;
            }

            // Advance blink state machine
            eye.blink.update(nowUs);

            // Compute eyelid thresholds for this frame
            int[] thresh = eye.eyelidRenderer.computeThresholds(eyeX, eyeY, eye.blink, nowUs);

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
}
