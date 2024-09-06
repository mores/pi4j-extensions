package com.pi4j.extensions.devices.i2c;

import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

import com.adafruit.Seesaw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Adafruit5880 {

    private static Logger log = LoggerFactory.getLogger(Adafruit5880.class);

    private I2C rotary;
    private int pins;

    public Adafruit5880(Context pi4j, int address) {

        I2CConfig i2cConfig = I2C.newConfigBuilder(pi4j).id("Adafruit5880").bus(1).device(address).build();
        I2CProvider i2CProvider = pi4j.provider("linuxfs-i2c");

        rotary = i2CProvider.create(i2cConfig);

        int pin = 24;
        pins = 1 << pin;

        try {

            byte chipId = Seesaw.read8(rotary, Seesaw.STATUS_BASE, Seesaw.STATUS_HW_ID);
            log.info("chipId: " + chipId);

            java.nio.ByteBuffer version = Seesaw.read(rotary, Seesaw.STATUS_BASE, Seesaw.STATUS_VERSION, 4);

            byte[] data = new byte[6];
            data[0] = (byte) Seesaw.ENCODER_BASE;
            data[1] = (byte) Seesaw.ENCODER_POSITION;

            data[5] = (byte) 0x00;
            data[4] = (byte) 0x00;
            data[3] = (byte) 0x00;
            data[2] = (byte) 0x00;
            rotary.write(data);

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

}
