package com.pi4j.drivers.multipurpose.piborg;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.input.KeyStroke;

import java.util.function.IntConsumer;

/**
 * A vertical, keyboard-driven slider for a terminal UI, standing in for the {@code
 * tkinter.Scale(orient=VERTICAL)} widgets used by PiBorg's original {@code ubTuningGui.py}.
 * <p>
 * The maximum value is drawn at the top of the track and the minimum at the bottom, matching the original GUI's
 * {@code from_ = CAL_PWM_MAX, to = CAL_PWM_MIN} sliders. While focused:
 * <ul>
 * <li>Up / Down arrow moves the value by one small step
 * <li>Page Up / Page Down moves the value by one large step
 * <li>Home / End jump to the maximum / minimum
 * </ul>
 * <p>
 * Any other key (e.g. Tab) is passed to the default {@link AbstractInteractableComponent} behaviour so focus navigation
 * keeps working.
 */
public class VerticalSlider extends AbstractInteractableComponent<VerticalSlider> {

    private static final int TRACK_WIDTH = 7;
    private static final int TRACK_HEIGHT = 12;

    private final int minValue;
    private final int maxValue;
    private final int smallStep;
    private final int largeStep;

    private int value;
    private IntConsumer onValueChanged;

    /**
     * @param minValue
     *            the value at the bottom of the track
     * @param maxValue
     *            the value at the top of the track
     * @param startValue
     *            the initial value, clamped to [minValue, maxValue]
     * @param smallStep
     *            how far Up/Down arrow keys move the value
     * @param largeStep
     *            how far Page Up/Page Down keys move the value
     */
    public VerticalSlider(int minValue, int maxValue, int startValue, int smallStep, int largeStep) {
        if (minValue >= maxValue) {
            throw new IllegalArgumentException("minValue must be less than maxValue");
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.smallStep = smallStep;
        this.largeStep = largeStep;
        this.value = clamp(startValue);
    }

    /** Returns the slider's current value. */
    public int getValue() {
        return value;
    }

    /** Sets the slider's current value without notifying the {@link #onChange(IntConsumer)} listener. */
    public void setValue(int newValue) {
        int clamped = clamp(newValue);
        if (clamped != value) {
            value = clamped;
            invalidate();
        }
    }

    /** Registers a listener that is called (on the GUI thread) whenever the user changes the value. */
    public VerticalSlider onChange(IntConsumer listener) {
        this.onValueChanged = listener;
        return this;
    }

    private int clamp(int v) {
        return Math.max(minValue, Math.min(maxValue, v));
    }

    private void changeValueBy(int delta) {
        changeValueTo(value + delta);
    }

    private void changeValueTo(int newValue) {
        int clamped = clamp(newValue);
        if (clamped != value) {
            value = clamped;
            invalidate();
            if (onValueChanged != null) {
                onValueChanged.accept(value);
            }
        }
    }

    @Override
    public synchronized Result handleKeyStroke(KeyStroke keyStroke) {
        switch (keyStroke.getKeyType()) {
            case ArrowUp:
                changeValueBy(smallStep);
                return Result.HANDLED;
            case ArrowDown:
                changeValueBy(-smallStep);
                return Result.HANDLED;
            case PageUp:
                changeValueBy(largeStep);
                return Result.HANDLED;
            case PageDown:
                changeValueBy(-largeStep);
                return Result.HANDLED;
            case Home:
                changeValueTo(maxValue);
                return Result.HANDLED;
            case End:
                changeValueTo(minValue);
                return Result.HANDLED;
            default:
                return super.handleKeyStroke(keyStroke);
        }
    }

    @Override
    protected InteractableRenderer<VerticalSlider> createDefaultRenderer() {
        return new InteractableRenderer<VerticalSlider>() {
            @Override
            public TerminalPosition getCursorLocation(VerticalSlider component) {
                // No text cursor; the highlighted knob below shows focus instead.
                return null;
            }

            @Override
            public TerminalSize getPreferredSize(VerticalSlider component) {
                return new TerminalSize(TRACK_WIDTH, TRACK_HEIGHT);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, VerticalSlider component) {
                TerminalSize size = graphics.getSize();
                int height = size.getRows();
                int width = size.getColumns();
                if (height <= 0 || width <= 0) {
                    return;
                }

                boolean focused = component.isFocused();
                double fraction = (double) (component.value - component.minValue)
                        / (component.maxValue - component.minValue);
                int knobRow = height - 1 - (int) Math.round(fraction * (height - 1));

                TextColor trackColor = focused ? TextColor.ANSI.CYAN : TextColor.ANSI.WHITE;
                TextColor knobColor = focused ? TextColor.ANSI.YELLOW : TextColor.ANSI.CYAN;
                int centerColumn = width / 2;

                graphics.setForegroundColor(trackColor);
                for (int row = 0; row < height; row++) {
                    graphics.putString(centerColumn, row, "\u2502"); // vertical bar: |
                }
                // Ticks at the very top and bottom make the min/max ends of the track obvious.
                graphics.putString(centerColumn, 0, "\u252c"); // top tee
                graphics.putString(centerColumn, height - 1, "\u2534"); // bottom tee

                graphics.setForegroundColor(knobColor);
                graphics.putString(0, knobRow, repeat("\u2550", width)); // knob bar: ===
            }
        };
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
