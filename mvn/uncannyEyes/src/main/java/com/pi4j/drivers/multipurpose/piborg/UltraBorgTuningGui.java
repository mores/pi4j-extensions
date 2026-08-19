package com.pi4j.drivers.multipurpose.piborg;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.drivers.UltraBorgDriver;
import com.pi4j.drivers.UltraBorgDriver.Channel;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A Lanterna terminal UI for calibrating the four servo channels of a PiBorg UltraBorg, using {@link UltraBorgDriver}.
 * This is a terminal port of PiBorg's original Tkinter tool, {@code
 * ubTuningGui.py}, preserving its workflow:
 * <ol>
 * <li>Move a channel's slider to drive the servo directly and find the position you want.
 * <li>Press "Save maximum" / "Save startup" / "Save minimum" to persist that position to the board's EEPROM for that
 * limit.
 * <li>"Reset and save all to default values" restores every channel to the factory defaults.
 * </ol>
 * <p>
 * Run with the arrow keys / Page Up / Page Down / Home / End to move the highlighted slider, Tab / Shift+Tab to move
 * between controls, and Enter / Space to press a button.
 */
public class UltraBorgTuningGui {

    // Calibration slider range (2000 = 1ms burst, 4000 = 2ms burst), mirrors ubTuningGui.py.
    private static final int CAL_PWM_MIN = 0;
    private static final int CAL_PWM_MAX = 6000;
    private static final int CAL_PWM_START = 3000;
    private static final int CAL_SMALL_STEP = 50;
    private static final int CAL_LARGE_STEP = 250;

    // Default limits used by "Reset and save all to default values", mirrors ubTuningGui.py.
    private static final int STD_PWM_MIN = UltraBorgDriver.PWM_TYPICAL_MIN;
    private static final int STD_PWM_MAX = UltraBorgDriver.PWM_TYPICAL_MAX;
    private static final int STD_PWM_START = UltraBorgDriver.PWM_UNSET;

    private static final int POLL_INTERVAL_MS = 200;
    private static final int RETRY_COUNT = 5;

    private final UltraBorgDriver driver;
    private final Map<Channel, ChannelPanel> channelPanels = new EnumMap<>(Channel.class);

    private WindowBasedTextGUI gui;
    private ScheduledExecutorService pollExecutor;

    public UltraBorgTuningGui(UltraBorgDriver driver) {
        this.driver = driver;
    }

    public static void main(String[] args) throws Exception {
        Context pi4j = Pi4J.newAutoContext();
        try {
            I2CProvider i2CProvider = pi4j.provider("ffm-i2c");
            I2CConfig config = I2C.newConfigBuilder(pi4j).id("UltraBorg").bus(1).device(UltraBorgDriver.DEFAULT_ADDRESS)
                    .build();

            try (I2C i2c = i2CProvider.create(config);
                    UltraBorgDriver driver = new UltraBorgDriver(i2c)) {
                new UltraBorgTuningGui(driver).run();
            }
        } finally {
            pi4j.shutdown();
        }
    }

    /** Builds and runs the terminal UI. Blocks until the user quits. */
    public void run() throws IOException {
        Screen screen = new DefaultTerminalFactory().createScreen();
        screen.startScreen();
        try {
            gui = new MultiWindowTextGUI(screen);

            BasicWindow window = new BasicWindow("UltraBorg Tuning GUI");
            window.setHints(Collections.singletonList(Window.Hint.FULL_SCREEN));
            window.setComponent(buildRootPanel(window));

            startPolling();
            gui.addWindowAndWait(window);
        } finally {
            stopPolling();
            screen.stopScreen();
        }
    }

    private Panel buildRootPanel(BasicWindow window) {
        Panel root = new Panel(new BorderLayout());

        Label help = new Label(
                "Up/Down move the highlighted slider, Page Up/Down for bigger steps, Home/End for max/min.\n"
                        + "Tab / Shift+Tab moves between controls. Each slider's number is the raw calibration\n"
                        + "burst sent live to that servo. \"Current position\" is read back from the board.");
        root.addComponent(help, BorderLayout.Location.TOP);

        Panel servoRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        for (Channel channel : Channel.values()) {
            ChannelPanel channelPanel = new ChannelPanel(channel);
            channelPanels.put(channel, channelPanel);
            servoRow.addComponent(channelPanel.getRootComponent());
        }
        root.addComponent(servoRow, BorderLayout.Location.CENTER);

        Panel southPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        southPanel.addComponent(new Button("Reset and save all to default values", this::onReset));
        southPanel.addComponent(new Button("Quit", window::close));
        root.addComponent(southPanel, BorderLayout.Location.BOTTOM);

        readAllCalibration();
        return root;
    }

