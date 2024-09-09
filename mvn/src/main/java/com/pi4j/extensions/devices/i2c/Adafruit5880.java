package com.pi4j.extensions.devices.i2c;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

import com.adafruit.Seesaw;
import com.pi4j.extensions.components.LedColor;
import com.pi4j.extensions.events.PositionEvent;
import com.pi4j.extensions.events.PressEvent;

import org.greenrobot.eventbus.EventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Adafruit5880 {

    private static Logger log = LoggerFactory.getLogger(Adafruit5880.class);

    private I2C rotary;
    private int pins;

    private int position;
    private boolean pressed;

    public Adafruit5880(Context pi4j, int address) {

        I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("Adafruit5880").bus(1).device(address).build();
        I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");

        rotary = i2CProvider.create(i2cConfig);

        pins = 1 << 24;

        try {

            byte chipId = Seesaw.read8(rotary, Seesaw.STATUS_BASE, Seesaw.STATUS_HW_ID);
            log.info("chipId: " + chipId);

            java.nio.ByteBuffer version = Seesaw.read(rotary, Seesaw.STATUS_BASE, Seesaw.STATUS_VERSION, 4);

            byte[] pin = new byte[3];
            pin[0] = (byte) Seesaw.NEOPIXEL_BASE;
            pin[1] = (byte) Seesaw.NEOPIXEL_PIN;
            pin[2] = (byte) 0x06;
            writeIt(pin);

            byte[] bufLength = new byte[4];
            bufLength[0] = (byte) Seesaw.NEOPIXEL_BASE;
            bufLength[1] = (byte) Seesaw.NEOPIXEL_BUF_LENGTH;
            bufLength[2] = (byte) 0x00;
            bufLength[3] = (byte) 0x03;
            writeIt(bufLength);

            byte[] data = new byte[6];
            data[0] = (byte) Seesaw.ENCODER_BASE;
            data[1] = (byte) Seesaw.ENCODER_POSITION;
            data[2] = (byte) 0x00;
            data[3] = (byte) 0x00;
            data[4] = (byte) 0x00;
            data[5] = (byte) 0x00;
            writeIt(data);

            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

            executor.scheduleAtFixedRate(() -> {
                CompletableFuture.runAsync(() -> {

                    try {
                        if (position != getPosition()) {
                            position = getPosition();
                            log.trace("New Position: " + position);
                            EventBus.getDefault().post(new PositionEvent(position));
                        }

                        if (pressed != isPressed()) {
                            pressed = isPressed();
                            log.trace("Pressed: " + pressed);
                            EventBus.getDefault().post(new PressEvent(pressed));
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                });
            }, 0, 50, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getPosition() throws Exception {
        return Seesaw.read(rotary, Seesaw.ENCODER_BASE, Seesaw.ENCODER_POSITION, 4).getInt();
    }

    public boolean isPressed() throws Exception {

        int button = Seesaw.read(rotary, Seesaw.GPIO_BASE, Seesaw.GPIO_BULK, 4).getInt();

        int result = button & pins;
        if (result == 0) {
            return true;
        }
        return false;
    }

    public void setPixel(int ledColor) throws Exception {

        byte[] msg = new byte[7];
        msg[0] = (byte) Seesaw.NEOPIXEL_BASE;
        msg[1] = (byte) Seesaw.NEOPIXEL_BUF;
        msg[2] = 0x00;
        msg[3] = 0x00;
        msg[4] = (byte) LedColor.getGreenComponent(ledColor);
        msg[5] = (byte) LedColor.getRedComponent(ledColor);
        msg[6] = (byte) LedColor.getBlueComponent(ledColor);
        writeIt(msg);

        byte[] show = new byte[2];
        show[0] = (byte) Seesaw.NEOPIXEL_BASE;
        show[1] = (byte) Seesaw.NEOPIXEL_SHOW;
        writeIt(show);

    }

    private void writeIt(byte[] data) {
        log.info(org.apache.commons.codec.binary.Hex.encodeHexString(data));

        rotary.write(data);
    }
}
