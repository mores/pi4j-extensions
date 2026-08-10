package com.mores.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.drivers.display.graphics.Graphics; // TODO: adjust to the actual Graphics type
import com.pi4j.drivers.display.graphics.GraphicsDisplay;
import com.pi4j.drivers.display.graphics.GraphicsDisplayDriver;
import lombok.Getter;

/**
 * Represents one physical display: its GraphicsDisplay/driver pair, its current x/y offset and rotation. This class
 * only knows about physical alignment -- it does NOT own a display mode or a renderer. Mode is a property of the whole
 * rig (see {@link DisplayGroupController}), not of an individual display, because content like Uncanny Eyes must be
 * driven by a single renderer shared across every display so both eyes stay in sync; letting each DisplayChannel pick
 * its own mode independently is what causes the eyes to move/blink out of sync with each other. Driver type is generic
 * (D) since attachDriver()'s driver parameter type depends on which panel driver you're using (St7789Driver, etc.) --
 * this class doesn't need to know the concrete type, just pass it straight through to attachDriver().
 */
@Getter
public class DisplayChannel<D> {

    private static Logger log = LoggerFactory.getLogger(DisplayChannel.class);

    private final String name;
    private final GraphicsDisplay graphicsDisplay;
    private final Graphics graphics;
    private final GraphicsDisplayDriver driver;
    private final int width;
    private final int height;
    private final GraphicsDisplay.Rotation rotation;

    private int xOffset;
    private int yOffset;

    public DisplayChannel(String name, GraphicsDisplay graphicsDisplay, GraphicsDisplayDriver driver, int width,
            int height, int initialXOffset, int initialYOffset, GraphicsDisplay.Rotation rotation) {
        this.name = name;
        this.graphicsDisplay = graphicsDisplay;
        this.graphics = graphicsDisplay.getGraphics();
        this.driver = driver;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.xOffset = initialXOffset;
        this.yOffset = initialYOffset;

        attachDriver();
    }

    /**
     * Move this display's content by re-attaching the driver at a new offset. Whatever renderer is currently active for
     * this display (owned by {@link DisplayGroupController}) should be refreshed by the caller after this returns, if
     * it's a static-image renderer.
     */
    public synchronized void setOffsets(int x, int y) {
        log.info("[{}] setting offsets ({}, {}) -> ({}, {})", name, xOffset, yOffset, x, y);
        this.xOffset = x;
        this.yOffset = y;
        attachDriver();
    }

    private void attachDriver() {
        graphicsDisplay.attachDriver(xOffset, yOffset, driver, rotation);
    }
}
