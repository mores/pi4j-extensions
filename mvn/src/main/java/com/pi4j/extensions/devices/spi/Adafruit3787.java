package com.pi4j.extensions.devices.spi;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.IOException;

import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.spi.Spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.extensions.components.LedColor;
import com.pi4j.extensions.Utils;

public class Adafruit3787 {

    private static Logger log = LoggerFactory.getLogger(Adafruit3787.class);

    private final int BITS_PER_PIXEL = 16;
    private final int OFFSET = 80;
    private final int WIDTH = 240;
    private final int HEIGHT = 240;

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
        cols[2] = (byte) (WIDTH >> 8);
        cols[3] = (byte) (WIDTH & 0xff);
        data(cols);

        command(RASET); // Row addr set
        byte[] rows = new byte[4];
        rows[0] = 0x00;
        rows[1] = 0x50;
        rows[2] = (byte) ((OFFSET + HEIGHT) >> 8);
        rows[3] = (byte) ((OFFSET + HEIGHT) & 0xff);
        data(rows);

        command(INVON);

        command(NORON);

        command(DISPON);

        command(MADCTL);
        data(0xC0);

    }

    private void command(int x) throws com.pi4j.io.exception.IOException, IOException {

        if (x < 0 || x > 0xff) {
            throw new IllegalArgumentException("ST7789 bad command value " + x);
        }

        log.trace("Command: " + x);

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

        String raw = org.apache.commons.codec.binary.Hex.encodeHexString(x);
        if (raw.length() > 100) {
            log.trace("Data: " + x.length + " " + raw.substring(0, 80));
        } else {
            log.trace("Data: " + x.length + " " + raw);
        }

        dc.on();
        spi.write(x);
        dc.off();
    }

    public void display(BufferedImage img) throws Exception {

        log.debug("display: " + img.getWidth() + " x " + img.getHeight());

        DataBuffer dataBuffer = img.getRaster().getDataBuffer();

        if (dataBuffer instanceof DataBufferByte) {

            byte[] pixels = ((DataBufferByte) dataBuffer).getData();

            boolean hasAlphaChannel = img.getAlphaRaster() != null;
            int pixelLength = 3;
            if (hasAlphaChannel) {
                pixelLength = 4;
            }

            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    int pos = (y * pixelLength * img.getWidth()) + (x * pixelLength);

                    int alpha = 0xff & pixels[pos++];
                    int blue = 0xff & pixels[pos++];
                    int green = 0xff & pixels[pos++];
                    int red = 0xff & pixels[pos++];

                    if (x < WIDTH && y < HEIGHT) {
                        updateImage(x, y, red, green, blue);
                    }
                }
            }
            showImage();
        } else if (dataBuffer instanceof DataBufferInt) {
            int[] pixels = ((DataBufferInt) dataBuffer).getData();

            for (int x = 0; x < img.getWidth(); x++) {
                for (int y = 0; y < img.getHeight(); y++) {

                    int i = x + y * img.getWidth();
                    int alpha = (pixels[i] >> 24) & 0xff;
                    int red = (pixels[i] >> 16) & 0xff;
                    int green = (pixels[i] >> 8) & 0xff;
                    int blue = (pixels[i] >> 0) & 0xff;

                    if (x < WIDTH && y < HEIGHT) {
                        updateImage(x, y, red, green, blue);
                    }

                }
            }
            showImage();
        } else {
            log.warn("Unable to display BufferedImage DataBufferType: " + dataBuffer.getClass());
        }
    }

    public void fill(int ledColor) throws Exception {

        for (int x = 0; x < WIDTH; ++x) {
            for (int y = 0; y < HEIGHT; ++y) {

                updateImage(x, y, LedColor.getRedComponent(ledColor), LedColor.getGreenComponent(ledColor),
                        LedColor.getBlueComponent(ledColor));
            }
        }
        showImage();

    }

    public void pixel(int x, int y, int ledColor) throws Exception {

        command(CASET); // Column addr set
        byte[] cols = new byte[4];
        cols[0] = 0x00;
        cols[1] = (byte) x;
        cols[2] = 0x00;
        cols[3] = (byte) x;
        data(cols);

        command(RASET); // Row addr set
        byte[] rows = new byte[4];
        rows[0] = (byte) ((OFFSET + y) >> 8);
        rows[1] = (byte) ((OFFSET + y) & 0xff);
        rows[2] = (byte) ((OFFSET + y) >> 8);
        rows[3] = (byte) ((OFFSET + y) & 0xff);
        data(rows);

        int red = LedColor.getRedComponent(ledColor);
        int green = LedColor.getGreenComponent(ledColor);
        int blue = LedColor.getBlueComponent(ledColor);

        final int value = calculatePixelColor(red, green, blue);

        command(RAMWR); // write to RAM
        byte[] bytes = new byte[2];
        bytes[0] = (byte) (value >> 8);
        bytes[1] = (byte) value;
        data(bytes);
    }

    private void updateImage(int x, int y, int r, int g, int b) {

        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            throw new IllegalArgumentException("Invalid Pixel [" + x + "," + y + "]");
        }

        final int index = ((y * WIDTH) + x) * 2;

        final int value = calculatePixelColor(r, g, b);

        image[index] = (byte) (value >> 8);
        image[index + 1] = (byte) value;
    }

    private int calculatePixelColor(int r, int g, int b) {

        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException("Invalid Colour (" + r + "," + g + "," + b + ")");
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
        return value;

    }

    private void showImage() throws IOException {

        log.trace("window");
        command(CASET); // Column addr set
        byte[] cols = new byte[4];
        cols[0] = 0x00;
        cols[1] = 0x00;
        cols[2] = (byte) (WIDTH - 1 >> 8);
        cols[3] = (byte) (WIDTH - 1 & 0xff);
        data(cols);

        command(RASET); // Row addr set
        byte[] rows = new byte[4];
        rows[0] = 0x00;
        rows[1] = 0x50;
        rows[2] = (byte) ((OFFSET + HEIGHT - 1) >> 8);
        rows[3] = (byte) ((OFFSET + HEIGHT - 1) & 0xff);
        data(rows);

        command(RAMWR); // write to RAM
        data(image);
    }
}
