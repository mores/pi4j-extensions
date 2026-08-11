package com.pi4j.uncannyEyes;

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

public class App {

    private static Logger log = LoggerFactory.getLogger(App.class);

    private String[] args;

    private static Context pi4j;
    private Spi spi0;
    private Spi spi1;

    private DigitalOutput bl;

    private DigitalOutput dc0;
    private DigitalOutput dc1;

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

        pi4j.providers().describe().print(System.out);

        final DigitalOutputProvider digitalOutputProvider = pi4j.provider("ffm-digital-output");

        SpiConfig spi_config0 = Spi.newConfigBuilder(pi4j).id("Swift0").name("Display0").bus(SpiBus.BUS_0)
                .chipSelect(SpiChipSelect.CS_0).baud(24000000).mode(SpiMode.MODE_0).build();

        SpiConfig spi_config1 = Spi.newConfigBuilder(pi4j).id("Swift1").name("Display1").bus(SpiBus.BUS_1)
                .chipSelect(SpiChipSelect.CS_0).baud(24000000).mode(SpiMode.MODE_0).build();

        SpiProvider spiProvider = pi4j.provider("ffm-spi");

        try (Spi spi0 = spiProvider.create(spi_config0);
                Spi spi1 = spiProvider.create(spi_config1);) {

            // used to indicate which is being sent: data vs command
            DigitalOutputConfig dc_config0 = DigitalOutput.newConfigBuilder(pi4j).address(25).build();
            dc0 = digitalOutputProvider.create(dc_config0);

            DigitalOutputConfig dc_config1 = DigitalOutput.newConfigBuilder(pi4j).address(16).build();
            dc1 = digitalOutputProvider.create(dc_config1);

            St7789Driver driver0 = new St7789Driver(spi0, dc0, 240,
                    com.pi4j.drivers.display.graphics.PixelFormat.RGB_444);
            St7789Driver driver1 = new St7789Driver(spi1, dc1, 240,
                    com.pi4j.drivers.display.graphics.PixelFormat.RGB_444);

            // Left
            com.pi4j.drivers.display.graphics.GraphicsDisplay graphicsDisplay0 = new com.pi4j.drivers.display.graphics.GraphicsDisplay(
                    240, 240);
            // positive right , positive down
            graphicsDisplay0.attachDriver(1, 20, driver0,
                    com.pi4j.drivers.display.graphics.GraphicsDisplay.Rotation.ROTATE_180);

            // Right
            com.pi4j.drivers.display.graphics.GraphicsDisplay graphicsDisplay1 = new com.pi4j.drivers.display.graphics.GraphicsDisplay(
                    240, 240);
            graphicsDisplay1.attachDriver(0, 0, driver1,
                    com.pi4j.drivers.display.graphics.GraphicsDisplay.Rotation.ROTATE_180);

            com.mores.uncanny.UncannyEyesEngine engine = com.mores.uncanny.UncannyEyesEngine.builder()
                    .addDisplay(graphicsDisplay0.getGraphics()).addDisplay(graphicsDisplay1.getGraphics()).build();
            engine.start();

            while (1 == 1) {
                Thread.yield();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
