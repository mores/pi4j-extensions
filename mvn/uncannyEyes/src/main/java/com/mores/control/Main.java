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

            // Left eye
            GraphicsDisplay graphicsDisplay0 = new GraphicsDisplay(width, height);
            DisplayChannel<St7789Driver> channel0 = new DisplayChannel<>("Left Eye", graphicsDisplay0, driver0, width,
                    height, 1, 20, GraphicsDisplay.Rotation.ROTATE_180, DisplayMode.TEST_PATTERN // start safe; switch
                                                                                                 // to UNCANNY_EYES from
                                                                                                 // the TUI once aligned
            );

            // Right eye
            GraphicsDisplay graphicsDisplay1 = new GraphicsDisplay(width, height);
            DisplayChannel<St7789Driver> channel1 = new DisplayChannel<>("Right Eye", graphicsDisplay1, driver1, width,
                    height, 0, 0, GraphicsDisplay.Rotation.ROTATE_180, DisplayMode.TEST_PATTERN);

            ControllerApp controllerApp = new ControllerApp(List.of(channel0, channel1));
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
