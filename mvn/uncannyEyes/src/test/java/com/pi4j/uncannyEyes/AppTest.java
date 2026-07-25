package com.pi4j.uncannyEyes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.drivers.display.graphics.*;
import com.pi4j.drivers.display.graphics.awt.JFrameGraphicsDriver;

import com.pi4j.extensions.DisplayUtils;

public class AppTest {
    private static Logger log = LoggerFactory.getLogger(AppTest.class);

    public void testOne() throws Exception {
        log.info("testOne");

        int width = 240;
        int height = 240;

        JFrameGraphicsDriver driver0 = new JFrameGraphicsDriver(width, height);
        GraphicsDisplay display0 = new GraphicsDisplay(driver0);
        Graphics graphics0 = display0.getGraphics();
        graphics0.drawRgb(0, 0, width, height, DisplayUtils.createTestPattern(width, height));

        JFrameGraphicsDriver driver1 = new JFrameGraphicsDriver(width, height);
        com.pi4j.drivers.display.graphics.GraphicsDisplay display1 = new com.pi4j.drivers.display.graphics.GraphicsDisplay(
                driver1);
        Graphics graphics1 = display1.getGraphics();

        com.pi4j.drivers.display.graphics.awt.JFrameGraphicsDriver driver2 = new com.pi4j.drivers.display.graphics.awt.JFrameGraphicsDriver(
                width, height);
        com.pi4j.drivers.display.graphics.GraphicsDisplay display2 = new com.pi4j.drivers.display.graphics.GraphicsDisplay(
                driver2);
        Graphics graphics2 = display2.getGraphics();

        com.mores.uncanny.UncannyEyesEngine engine = com.mores.uncanny.UncannyEyesEngine.builder().addDisplay(graphics1)
                .addDisplay(graphics2).build();
        engine.start();

        while (1 == 1) {
            Thread.yield();
        }
    }
}
