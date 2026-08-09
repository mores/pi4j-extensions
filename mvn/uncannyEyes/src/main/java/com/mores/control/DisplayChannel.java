package com.mores.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mores.control.renderer.AlignmentPatternRenderer;
import com.mores.control.renderer.TestPatternRenderer;
import com.mores.control.renderer.UncannyEyesRenderer;
import com.pi4j.drivers.display.graphics.Graphics; // TODO: adjust to the actual Graphics type
import com.pi4j.drivers.display.graphics.GraphicsDisplay;
import com.pi4j.drivers.display.graphics.GraphicsDisplayDriver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents one physical display: its GraphicsDisplay/driver pair, its current x/y offset and rotation, and whichever
 * ScreenRenderer is currently active. Driver type is generic (D) since attachDriver()'s driver parameter type depends
 * on which panel driver you're using (St7789Driver, etc.) -- this class doesn't need to know the concrete type, just
 * pass it straight through to attachDriver().
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
    private DisplayMode mode;
    private ScreenRenderer renderer;

    public DisplayChannel(String name, GraphicsDisplay graphicsDisplay, GraphicsDisplayDriver driver, int width,
            int height, int initialXOffset, int initialYOffset, GraphicsDisplay.Rotation rotation,
            DisplayMode initialMode) {
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
        setMode(initialMode);
    }

    /** Change what's being shown on this display. Stops the old renderer, starts the new one. */
    public synchronized void setMode(DisplayMode newMode) {
        log.info("[{}] switching mode {} -> {}", name, mode, newMode);
        if (renderer != null) {
            renderer.stop();
        }
        this.mode = newMode;
        this.renderer = createRenderer(newMode);
        renderer.start();
    }

    /** Move this display's content by re-attaching the driver at a new offset. */
    public synchronized void setOffsets(int x, int y) {
        log.info("[{}] setting offsets ({}, {}) -> ({}, {})", name, xOffset, yOffset, x, y);
        this.xOffset = x;
        this.yOffset = y;
        attachDriver();
        if (renderer != null) {
            renderer.refresh();
        }
    }

    private void attachDriver() {
        graphicsDisplay.attachDriver(xOffset, yOffset, driver, rotation);
    }

    private ScreenRenderer createRenderer(DisplayMode m) {
        return switch (m) {
            case UNCANNY_EYES -> new UncannyEyesRenderer(graphics);
            case TEST_PATTERN -> new TestPatternRenderer(graphics, width, height);
            case ALIGNMENT_PATTERN -> new AlignmentPatternRenderer(graphics, width, height);
        };
    }
}
