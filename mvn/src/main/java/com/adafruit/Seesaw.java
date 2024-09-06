package com.adafruit;

import java.nio.ByteBuffer;

import com.pi4j.io.i2c.I2C;

// port of https://github.com/adafruit/Adafruit_CircuitPython_seesaw/blob/main/adafruit_seesaw/seesaw.py
// additional info https://learn.adafruit.com/adafruit-seesaw-atsamd09-breakout/status
public class Seesaw {

    public static final short ENCODER_BASE = 0x11;
    public static final short ENCODER_INTENSET = 0x10;
    public static final short ENCODER_INTENCLR = 0x20;
    public static final short ENCODER_POSITION = 0x30;

    public static final short GPIO_BASE = 0x01;
    public static final short GPIO_BULK = 0x04;

    public static final short NEOPIXEL_BASE = 0x0E;
    public static final short NEOPIXEL_BUF = 0x04;
    public static final short NEOPIXEL_SHOW = 0x05;

    public static final short STATUS_BASE = 0x00;
    public static final short STATUS_HW_ID = 0x01;
    public static final short STATUS_VERSION = 0x02;

    public static byte read8(I2C dev, short register, short addr) throws Exception {
        dev.writeRegister((byte) register, (byte) addr);
        Thread.sleep(8);

        byte returnValue = (byte) dev.readRegister((byte) register);

        return returnValue;
    }

    public static ByteBuffer read(I2C dev, short register, short addr, int length) throws Exception {
        dev.writeRegister((byte) register, (byte) addr);
        Thread.sleep(8);

        return dev.readRegisterByteBuffer((byte) register, length);
    }
}
