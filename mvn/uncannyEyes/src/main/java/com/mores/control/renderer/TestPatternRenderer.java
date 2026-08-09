package com.mores.control.renderer;

import com.mores.control.ScreenRenderer;
import com.pi4j.extensions.DisplayUtils;
import com.pi4j.drivers.display.graphics.Graphics; // TODO: adjust to the actual Graphics type returned by GraphicsDisplay#getGraphics()

/**
 * Draws a static test pattern once, and re-draws it whenever the display's offset changes (since the pattern itself
 * doesn't animate).
 */
public class TestPatternRenderer implements ScreenRenderer {

    private final Graphics graphics;
    private final int width;
    private final int height;

    public TestPatternRenderer(Graphics graphics, int width, int height) {
        this.graphics = graphics;
        this.width = width;
        this.height = height;
    }

    @Override
    public void start() {
        refresh();
    }

    @Override
    public void stop() {
        // static image, nothing to tear down
    }

    @Override
    public void refresh() {
        graphics.drawRgb(0, 0, width, height, DisplayUtils.createTestPattern(width, height));
    }
}
