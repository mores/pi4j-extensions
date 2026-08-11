package com.mores.control.renderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mores.control.ScreenRenderer;
import com.pi4j.drivers.display.graphics.Graphics; // TODO: adjust to the actual Graphics type returned by GraphicsDisplay#getGraphics()
import com.pi4j.drivers.display.graphics.awt.AwtGraphics;
import com.pi4j.extensions.Utils;

/**
 * "Cyborg" eye animation: a red/white/blue radial-gradient iris with a black/red radial-gradient pupil that wanders
 * around the sclera texture in short random saccades (3-9 in-between frames per move) separated by a random pause
 * (0-2s). This is a straight port of the original standalone prototype in {@code App.old} onto the current
 * {@link ScreenRenderer} architecture.
 * <p>
 * Like {@link UncannyEyesRenderer}, this owns every display passed in and drives them all from a single background
 * thread so all screens show the exact same frame at the exact same moment (one shared RNG-driven gaze), rather than
 * each display running its own independent, unsynchronized wander.
 */
public class CyborgRenderer implements ScreenRenderer {

    private static final Logger log = LoggerFactory.getLogger(CyborgRenderer.class);

    /** Half-range (in sclera pixels) that the simulated gaze target is allowed to wander within. */
    private static final double MAXRANGE = 125;

    private final List<Graphics> displays;
    private final AwtGraphics awt = new AwtGraphics();

    private BufferedImage sclera;
    private BufferedImage lower;
    private BufferedImage upper;

    private volatile boolean running;
    private Thread thread;

    /**
     * @param displays
     *            every physical display this animation should be drawn to, in lock-step.
     */
    public CyborgRenderer(List<Graphics> displays) {
        if (displays == null || displays.isEmpty()) {
            throw new IllegalArgumentException("CyborgRenderer requires at least one display");
        }
        this.displays = List.copyOf(displays);
    }

    @Override
    public void start() {
        try {
            sclera = ImageIO.read(getClass().getClassLoader().getResourceAsStream("defaultEye/sclera.png"));
            lower = ImageIO
                    .read(getClass().getClassLoader().getResourceAsStream("defaultEye/lid-lower-symmetrical.png"));
            upper = ImageIO
                    .read(getClass().getClassLoader().getResourceAsStream("defaultEye/lid-upper-symmetrical.png"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load cyborg eye textures", e);
        }

        running = true;
        thread = new Thread(this::animate, "cyborg-render");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                thread = null;
            }
        }
    }

    private void animate() {
        Random random = new Random();

        double startX = 0;
        double startY = 0;

        while (running) {
            Point2D start = new Point2D.Double(startX, startY);

            double randomX = random.nextInt((int) MAXRANGE);
            double randomY = random.nextInt((int) MAXRANGE - 68) + 68;
            Point2D end = new Point2D.Double(randomX, randomY);

            int randomFrames = random.nextInt(7) + 3;
            int pupil = random.nextInt(20) + 20;

            for (Point2D point : Utils.pointsOnLine(new Line2D.Double(start, end), randomFrames)) {
                if (!running) {
                    return;
                }

                int x = (int) Math.round(point.getX() - (MAXRANGE / 2.0));
                int y = (int) Math.round(point.getY() - (MAXRANGE / 2.0));

                BufferedImage frame = drawEye(x, y, pupil);
                for (Graphics display : displays) {
                    awt.drawImage(display, 0, 0, frame);
                }
            }

            if (!running) {
                return;
            }

            int randomSleep = random.nextInt(2000);
            Utils.delay(Duration.ofMillis(randomSleep));

            startX = randomX;
            startY = randomY;
            Thread.yield();
        }
    }

    private BufferedImage drawEye(int x, int y, int pupil) {
        log.debug("drawEye: {} {} {}", x, y, pupil);

        if (pupil > 30) {
            pupil = 30;
        }

        BufferedImage img = new BufferedImage(240, 240, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = img.createGraphics();

        // 375 x 375 original size
        g2d.drawImage(sclera, -68 + x, -68 + y, null);

        // Shape starts in upper left of rectangle
        int radius = 70;
        Point2D topLeft = new Point2D.Float(12, 12);
        float rad = 5;
        float[] dist = { 0.0f, 0.2f, 1.0f };
        Color[] colors = { Color.RED, Color.WHITE, Color.BLUE };
        RadialGradientPaint rgp = new RadialGradientPaint(topLeft, rad, dist, colors);
        g2d.setPaint(rgp);
        g2d.fillOval(120 - radius + x, 120 - radius + y, radius * 2, radius * 2);

        Point2D bottomRight = new Point2D.Float(120 + (x / 1.5f), 120 + (y / 1.5f));
        rad = 25;
        float[] dist2 = { 0.0f, 0.5f, 1.0f };
        Color[] colors2 = { Color.BLACK, Color.RED, Color.BLACK };
        rgp = new RadialGradientPaint(bottomRight, rad, dist2, colors2);
        g2d.setPaint(rgp);
        g2d.fillOval(120 - pupil + x, 120 - pupil + y, pupil * 2, pupil * 2);

        g2d.drawImage(lower, 0, 0, null);
        g2d.drawImage(upper, 0, 0, null);
        g2d.dispose();

        return img;
    }
}
