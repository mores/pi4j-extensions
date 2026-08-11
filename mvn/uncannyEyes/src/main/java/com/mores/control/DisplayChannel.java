package com.mores.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.drivers.display.graphics.Graphics; // TODO: adjust to the actual Graphics type
import com.pi4j.drivers.display.graphics.GraphicsDisplay;
import com.pi4j.drivers.display.graphics.GraphicsDisplayDriver;
import lombok.Getter;

/**
 * Represents one physical display: its driver, its current x/y offset and rotation, and the {@link GraphicsDisplay} /
 * {@link Graphics} pair currently attached to that driver. This class only knows about physical alignment -- it does
 * NOT own a display mode or a renderer. Mode is a property of the whole rig (see {@link DisplayGroupController}), not
 * of an individual display; see that class's javadoc for why. IMPORTANT: {@code GraphicsDisplay.attachDriver(...)}
 * APPENDS a new entry to an internal list of attached drivers -- it does not replace whatever was attached before:
 *
 * <pre>
 * public void attachDriver(int x0, int y0, GraphicsDisplayDriver driver, Rotation rotation, Mirror mirror) {
 *     synchronized (lock) {
 *         drivers.add(new DriverEntry(x0, y0, driver, rotation.minus(...), mirror));
 *         markModified(0, 0, displayWidth, displayHeight);
 *     }
 * }
 * </pre>
 *
 * Calling attachDriver() a second time on the SAME GraphicsDisplay instance -- e.g. to move an offset -- does not move
 * the existing attachment, it adds a second one. Both entries then stay live and both get written to on every
 * subsequent frame, so the physical panel shows content at the OLD offset and the NEW offset every single frame,
 * forever (this was the exact "animates between the old and new offset on every frame" bug report). The only way to end
 * up with exactly one active attachment is to never call attachDriver() twice on the same GraphicsDisplay: build a
 * brand new GraphicsDisplay, call attachDriver() on it exactly once, and discard the old one.
 */
@Getter
public class DisplayChannel<D> {

    private static Logger log = LoggerFactory.getLogger(DisplayChannel.class);

    private final String name;
    private final GraphicsDisplayDriver driver;
    private final int width;
    private final int height;
    private final GraphicsDisplay.Rotation rotation;

    // TODO: confirm the actual Mirror enum location/values -- assumed to live nested on GraphicsDisplay alongside
    // Rotation, and NONE assumed to mean "no mirroring" (matching prior behavior, which never mirrored).

    private int xOffset;
    private int yOffset;

    private GraphicsDisplay graphicsDisplay;
    private Graphics graphics;

    public DisplayChannel(String name, GraphicsDisplayDriver driver, int width, int height, int initialXOffset,
            int initialYOffset, GraphicsDisplay.Rotation rotation) {
        this.name = name;
        this.driver = driver;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.xOffset = initialXOffset;
        this.yOffset = initialYOffset;

        rebuildGraphicsDisplay();
    }

    /**
     * Move this display's content to a new offset. Since attachDriver() can't be re-issued on the same GraphicsDisplay
     * (see class javadoc), this builds a brand new GraphicsDisplay/Graphics pair rather than mutating the existing one.
     * Whatever renderer(s) are currently active MUST be rebuilt after this returns so they pick up the new
     * {@link #getGraphics()} reference -- see {@link DisplayGroupController#onOffsetChanged}. The old GraphicsDisplay
     * is simply dropped; as long as nothing draws through it again, its stale driver entry never receives another
     * frame.
     */
    public synchronized void setOffsets(int x, int y) {
        log.info("[{}] setting offsets ({}, {}) -> ({}, {})", name, xOffset, yOffset, x, y);
        this.xOffset = x;
        this.yOffset = y;
        rebuildGraphicsDisplay();
    }

    private void rebuildGraphicsDisplay() {
        this.graphicsDisplay = new GraphicsDisplay(width, height);
        this.graphicsDisplay.attachDriver(xOffset, yOffset, driver, rotation); // exactly once per instance
        this.graphics = graphicsDisplay.getGraphics();
    }
}
