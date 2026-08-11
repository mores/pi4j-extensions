package com.mores.control;

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

import com.pi4j.drivers.display.graphics.GraphicsDisplay;
import com.pi4j.drivers.display.graphics.st7789.St7789Driver;

import com.pi4j.extensions.Utils;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.exception.LifecycleException;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalOutputConfig;
import com.pi4j.io.gpio.digital.DigitalOutputProvider;
import com.pi4j.io.spi.Spi;
import com.pi4j.io.spi.SpiBus;
import com.pi4j.io.spi.SpiChipSelect;
import com.pi4j.io.spi.SpiConfig;
import com.pi4j.io.spi.SpiMode;
import com.pi4j.io.spi.SpiProvider;
import com.pi4j.drivers.display.graphics.GraphicsDisplay;
import com.pi4j.drivers.display.graphics.PixelFormat;
import lombok.extern.slf4j.Slf4j;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.List;

public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    private static Context pi4j;
    private static DigitalOutput dc0;
    private static DigitalOutput dc1;

    public static void main(String[] args) throws Exception {
        new Main().run();
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

        pi4j.providers().describe().print(System.out);

        final DigitalOutputProvider digitalOutputProvider = pi4j.provider("ffm-digital-output");

        SpiConfig spiConfig0 = Spi.newConfigBuilder(pi4j).id("Swift0").name("Display0").bus(SpiBus.BUS_0)
                .chipSelect(SpiChipSelect.CS_0).baud(24000000).mode(SpiMode.MODE_0).build();
        SpiConfig spiConfig1 = Spi.newConfigBuilder(pi4j).id("Swift1").name("Display1").bus(SpiBus.BUS_1)
                .chipSelect(SpiChipSelect.CS_0).baud(24000000).mode(SpiMode.MODE_0).build();

        SpiProvider spiProvider = pi4j.provider("ffm-spi");

        try (Spi spi0 = spiProvider.create(spiConfig0);
                Spi spi1 = spiProvider.create(spiConfig1)) {

            DigitalOutputConfig dcConfig0 = DigitalOutput.newConfigBuilder(pi4j).address(25).build();
            dc0 = digitalOutputProvider.create(dcConfig0);
            DigitalOutputConfig dcConfig1 = DigitalOutput.newConfigBuilder(pi4j).address(16).build();
            dc1 = digitalOutputProvider.create(dcConfig1);

            St7789Driver driver0 = new St7789Driver(spi0, dc0, 240, PixelFormat.RGB_444);
            St7789Driver driver1 = new St7789Driver(spi1, dc1, 240, PixelFormat.RGB_444);

            int width = 240;
            int height = 240;

            // Read ~/.uncannyEyes (if present) for the mode and per-eye nudge offsets left over from the last run.
            // Missing/corrupt file -> AppConfig.load() falls back to empty, so the hardcoded defaults below still
            // apply exactly as before.
            AppConfig config = AppConfig.load();

            int[] leftOffset = config.getOffset("Left Eye");
            int leftX = leftOffset != null ? leftOffset[0] : 10;
            int leftY = leftOffset != null ? leftOffset[1] : 10;

            int[] rightOffset = config.getOffset("Right Eye");
            int rightX = rightOffset != null ? rightOffset[0] : -10;
            int rightY = rightOffset != null ? rightOffset[1] : -10;

            // Left eye
            DisplayChannel<St7789Driver> channel0 = new DisplayChannel<>("Left Eye", driver0, width, height, leftX,
                    leftY, GraphicsDisplay.Rotation.ROTATE_180);

            // Right eye
            DisplayChannel<St7789Driver> channel1 = new DisplayChannel<>("Right Eye", driver1, width, height, rightX,
                    rightY, GraphicsDisplay.Rotation.ROTATE_180);

            // Mode (and therefore whether UNCANNY_EYES is active) is shared across both eyes -- they are not
            // independent, so this is one controller, not one per channel. Falls back to a static pattern if nothing
            // was saved yet; from then on every mode switch and nudge (via the TUI) is saved back to
            // ~/.uncannyEyes by the controller.
            DisplayGroupController controller = new DisplayGroupController(List.of(channel0, channel1),
                    config.getMode(DisplayMode.TEST_PATTERN), config);

            ControllerApp controllerApp = new ControllerApp(controller);
            controllerApp.run(); // blocks until you quit the TUI

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                pi4j.shutdown();
            } catch (LifecycleException e) {
                e.printStackTrace();
            }
        }
    }
}
