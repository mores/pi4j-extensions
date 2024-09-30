package com.pi4j.extensions.devices.i2c;

import com.adafruit.Seesaw;

import java.time.Duration;

import com.pi4j.context.Context;
import com.pi4j.Pi4J;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.extensions.Utils;
import com.pi4j.extensions.components.LedColor;
import com.pi4j.extensions.events.PositionEvent;
import com.pi4j.extensions.events.PressEvent;

public class Adafruit5880Test {

    private static Logger log = LoggerFactory.getLogger(Adafruit5880Test.class);

    private static Context pi4j;
    private Adafruit5880 knob;

    public void setUp() {
        log.info("setUp");

        pi4j = Pi4J.newAutoContext();
        EventBus.getDefault().register(this);
    }

    public void tearDown() {
        log.info("tearDown");

        pi4j.shutdown();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPositionEvent(PositionEvent event) throws Exception {

        log.info("onPositionEvent: " + event.identity.getId() + "\t" + event.position);

        if (5 == event.position) {
            knob.setPixel(LedColor.RED);
        } else if (10 == event.position) {
            knob.setPixel(LedColor.BLUE);
        } else if (15 == event.position) {
            knob.setPixel(LedColor.ORANGE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPressEvent(PressEvent event) throws Exception {
        log.info("onPressEvent: " + event.identity.getId() + "\t" + event.pressed);
        knob.setPixel(LedColor.BLACK);
    }

    public void testOne() {
        log.info("testOne");

        I2CProvider i2CProvider = pi4j.provider("pigpio-i2c");
        I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("Adafruit5880").bus(1).device(0x36).build();

        I2C rotary = i2CProvider.create(i2cConfig);

        knob = new Adafruit5880(rotary);

        // Wait for 30 seconds while handling events before exiting
        log.info("Rotate the knob to see it in action!");
        Utils.delay(Duration.ofSeconds(30));

    }
}
