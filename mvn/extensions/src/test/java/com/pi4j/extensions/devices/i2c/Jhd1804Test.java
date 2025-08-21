package com.pi4j.extensions.devices.i2c;

import java.time.Duration;

import com.pi4j.context.Context;
import com.pi4j.Pi4J;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.extensions.Utils;

public class Jhd1804Test {

    private static Logger log = LoggerFactory.getLogger(Jhd1804Test.class);

    private static Context pi4j;
    private Jhd1804 display;

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

        I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");
        I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("Jhd1804").bus(1).device(0x3e).build();

        I2C i2c = i2CProvider.create(i2cConfig);

        display = new Jhd1804(i2c);
        display.clear();
        Utils.delay(Duration.ofMillis(500));
        display.off();
        Utils.delay(Duration.ofMillis(500));
        display.setText("Hello world! " + java.time.LocalDateTime.now());
        Utils.delay(Duration.ofSeconds(5));
        for (int x = 0; x < 10; x++) {
            display.setTextNoRefresh("Hello world! " + java.time.LocalDateTime.now());
            Utils.delay(Duration.ofSeconds(1));
        }
    }
}
