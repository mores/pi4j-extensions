package com.pi4j.extensions;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisplayUtils {

    private static Logger log = LoggerFactory.getLogger(DisplayUtils.class);

    public static int[] createTestPattern(int w, int h) {

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        // 1. Top section — 75 % colour bars (67 % of height)
        Color[] topColors = { new Color(192, 192, 192), // 75 % White
                new Color(192, 192, 0), // Yellow
                new Color(0, 192, 192), // Cyan
                new Color(0, 192, 0), // Green
                new Color(192, 0, 192), // Magenta
                new Color(192, 0, 0), // Red
                new Color(0, 0, 192) // Blue
        };

        int barWidth = w / 7;
        int topHeight = (int) (h * 0.67);

        int remainder = w - (barWidth * 7);
        log.info("Remainder: " + remainder);

        for (int i = 0; i < 7; i++) {
            g2.setColor(topColors[i]);

            int extra = 0;
            if (i == 6) {
                extra = remainder;
            }
            g2.fillRect(i * barWidth, 0, barWidth + extra, topHeight);
        }

        // 2. Middle section — reverse bars (8 % of height)
        int midHeight = (int) (h * 0.08);
        Color[] midColors = { Color.BLUE, Color.BLACK, Color.MAGENTA, Color.BLACK, Color.CYAN, Color.BLACK,
                Color.WHITE };

        for (int i = 0; i < 7; i++) {
            g2.setColor(midColors[i]);
            int extra = 0;
            if (i == 6) {
                extra = remainder;
            }
            g2.fillRect(i * barWidth, topHeight, barWidth + extra, midHeight);
        }

        // 3. Bottom section — PLUGE gradient
        int botY = topHeight + midHeight;
        int numBlocks = 8;
        int blockWidth = w / numBlocks;

        for (int i = 0; i < numBlocks; i++) {
            int gray = 255 - (i * 255) / (numBlocks - 1);
            g2.setColor(new Color(gray, gray, gray));
            int extra = 0;
            if (i == 7) {
                extra = remainder;
            }
            g2.fillRect(i * blockWidth, botY, blockWidth + extra, h);
        }

        g2.dispose();
        int[] rgb888pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        return rgb888pixels;
    }
}
