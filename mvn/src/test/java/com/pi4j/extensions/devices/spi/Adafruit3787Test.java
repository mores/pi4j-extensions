package com.pi4j.extensions.devices.spi;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;

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

            display.fill(LedColor.WHITE);
            Utils.delay(Duration.ofMillis(1000));
            display.fill(LedColor.BLUE);
            Utils.delay(Duration.ofMillis(1000));
            display.fill(LedColor.GREEN);
            Utils.delay(Duration.ofMillis(1000));
            display.fill(LedColor.RED);
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

            g2d.setPaint(Color.yellow);
            g2d.fillRect(0, 0, img.getWidth(), img.getHeight());

            g2d.setPaint(Color.black);
            g2d.setFont(new Font("TimesRoman", Font.PLAIN, 28));
            g2d.drawString("Hello", 50, 50);

            g2d.dispose();

            display.display(img);
            Utils.delay(Duration.ofMillis(2000));

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
            Utils.delay(Duration.ofMillis(2000));

            hello.setBorder(getBorder(true));

            frame.paintAll(graphics);
            display.display(img2);
            Utils.delay(Duration.ofMillis(2000));

            world.setBorder(getBorder(true));
            hello.setBorder(getBorder(false));

            frame.paintAll(graphics);
            display.display(img2);
            Utils.delay(Duration.ofMillis(2000));

            world.setBorder(getBorder(false));

            frame.paintAll(graphics);
            display.display(img2);
            Utils.delay(Duration.ofMillis(2000));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Border getBorder(boolean active) {

        Border border1 = new LineBorder(Color.black, 1);
        if (active) {
            border1 = new LineBorder(Color.blue, 2);
        }

        EmptyBorder border2 = new EmptyBorder(5, 10, 5, 10);
        Border newBorder = BorderFactory.createCompoundBorder(border1, border2);

        return newBorder;
    }
}
