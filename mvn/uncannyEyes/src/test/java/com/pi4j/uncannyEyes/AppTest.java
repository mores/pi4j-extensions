package com.pi4j.uncannyEyes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Random;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.drivers.display.graphics.*;
import com.pi4j.drivers.display.graphics.awt.JFrameGraphicsDriver;

import com.pi4j.extensions.DisplayUtils;
import com.pi4j.extensions.Utils;

public class AppTest {
    private static Logger log = LoggerFactory.getLogger(AppTest.class);

    private BufferedImage iris;
    private BufferedImage sclera;
    private BufferedImage lower;
    private BufferedImage upper;

    public void testOne() throws Exception {
        log.info("testOne");

        int width = 240;
        int height = 240;

        //
        JFrameGraphicsDriver driver0 = new JFrameGraphicsDriver(width, height);
        GraphicsDisplay display0 = new GraphicsDisplay(driver0);
        Graphics graphics0 = display0.getGraphics();
        graphics0.drawRgb(0, 0, width, height, DisplayUtils.createTestPattern(width, height));

        //
        JFrameGraphicsDriver driver1 = new JFrameGraphicsDriver(width, height);
        com.pi4j.drivers.display.graphics.GraphicsDisplay display1 = new com.pi4j.drivers.display.graphics.GraphicsDisplay(
                driver1);
        Graphics graphics1 = display1.getGraphics();

        //
        com.pi4j.drivers.display.graphics.awt.JFrameGraphicsDriver driver2 = new com.pi4j.drivers.display.graphics.awt.JFrameGraphicsDriver(
                width, height);
        com.pi4j.drivers.display.graphics.GraphicsDisplay display2 = new com.pi4j.drivers.display.graphics.GraphicsDisplay(
                driver2);
        Graphics graphics2 = display2.getGraphics();

        com.mores.uncanny.UncannyEyesEngine engine = com.mores.uncanny.UncannyEyesEngine.builder().addDisplay(graphics1)
                .addDisplay(graphics2).build();
        engine.start();

        //
        JFrameGraphicsDriver driver3 = new JFrameGraphicsDriver(width, height);
        GraphicsDisplay display3 = new GraphicsDisplay(driver3);
        Graphics graphics3 = display3.getGraphics();

        com.pi4j.drivers.display.graphics.awt.AwtGraphics awt = new com.pi4j.drivers.display.graphics.awt.AwtGraphics();

        iris = ImageIO.read(getClass().getClassLoader().getResourceAsStream("defaultEye/iris.png"));
        sclera = ImageIO.read(getClass().getClassLoader().getResourceAsStream("defaultEye/sclera.png"));
        lower = ImageIO.read(getClass().getClassLoader().getResourceAsStream("defaultEye/lid-lower-symmetrical.png"));
        upper = ImageIO.read(getClass().getClassLoader().getResourceAsStream("defaultEye/lid-upper-symmetrical.png"));

        double MAXRANGE = 125;
        Random random = new Random();

        java.util.List<Point2D> points = new java.util.ArrayList<>();

        double startX = 0;
        double startY = 0;

        while (1 == 1) {

            java.awt.geom.Point2D start = new java.awt.geom.Point2D.Double(startX, startY);

            double randomX = random.nextInt((int) MAXRANGE);
            double randomY = random.nextInt((int) MAXRANGE - 68) + 68;
            java.awt.geom.Point2D end = new java.awt.geom.Point2D.Double(randomX, randomY);

            int randomFrames = random.nextInt(7) + 3;

            int pupil = random.nextInt(20) + 20;

            for (Point2D point : Utils.pointsOnLine(new java.awt.geom.Line2D.Double(start, end), randomFrames)) {
                int x = (int) Math.round(point.getX() - (MAXRANGE / 2.0));
                int y = (int) Math.round(point.getY() - (MAXRANGE / 2.0));

                awt.drawImage(graphics3, 0, 0, drawEye(x, y, pupil));
            }

            int randomSleep = random.nextInt(2000);
            Utils.delay(Duration.ofMillis(randomSleep));

            startX = randomX;
            startY = randomY;

            Thread.yield();
        }
    }

    private BufferedImage drawEye(int x, int y, int pupil) {

        log.debug("drawEye: " + x + " " + y + " " + pupil);

        if (pupil > 30) {
            pupil = 30;
        }

        BufferedImage img = new BufferedImage(240, 240, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g2d = img.createGraphics();

        // 375 x 375 original size
        g2d.drawImage(sclera, -68 + x, -68 + y, null);

        // Shape starts in upper left of rectangle

        // 80 too big
        int radius = 70;

        java.awt.geom.Point2D topLeft = new java.awt.geom.Point2D.Float(12, 12);
        float rad = 5;
        float[] dist = { 0.0f, 0.2f, 1.0f };
        Color[] colors = { Color.RED, Color.WHITE, Color.BLUE };
        java.awt.RadialGradientPaint rgp = new java.awt.RadialGradientPaint(topLeft, rad, dist, colors);
        g2d.setPaint(rgp);
        g2d.fillOval(120 - radius + x, 120 - radius + y, radius * 2, radius * 2);

        java.awt.geom.Point2D bottomRight = new java.awt.geom.Point2D.Float(120 + (x / 1.5f), 120 + (y / 1.5f));
        rad = 25;
        float[] dist2 = { 0.0f, 0.5f, 1.0f };
        Color[] colors2 = { Color.BLACK, Color.RED, Color.BLACK };
        rgp = new java.awt.RadialGradientPaint(bottomRight, rad, dist2, colors2);
        g2d.setPaint(rgp);
        g2d.fillOval(120 - pupil + x, 120 - pupil + y, pupil * 2, pupil * 2);

        g2d.drawImage(lower, 0, 0, null);
        g2d.drawImage(upper, 0, 0, null);
        g2d.dispose();

        return img;
    }
}
