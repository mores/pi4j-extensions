package com.pi4j.extensions.devices.spi;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
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
import com.pi4j.extensions.components.LedColor;

public class Adafruit3787Test {

    private static Logger log = LoggerFactory.getLogger(Adafruit3787Test.class);

    private static Context pi4j;
    private Spi spi;

    DigitalOutput bl;
    DigitalOutput dc;
    DigitalOutput rst;

    public void setUp() {
        log.info("setUp");

        pi4j = Pi4J.newAutoContext();
    }

    public void tearDown() {
        log.info("tearDown");

        pi4j.shutdown();
    }

    public void testOne() throws Exception {
        log.info("testOne");

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

            display.fill(LedColor.RED);
            Utils.delay(Duration.ofMillis(1000));
            display.fill(LedColor.BLUE);
            Utils.delay(Duration.ofMillis(1000));
            display.fill(LedColor.GREEN);
            Utils.delay(Duration.ofMillis(1000));
            display.fill(LedColor.WHITE);
            Utils.delay(Duration.ofMillis(1000));

            for (int x = 0; x < 240; x++) {
                for (int y = 0; y < 240; y++) {
                    display.pixel(x, y, LedColor.BLACK);
                }
            }
            Utils.delay(Duration.ofMillis(1000));

            int w = 240;
            int h = 240;

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D g2d = img.createGraphics();

            g2d.setPaint(java.awt.Color.yellow);
            g2d.fillRect(0, 0, img.getWidth(), img.getHeight());

            g2d.setPaint(java.awt.Color.black);
            g2d.setFont(new Font("TimesRoman", Font.PLAIN, 28));
            g2d.drawString("Hello", 50, 50);

            g2d.dispose();

            display.display(img);
            Utils.delay(Duration.ofMillis(1000));

            BufferedImage img2 = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D graphics = img2.createGraphics();

            JFrame frame = new JFrame();

            JPanel panel = new JPanel();
            frame.add(panel);

            JButton hello = new JButton("Hello");
            hello.setBorder(getBorder(false));
            hello.setFocusable(false);
            panel.add(hello);

            JButton world = new JButton("World");
            world.setBorder(getBorder(false));
            world.setFocusable(false);
            panel.add(world);

            frame.setSize(240, 240);
            frame.setVisible(true);

            frame.paintAll(graphics);
            display.display(img2);
            Utils.delay(Duration.ofMillis(1000));

            hello.setBorder(getBorder(true));

            frame.paintAll(graphics);
            display.display(img2);
            Utils.delay(Duration.ofMillis(1000));

            world.setBorder(getBorder(true));
            hello.setBorder(getBorder(false));

            frame.paintAll(graphics);
            display.display(img2);
            Utils.delay(Duration.ofMillis(1000));

            world.setBorder(getBorder(false));

            frame.paintAll(graphics);
            display.display(img2);
            Utils.delay(Duration.ofMillis(1000));

            BufferedImage logo = ImageIO.read(getClass().getClassLoader().getResourceAsStream("pi4j.png"));
            display.display(logo);
            Utils.delay(Duration.ofMillis(1000));

            BufferedImage distorted = distortImg(logo);
            display.display(distorted);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Border getBorder(boolean active) {

        Border border1 = new LineBorder(java.awt.Color.black, 1);
        if (active) {
            border1 = new LineBorder(java.awt.Color.blue, 2);
        }

        EmptyBorder border2 = new EmptyBorder(5, 10, 5, 10);
        Border newBorder = BorderFactory.createCompoundBorder(border1, border2);

        return newBorder;
    }

    private BufferedImage distortImg(BufferedImage image) {

        // Necessary to initialize the JavaFX platform and to avoid "IllegalStateException: Toolkit not initialized"
        new JFXPanel();

        // This array allows us to get the distorted image out of the runLater closure below
        final BufferedImage[] imageContainer = new BufferedImage[1];

        // We use this latch to await the end of the JavaFX thread. Otherwise this method would finish before
        // the thread creates the distorted image
        final CountDownLatch latch = new CountDownLatch(1);

        // To avoid "IllegalStateException: Not on FX application thread" we start a JavaFX thread
        Platform.runLater(() -> {
            int width = image.getWidth();
            int height = image.getHeight();
            Canvas canvas = new Canvas(width, height);
            GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
            ImageView imageView = new ImageView(SwingFXUtils.toFXImage(image, null));

            PerspectiveTransform trans = new PerspectiveTransform();
            trans.setUlx(0);
            trans.setUly(height / 4);
            trans.setUrx(width);
            trans.setUry(0);
            trans.setLrx(width);
            trans.setLry(height);
            trans.setLlx(0);
            trans.setLly(height - height / 2);

            imageView.setEffect(trans);

            imageView.setRotate(2);

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.TRANSPARENT);

            Image newImage = imageView.snapshot(params, null);
            graphicsContext.drawImage(newImage, 0, 0);

            imageContainer[0] = SwingFXUtils.fromFXImage(newImage, image);
            // Work is done, we decrement the latch which we used for awaiting the end of this thread
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return imageContainer[0];
    }
}
