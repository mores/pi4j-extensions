package com.mores.control.renderer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mores.control.ScreenRenderer;
import com.mores.uncanny.UncannyEyesEngine;
import com.pi4j.drivers.display.graphics.Graphics; // TODO: adjust to the actual Graphics type returned by GraphicsDisplay#getGraphics()
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a single UncannyEyesEngine that drives ALL of the physical displays passed in. This must own every display at
 * once (not one instance per display) because UncannyEyesEngine only shares motion/blink/squint state across the
 * {@code Eye}s it owns internally -- two separate engines, each given one display, would each get their own RNG seed
 * and independent blink/squint timers, so the two eyes would drift out of sync. Passing every display into a single
 * {@code UncannyEyesEngine.Builder} is what makes both eyes look, blink, and squint together. ASSUMPTION:
 * UncannyEyesEngine exposes a stop() method to tear down its animation thread. If it doesn't, swap the call below for
 * whatever teardown API it actually has (close(), shutdown(), etc.) -- otherwise switching away from this mode will
 * leave a stray thread running.
 */
@Slf4j
public class UncannyEyesRenderer implements ScreenRenderer {

    private static Logger log = LoggerFactory.getLogger(UncannyEyesRenderer.class);

    private final List<Graphics> displays;
    private UncannyEyesEngine engine;

    /**
     * @param displays
     *            every physical display this engine should drive, in a stable order (e.g. left eye, right eye).
     */
    public UncannyEyesRenderer(List<Graphics> displays) {
        if (displays == null || displays.isEmpty()) {
            throw new IllegalArgumentException("UncannyEyesRenderer requires at least one display");
        }
        this.displays = List.copyOf(displays);
    }

    @Override
    public void start() {
        UncannyEyesEngine.Builder builder = UncannyEyesEngine.builder();
        displays.forEach(builder::addDisplay);
        engine = builder.build();
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
