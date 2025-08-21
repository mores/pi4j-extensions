package com.pi4j.extensions.devices.i2c;

import java.time.Duration;

import com.pi4j.io.i2c.I2C;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.extensions.Utils;

/*
 * Controller AIP31068L & AIP31065
 */

public class Jhd1804 {

    private static Logger log = LoggerFactory.getLogger(Jhd1804.class);

    private static final byte LCD_CLEAR_DISPLAY = (byte) 0x01;
    private static final byte LCD_RETURN_HOME = (byte) 0x02;
    private static final byte LCD_DISPLAY_CONTROL = (byte) 0x08;
    // flags for display on/off control
    private static final byte LCD_DISPLAY_ON = (byte) 0x04;

    private I2C device;

    public Jhd1804(I2C device) {

        this.device = device;

    }

    private void textCommand(int cmd) throws Exception {
        device.writeRegister(0x80, (byte) cmd);
    }

    public void setText(String text) throws Exception {

        textCommand(LCD_CLEAR_DISPLAY);
        Thread.sleep(50);
        textCommand(LCD_DISPLAY_CONTROL | LCD_DISPLAY_ON);
        textCommand(0x28); // 2 lines
        Thread.sleep(50);

        int count = 0;
        int row = 0;

        for (char c : text.toCharArray()) {
            if (c == '\n' || count == 16) {
                count = 0;
                row++;
                if (row == 2) {
                    break;
                }
                textCommand(0xC0); // move to second line
                if (c == '\n') {
                    continue;
                }
            }
            count++;
            device.writeRegister(0x40, (byte) c);
        }
    }

    public void setTextNoRefresh(String text) throws Exception {

        textCommand(LCD_RETURN_HOME);
        Thread.sleep(50);
        textCommand(LCD_DISPLAY_CONTROL | LCD_DISPLAY_ON);
        textCommand(0x28); // 2 lines
        Thread.sleep(50);

        int count = 0;
        int row = 0;

        // pad text to 32 chars (16 per line, 2 lines)
        while (text.length() < 32) {
            text += " ";
        }

        for (char c : text.toCharArray()) {
            if (c == '\n' || count == 16) {
                count = 0;
                row++;
                if (row == 2) {
                    break;
                }
                textCommand(0xC0); // move to second line
                if (c == '\n') {
                    continue;
                }
            }
            count++;
            device.writeRegister(0x40, (byte) c);
        }
    }
}
