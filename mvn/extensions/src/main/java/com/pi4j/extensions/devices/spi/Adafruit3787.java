package com.pi4j.extensions.drivers.spi;

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

import com.pi4j.extensions.LedColor;

public class Adafruit3787 {

    private static Logger log = LoggerFactory.getLogger(Adafruit3787.class);

    // TODO - check for it during runtime
    // cat /sys/module/spidev/parameters/bufsiz
    // OS Update needs
    // /boot/firmware/cmdline.txt
    // spidev.bufsiz=115200
    // Maynot be needed after all see: https://github.com/Pi4J/pi4j/issues/475

    private com.pi4j.driver.display.st7789.St7789Driver driver;
    private com.pi4j.driver.display.BaseGraphicsDisplayComponent graphics;

    private final int BITS_PER_PIXEL = 16;
    private final int WIDTH = 240;
    private final int HEIGHT = 240;

    private final byte[] image = new byte[WIDTH * HEIGHT * BITS_PER_PIXEL / 8];

    public Adafruit3787(Spi spi, DigitalOutput dc) {

        driver = new com.pi4j.driver.display.st7789.St7789Driver(spi, dc, com.pi4j.driver.display.PixelFormat.RGB_565);
        graphics = new com.pi4j.driver.display.BaseGraphicsDisplayComponent(driver);
    }

    public void display(BufferedImage img) throws Exception {

        log.debug("display: " + img.getType() + " " + img.getWidth() + " x " + img.getHeight());

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

                    int alpha = 0;
                    int blue = 0;
                    int green = 0;
                    int red = 0;

                    if (BufferedImage.TYPE_3BYTE_BGR == img.getType()) {
                        blue = 0xff & pixels[pos++];
                        green = 0xff & pixels[pos++];
                        red = 0xff & pixels[pos++];
                    } else if (BufferedImage.TYPE_BYTE_GRAY == img.getType()) {
                        int grayPos = (y * img.getWidth()) + x;

                        blue = 0xff & pixels[grayPos];
                        green = 0xff & pixels[grayPos];
                        red = 0xff & pixels[grayPos];

                    } else {
                        alpha = 0xff & pixels[pos++];

                        blue = 0xff & pixels[pos++];
                        green = 0xff & pixels[pos++];
                        red = 0xff & pixels[pos++];
                    }

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
        graphics.fillRect(0, 0, WIDTH, HEIGHT, ledColor);
    }

    public void pixel(int x, int y, int ledColor) throws Exception {

        int red = LedColor.getRedComponent(ledColor);
        int green = LedColor.getGreenComponent(ledColor);
        int blue = LedColor.getBlueComponent(ledColor);

        final int value = calculatePixelColor(red, green, blue);

        byte[] bytes = new byte[2];
        bytes[0] = (byte) (value >> 8);
        bytes[1] = (byte) value;

        driver.setPixels(x, y, 1, 1, bytes);
    }

    private void showImage() throws IOException {
        driver.setPixels(0, 0, WIDTH, HEIGHT, image);
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
}
