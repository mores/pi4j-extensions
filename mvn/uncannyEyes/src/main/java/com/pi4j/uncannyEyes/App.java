package com.pi4j.etc.uncannyEyes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.Point;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.exception.LifecycleException;
import com.pi4j.exception.Pi4JException;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalOutputConfig;
import com.pi4j.io.gpio.digital.DigitalOutputProvider;
import com.pi4j.io.spi.Spi;
import com.pi4j.io.spi.SpiBus;
import com.pi4j.io.spi.SpiChipSelect;
import com.pi4j.io.spi.SpiConfig;
import com.pi4j.io.spi.SpiMode;
import com.pi4j.io.spi.SpiProvider;

import com.pi4j.extensions.Utils;
import com.pi4j.extensions.devices.spi.Adafruit3787;

public class App {

    private static Logger log = LoggerFactory.getLogger(App.class);

    private String[] args;

    private static Context pi4j;
    private Spi spi;

    private DigitalOutput bl;
    private DigitalOutput dc;

    private BufferedImage iris;
    private BufferedImage sclera;
    private BufferedImage lower;
    private BufferedImage upper;

    public static void main(String[] args) throws Exception {

        new App(args).run();
    }

    public App(String[] args) {
        this.args = args;
    }

    public void run() throws Exception {
        log.info("Running");

        pi4j = Pi4J.newAutoContext();

        Signal.handle(new Signal("INT"), new SignalHandler() {

            public void handle(Signal sig) {
                log.info("Performing ctl-C shutdown");
                try {
                    pi4j.shutdown();
                } catch (LifecycleException e) {
                    e.printStackTrace();
                }
                System.exit(1);
            }
        });

        final DigitalOutputProvider digitalOutputProvider = pi4j.provider("pigpio-digital-output");

        SpiConfig spi_config = Spi.newConfigBuilder(pi4j).id("Adafruit3787").name("Display").bus(SpiBus.BUS_0)
                .chipSelect(SpiChipSelect.CS_0).baud(24000000).mode(SpiMode.MODE_0).build();

        SpiProvider spiProvider = pi4j.provider("pigpio-spi");

        try (Spi spi = spiProvider.create(spi_config)) {

            DigitalOutputConfig bl_config = DigitalOutput.newConfigBuilder(pi4j).address(18).build();
            bl = digitalOutputProvider.create(bl_config);
            bl.on();

            // used to indicate which is being sent: data vs command
            DigitalOutputConfig dc_config = DigitalOutput.newConfigBuilder(pi4j).address(25).build();
            dc = digitalOutputProvider.create(dc_config);

            Adafruit3787 display = new Adafruit3787(spi, dc);

            iris = ImageIO.read(getClass().getClassLoader().getResourceAsStream("defaultEye/iris.png"));

            sclera = ImageIO.read(getClass().getClassLoader().getResourceAsStream("defaultEye/sclera.png"));

            lower = ImageIO
                    .read(getClass().getClassLoader().getResourceAsStream("defaultEye/lid-lower-symmetrical.png"));

            upper = ImageIO
                    .read(getClass().getClassLoader().getResourceAsStream("defaultEye/lid-upper-symmetrical.png"));

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

                    display.display(drawEye(x, y, pupil));
                }

                int randomSleep = random.nextInt(2000);
                Utils.delay(Duration.ofMillis(randomSleep));

                startX = randomX;
                startY = randomY;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // should this be delta from center or absolute ??
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

        // g2d.setPaint(Color.red);

        // java.awt.Rectangle r = new java.awt.Rectangle(0, 0, iris.getWidth(), iris.getHeight());
        // java.awt.TexturePaint tp = new java.awt.TexturePaint(iris, r);
        // g2d.setPaint(tp);

        java.awt.geom.Point2D topLeft = new java.awt.geom.Point2D.Float(12, 12);
        float rad = 5;
        float[] dist = { 0.0f, 0.2f, 1.0f };
        Color[] colors = { Color.RED, Color.WHITE, Color.BLUE };
        java.awt.RadialGradientPaint rgp = new java.awt.RadialGradientPaint(topLeft, rad, dist, colors);
        g2d.setPaint(rgp);
        g2d.fillOval(120 - radius + x, 120 - radius + y, radius * 2, radius * 2);

        // g2d.setPaint(Color.black);

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
