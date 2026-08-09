package com.mores.control.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mores.control.ScreenRenderer;
import com.mores.uncanny.UncannyEyesEngine;
import com.pi4j.drivers.display.graphics.Graphics; // TODO: adjust to the actual Graphics type returned by GraphicsDisplay#getGraphics()
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a single-display UncannyEyesEngine so it can be started/stopped independently per physical display, alongside
 * the other render modes. ASSUMPTION: UncannyEyesEngine exposes a stop() method to tear down its animation thread. If
 * it doesn't, swap the call below for whatever teardown API it actually has (close(), shutdown(), etc.) -- otherwise
 * switching away from this mode will leave a stray thread running.
 */
@Slf4j
public class UncannyEyesRenderer implements ScreenRenderer {

    private static Logger log = LoggerFactory.getLogger(UncannyEyesRenderer.class);

    private final Graphics graphics;
    private UncannyEyesEngine engine;

    public UncannyEyesRenderer(Graphics graphics) {
        this.graphics = graphics;
    }

    @Override
    public void start() {
        engine = UncannyEyesEngine.builder().addDisplay(graphics).build();
        engine.start();
    }

    @Override
    public void stop() {
        if (engine == null) {
            return;
        }
        try {
            engine.stop(); // TODO: confirm this method exists on UncannyEyesEngine
        } catch (Exception e) {
            log.warn("Error stopping UncannyEyesEngine", e);
        } finally {
            engine = null;
        }
    }
}
