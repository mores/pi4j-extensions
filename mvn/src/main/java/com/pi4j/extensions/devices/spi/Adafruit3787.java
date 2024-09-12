package com.pi4j.extensions.devices.spi;

import java.io.IOException;
import java.time.Duration;

import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.spi.Spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.extensions.Utils;

public class Adafruit3787 {

    private static Logger log = LoggerFactory.getLogger(Adafruit3787.class);

    private final int BITS_PER_PIXEL = 16;
    private final int WIDTH = 24;
    private final int HEIGHT = 24;

    private final byte[] image = new byte[WIDTH * HEIGHT * BITS_PER_PIXEL / 8];

    private static final int SWRESET = 0x01;
    private static final int SLPOUT = 0x11;
    private static final int NORON = 0x13;
    private static final int INVON = 0x21;
    private static final int DISPON = 0x29;
    private static final int CASET = 0x2A;
    private static final int RASET = 0x2B;
    private static final int RAMWR = 0x2C;
    private static final int MADCTL = 0x36;
    private static final int COLMOD = 0x3A;

    private Spi spi;
    private DigitalOutput dc;

    public Adafruit3787(Spi spi, DigitalOutput dc) {

        this.spi = spi;
        this.dc = dc;

        try {
            init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void init() throws Exception {

        command(SWRESET);

        command(SLPOUT);

        command(COLMOD);
        data(0x55);

        command(MADCTL);
        data(0x08);

        command(CASET); // Column addr set
        byte[] cols = new byte[4];
        cols[0] = 0x00;
        cols[1] = 0x00;
        cols[2] = 0x00;
        cols[3] = 0x18;
        data(cols);

        command(RASET); // Row addr set
        byte[] row = new byte[4];
        row[0] = 0x00;
        row[1] = 0x50;
        row[2] = 0x00;
        row[3] = 0x68;
        data(row);

        command(INVON);

        command(NORON);

        command(DISPON);

        command(MADCTL);
        data(0xC0);

    }

    private void command(int x) throws com.pi4j.io.exception.IOException, IOException {

        if (x < 0 || x > 0xff)
            throw new IllegalArgumentException("ST7789 bad command value " + x);

        dc.off();
        byte[] buffer = new byte[1];
        buffer[0] = (byte) x;
        spi.write(buffer);
    }

    private void data(int x) throws IOException, com.pi4j.io.exception.IOException {

        if (x < 0 || x > 0xff) {
            throw new IllegalArgumentException("ST7789 bad data value " + x);
        }

        byte[] buffer = new byte[1];
        buffer[0] = (byte) x;

        data(buffer);
    }

    private void data(byte[] x) throws IOException, com.pi4j.io.exception.IOException {

        log.info("Data: " + org.apache.commons.codec.binary.Hex.encodeHexString(x));

        dc.on();
        spi.write(x);
        dc.off();
    }

    public void flash() throws Exception {

        for (int x = 0; x < WIDTH; ++x) {
            for (int y = 0; y < HEIGHT; ++y) {
                setPixel(x, y, 255, 0, 0);
            }
        }
        show();
        Utils.delay(Duration.ofMillis(500));

        for (int x = 0; x < WIDTH; ++x) {
            for (int y = 0; y < HEIGHT; ++y) {
                setPixel(x, y, 255, 255, 0);
            }
        }
        show();
        Utils.delay(Duration.ofMillis(500));

        for (int x = 0; x < WIDTH; ++x) {
            for (int y = 0; y < HEIGHT; ++y) {
                setPixel(x, y, 0, 255, 0);
            }
        }
        show();
        Utils.delay(Duration.ofMillis(500));

        for (int x = 0; x < WIDTH; ++x) {
            for (int y = 0; y < HEIGHT; ++y) {
                setPixel(x, y, 0, 255, 255);
            }
        }
        show();
        Utils.delay(Duration.ofMillis(500));

        for (int x = 0; x < WIDTH; ++x) {
            for (int y = 0; y < HEIGHT; ++y) {
                setPixel(x, y, 0, 0, 255);
            }
        }
        show();
    }

    private void setPixel(int x, int y, int r, int g, int b) {

        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            throw new IllegalArgumentException("ST7789 Invalid Pixel [" + x + "," + y + "]");
        }

        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException("ST7789 Invalid Colour (" + r + "," + g + "," + b + ")");
        }

        if ((r & 0x04) != 0) {
            r += 0x04;

            if (r > 255) {
                r = 255;
            }
        }

        if ((g & 0x02) != 0) {
            g += 0x02;

            if (g > 255) {
                g = 255;
            }
        }

        if ((b & 0x04) != 0) {
            b += 0x04;

            if (b > 255) {
                b = 255;
            }
        }

        final int value = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);

        // This is really horrible. It will break on non-square displays!
        final int index = ((HEIGHT - 1 - y) + (WIDTH - 1 - x) * WIDTH) * 2;

        image[index] = (byte) (value >> 8);
        image[index + 1] = (byte) value;

    }

    private void show() throws IOException {

        window(0, 0, WIDTH - 1, HEIGHT - 1);
        data(0);
        data(image);
    }

    private void window(int x0, int y0, int x1, int y1) throws com.pi4j.io.exception.IOException, IOException {

        command(CASET); // Column addr set
        byte[] cols = new byte[4];
        cols[0] = 0x00;
        cols[1] = 0x00;
        cols[2] = 0x00;
        cols[3] = 0x17;
        data(cols);

        command(RASET); // Row addr set
        byte[] buf = new byte[4];
        buf[0] = 0x00;
        buf[1] = 0x50;
        buf[2] = 0x00;
        buf[3] = 0x67;
        data(buf);

        command(RAMWR); // write to RAM
    }
}
