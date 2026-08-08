package com.pi4j.extensions;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisplayUtils {

    private static Logger log = LoggerFactory.getLogger(DisplayUtils.class);

    public static int[] createAlignmentPattern(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, w, h);

        drawGrid(g2, w, h, 40, 1, new Color(80, 80, 80));
        drawCenterCircles(g2, w, h, new Color(0, 200, 0), 2, 5, 60);
        drawCenterLines(g2, w, h, 4, Color.RED);
        drawTickMarks(g2, w, h, 10, 30, 3, Color.YELLOW);

        g2.dispose();
        return img.getRGB(0, 0, w, h, null, 0, w);
    }

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

	    /**
     * Draws a handful of concentric circles centered on the intersection
     * of the crosshair (i.e. the true center of the image).
     *
     * @param color       stroke color
     * @param thickness   stroke thickness
     * @param count       number of circles
     * @param ringSpacing radius increment between successive circles
     */
    public static void drawCenterCircles(Graphics2D g2, int w, int h, Color color, int thickness, int count,
            int ringSpacing) {
        int cx = w / 2;
        int cy = h / 2;
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));

        for (int i = 1; i <= count; i++) {
            int radius = i * ringSpacing;
            g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        }
    }

	/**
     * Draws a thick horizontal and vertical line through the exact center
     * of the image.
     */
    public static void drawCenterLines(Graphics2D g2, int w, int h, int thickness, Color color) {
        int cx = w / 2;
        int cy = h / 2;
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        // Horizontal center line
        g2.drawLine(0, cy, w, cy);
        // Vertical center line
        g2.drawLine(cx, 0, cx, h);
    }

public static void drawGrid(Graphics2D g2, int w, int h, int spacing, int thickness, Color color) {
        int cx = w / 2;
        int cy = h / 2;
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));

        // Vertical grid lines, radiating out from center
        for (int x = cx; x < w; x += spacing) {
            g2.drawLine(x, 0, x, h);
        }
        for (int x = cx; x >= 0; x -= spacing) {
            g2.drawLine(x, 0, x, h);
        }

        // Horizontal grid lines, radiating out from center
        for (int y = cy; y < h; y += spacing) {
            g2.drawLine(0, y, w, y);
        }
        for (int y = cy; y >= 0; y -= spacing) {
            g2.drawLine(0, y, w, y);
        }
    }

	/**
     * Draws alignment tick marks at the four corners and at the midpoint
     * of each edge. Useful for checking overscan/underscan in addition to
     * centering, since ticks near the physical edge of the image reveal
     * whether the visible screen area is cropping the picture.
     *
     * @param inset  distance in pixels from the true edge to start the ticks
     *               (keeps them visible even if a display slightly overscans)
     * @param length length of each tick mark, in pixels
     */
    public static void drawTickMarks(Graphics2D g2, int w, int h, int inset, int length, int thickness,
            Color color) {
        int cx = w / 2;
        int cy = h / 2;
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));

        // --- Corner ticks (an "L" shape at each corner) ---
        // Top-left
        g2.drawLine(inset, inset, inset + length, inset);
        g2.drawLine(inset, inset, inset, inset + length);
        // Top-right
        g2.drawLine(w - inset, inset, w - inset - length, inset);
        g2.drawLine(w - inset, inset, w - inset, inset + length);
        // Bottom-left
        g2.drawLine(inset, h - inset, inset + length, h - inset);
        g2.drawLine(inset, h - inset, inset, h - inset - length);
        // Bottom-right
        g2.drawLine(w - inset, h - inset, w - inset - length, h - inset);
        g2.drawLine(w - inset, h - inset, w - inset, h - inset - length);

        // --- Edge midpoint ticks (perpendicular to the edge) ---
        // Top edge
        g2.drawLine(cx, inset, cx, inset + length);
        // Bottom edge
        g2.drawLine(cx, h - inset, cx, h - inset - length);
        // Left edge
        g2.drawLine(inset, cy, inset + length, cy);
        // Right edge
        g2.drawLine(w - inset, cy, w - inset - length, cy);
    }
}