    private void readAllCalibration() {
        for (ChannelPanel panel : channelPanels.values()) {
            panel.readCalibration();
        }
    }

    private void onReset() {
        for (ChannelPanel panel : channelPanels.values()) {
            panel.resetToDefaults();
        }
    }

    // -- Polling for live servo position ---------------------------------------------------------

    private void startPolling() {
        pollExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ultraborg-poll");
            thread.setDaemon(true);
            return thread;
        });
        pollExecutor.scheduleWithFixedDelay(this::pollOnce, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (pollExecutor != null) {
            pollExecutor.shutdownNow();
        }
    }

    // Runs on the poll thread: does the blocking I2C reads, then hands the results to the GUI
    // thread. Never touch Lanterna components directly from this thread.
    private void pollOnce() {
        for (Map.Entry<Channel, ChannelPanel> entry : channelPanels.entrySet()) {
            Channel channel = entry.getKey();
            ChannelPanel panel = entry.getValue();

            Integer position;
            try {
                position = driver.getRawPwm(channel);
            } catch (IOException e) {
                position = null;
            }

            Integer finalPosition = position;
            WindowBasedTextGUI textGui = gui;
            if (textGui != null) {
                textGui.getGUIThread().invokeLater(() -> panel.updateCurrentPosition(finalPosition));
            }
        }
    }

    // -- Retry helpers, mirroring UltraBorg.py's SetWithRetry / GetWithRetry ---------------------

    @FunctionalInterface
    private interface IntWriter {
        void write(int value) throws IOException;
    }

    @FunctionalInterface
    private interface IntReader {
        int read() throws IOException;
    }

    /**
     * Writes {@code value} using {@code setter}, then confirms it stuck by reading it back with {@code getter},
     * retrying up to {@code retries} times on a mismatch, an I/O error, or a rejected value (e.g. a startup position
     * outside the configured min/max).
     */
    private static boolean setWithRetry(IntWriter setter, IntReader getter, int value, int retries) {
        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                setter.write(value);
                if (getter.read() == value) {
                    return true;
                }
            } catch (IOException | RuntimeException e) {
                // Fall through and retry.
            }
        }
        return false;
    }

    /** Reads a value with {@code reader}, retrying on I/O errors. Returns null if all attempts fail. */
    private static Integer getWithRetry(IntReader reader, int retries) {
        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                return reader.read();
            } catch (IOException e) {
                // Fall through and retry.
            }
        }
        return null;
    }

    // -- Per-channel UI ----------------------------------------------------------------------------

    /** Groups together all the widgets and state for one servo channel's column. */
    private class ChannelPanel {

        private final Channel channel;
        private final Component rootComponent;

        private final VerticalSlider slider;
        private final Label calibrationValueLabel;
        private final Label maximumLabel;
        private final Label startupLabel;
        private final Label minimumLabel;
        private final Label currentLabel;

        // The last raw PWM burst read back from the board for this channel, used as the value
        // that "Save maximum/startup/minimum" persist -- mirrors GetLabelValue(self.lblServoN)
        // in ubTuningGui.py, which reads the live position label rather than the slider.
        private volatile int lastKnownPosition = 0;

        ChannelPanel(Channel channel) {
            this.channel = channel;
            int displayNumber = channel.ordinal() + 1;

            slider = new VerticalSlider(CAL_PWM_MIN, CAL_PWM_MAX, CAL_PWM_START, CAL_SMALL_STEP, CAL_LARGE_STEP);
            calibrationValueLabel = new Label(Integer.toString(CAL_PWM_START));
            slider.onChange(value -> {
                calibrationValueLabel.setText(Integer.toString(value));
                try {
                    driver.calibrateServoPwm(channel, value);
                } catch (IOException e) {
                    calibrationValueLabel.setText(value + " (I2C error)");
                }
            });
            Panel sliderPanel = new Panel(new LinearLayout(Direction.VERTICAL));
            sliderPanel.addComponent(slider);
            sliderPanel.addComponent(calibrationValueLabel);

            maximumLabel = new Label("-");
            startupLabel = new Label("-");
            minimumLabel = new Label("-");
            currentLabel = new Label("-");

            Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
            panel.addComponent(sliderPanel.withBorder(Borders.singleLine("Calibrate")));
            panel.addComponent(new Label("Maximum").addStyle(SGR.BOLD));
            panel.addComponent(maximumLabel);
            panel.addComponent(new Button("Save maximum", this::onSaveMaximum));
            panel.addComponent(new Label("Startup").addStyle(SGR.BOLD));
            panel.addComponent(startupLabel);
            panel.addComponent(new Button("Save startup", this::onSaveStartup));
            panel.addComponent(new Label("Minimum").addStyle(SGR.BOLD));
            panel.addComponent(minimumLabel);
            panel.addComponent(new Button("Save minimum", this::onSaveMinimum));
            panel.addComponent(new Label("Current position").addStyle(SGR.BOLD));
            panel.addComponent(currentLabel);

            this.rootComponent = panel.withBorder(Borders.singleLine("Servo #" + displayNumber));
        }

        Component getRootComponent() {
            return rootComponent;
        }

        void readCalibration() {
            setLabelValue(maximumLabel, getWithRetry(() -> driver.getServoMaximum(channel), RETRY_COUNT));
            setLabelValue(startupLabel, getWithRetry(() -> driver.getServoStartup(channel), RETRY_COUNT));
            setLabelValue(minimumLabel, getWithRetry(() -> driver.getServoMinimum(channel), RETRY_COUNT));
        }

        private void setLabelValue(Label label, Integer pwmLevel) {
            if (pwmLevel == null || pwmLevel == 0 || pwmLevel == UltraBorgDriver.PWM_UNSET) {
                label.setText("Unset");
            } else {
                label.setText(Integer.toString(pwmLevel));
            }
        }

        // Called (on the poll thread's behalf, via invokeLater) with a fresh reading.
        void updateCurrentPosition(Integer position) {
            if (position == null) {
                currentLabel.setText("?");
            } else {
                currentLabel.setText(Integer.toString(position));
                lastKnownPosition = position;
            }
        }

        void onSaveMaximum() {
            saveLimit(maximumLabel, v -> driver.setServoMaximum(channel, v), () -> driver.getServoMaximum(channel));
        }

        void onSaveMinimum() {
            saveLimit(minimumLabel, v -> driver.setServoMinimum(channel, v), () -> driver.getServoMinimum(channel));
        }

        void onSaveStartup() {
            saveLimit(startupLabel, v -> driver.setServoStartup(channel, v), () -> driver.getServoStartup(channel));
        }

        private void saveLimit(Label label, IntWriter setter, IntReader getter) {
            int pwmLevel = lastKnownPosition;
            if (pwmLevel == 0) {
                label.setText(pwmLevel + "\nCannot save!");
                label.setForegroundColor(TextColor.ANSI.RED);
                return;
            }
            boolean ok = setWithRetry(setter, getter, pwmLevel, RETRY_COUNT);
            label.setText(pwmLevel + (ok ? "\nSaved" : "\nSave failed!"));
            label.setForegroundColor(ok ? TextColor.ANSI.DEFAULT : TextColor.ANSI.RED);
        }

        void resetToDefaults() {
            setWithRetry(v -> driver.setServoMaximum(channel, v), () -> driver.getServoMaximum(channel), STD_PWM_MAX,
                    RETRY_COUNT);
            setWithRetry(v -> driver.setServoMinimum(channel, v), () -> driver.getServoMinimum(channel), STD_PWM_MIN,
                    RETRY_COUNT);
            setWithRetry(v -> driver.setServoStartup(channel, v), () -> driver.getServoStartup(channel), STD_PWM_START,
                    RETRY_COUNT);

            slider.setValue(CAL_PWM_START);
            calibrationValueLabel.setText(Integer.toString(CAL_PWM_START));
            try {
                driver.calibrateServoPwm(channel, CAL_PWM_START);
            } catch (IOException e) {
                // The next poll tick will reflect whatever the board is actually doing.
            }

            maximumLabel.setForegroundColor(TextColor.ANSI.DEFAULT);
            minimumLabel.setForegroundColor(TextColor.ANSI.DEFAULT);
            startupLabel.setForegroundColor(TextColor.ANSI.DEFAULT);
            readCalibration();
        }
    }
}
