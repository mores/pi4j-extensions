package com.mores.control;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A full-screen Lanterna TUI with one panel per DisplayChannel. Each panel lets you pick the display mode and
 * independently adjust that display's x/y offset.
 */
@Slf4j
public class ControllerApp {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?[0-9]*");

    private final List<DisplayChannel<?>> channels;
    private Screen screen;
    private MultiWindowTextGUI gui;

    public ControllerApp(List<DisplayChannel<?>> channels) {
        this.channels = channels;
    }

    /** Blocks until the user quits the TUI (Quit button or closing the window). */
    public void run() throws Exception {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        screen = new TerminalScreen(terminal);
        screen.startScreen();

        gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace());

        BasicWindow window = new BasicWindow("Display Controller");
        window.setHints(List.of(Window.Hint.FULL_SCREEN));

        Panel channelsRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        for (DisplayChannel<?> channel : channels) {
            channelsRow.addComponent(buildChannelPanel(channel));
        }

        Panel root = new Panel(new BorderLayout());
        root.addComponent(channelsRow, BorderLayout.Location.CENTER);

        TextBox logBox = new TextBox(new TerminalSize(60, 10), TextBox.Style.MULTI_LINE);
        logBox.setReadOnly(true);

        com.mores.log.LanternaLogAppender.setTargetTextBox(logBox);
        root.addComponent(logBox, BorderLayout.Location.BOTTOM);

        // 2. NEW FIX: Manually link the Log4j 2 configuration tree to your appender instance
        try {
            org.apache.logging.log4j.core.LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager
                    .getContext(false);
            org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

            // Dynamically retrieve the pattern layout block declared inside your log4j2.xml config file
            org.apache.logging.log4j.core.Layout<? extends java.io.Serializable> xmlLayout = null;
            if (config.getAppender("fileAppender") != null) {
                xmlLayout = config.getAppender("fileAppender").getLayout();
            } else {
                // Fallback standard structure if fileAppender layout fails to parse
                xmlLayout = org.apache.logging.log4j.core.layout.PatternLayout.newBuilder()
                        .withPattern("%d{HH:mm:ss.SSS} [%t] %-5level - %msg%n").build();
            }

            // Build the dynamic, active instance of your Log4j appender logic
            com.mores.log.LanternaLogAppender activeAppender = new com.mores.log.LanternaLogAppender("LanternaAppender",
                    null, xmlLayout, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);

            // Lifecycle triggers: Active appenders require explicit boot sequences before processing logging events
            activeAppender.start();
            config.addAppender(activeAppender);

            // Forcefully hook the appender directly into all defined Loggers in your XML file
            config.getRootLogger().addAppender(activeAppender, null, null);

            if (config.getLoggerConfig("com.pi4j") != null) {
                config.getLoggerConfig("com.pi4j").addAppender(activeAppender, null, null);
            }
            if (config.getLoggerConfig("com.pi4j.extensions") != null) {
                config.getLoggerConfig("com.pi4j.extensions").addAppender(activeAppender, null, null);
            }

            // Push modifications live across the active application logger tree instance
            context.updateLoggers();

            // Send an immediate test validation trail to the UI components
            org.apache.logging.log4j.LogManager.getLogger(com.mores.log.LanternaLogAppender.class)
                    .info("Lanterna UI log streaming engine linked successfully!");

        } catch (Exception e) {
            // If anything breaks during contextual runtime hacking, dump the stack safely out to your log file
            org.apache.logging.log4j.LogManager.getLogger(com.mores.log.LanternaLogAppender.class)
                    .error("Failed to inject Lanterna Log Appender context dynamically", e);
        }

        /*
         * Button quitButton = new Button("Quit", window::close); Panel bottomBar = new Panel(new
         * LinearLayout(Direction.HORIZONTAL)); bottomBar.addComponent(quitButton); root.addComponent(bottomBar,
         * BorderLayout.Location.BOTTOM);
         */

        window.setComponent(root);

        try {
            gui.addWindowAndWait(window);
        } finally {
            screen.stopScreen();
        }
    }

    private Component buildChannelPanel(DisplayChannel<?> channel) {
        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        panel.addComponent(new Label(channel.getName()));

        // --- Mode selection ---
        RadioBoxList<DisplayMode> modeList = new RadioBoxList<>(new TerminalSize(24, DisplayMode.values().length));
        int selectedIndex = 0;
        for (int i = 0; i < DisplayMode.values().length; i++) {
            DisplayMode m = DisplayMode.values()[i];
            modeList.addItem(m);
            if (m == channel.getMode()) {
                selectedIndex = i;
            }
        }
        modeList.setCheckedItemIndex(selectedIndex);
        modeList.addListener((newSelection, oldSelection) -> {
            DisplayMode selected = modeList.getItemAt(newSelection);
            channel.setMode(selected);
        });
        panel.addComponent(modeList.withBorder(Borders.singleLine("Mode")));

        // --- Offset entry ---
        Panel offsetGrid = new Panel(new GridLayout(2));
        offsetGrid.addComponent(new Label("X:"));
        TextBox xBox = new TextBox(new TerminalSize(6, 1), Integer.toString(channel.getXOffset()));
        xBox.setValidationPattern(INTEGER_PATTERN);
        offsetGrid.addComponent(xBox);

        offsetGrid.addComponent(new Label("Y:"));
        TextBox yBox = new TextBox(new TerminalSize(6, 1), Integer.toString(channel.getYOffset()));
        yBox.setValidationPattern(INTEGER_PATTERN);
        offsetGrid.addComponent(yBox);

        Button apply = new Button("Apply", () -> applyOffsets(channel, xBox, yBox));

        Panel offsetPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        offsetPanel.addComponent(offsetGrid);
        offsetPanel.addComponent(apply);
        panel.addComponent(offsetPanel.withBorder(Borders.singleLine("Offset")));

        // --- Nudge buttons (1px increments), keeps the text boxes in sync ---
        Panel nudge = new Panel(new GridLayout(3));
        nudge.addComponent(new EmptySpace(new TerminalSize(3, 1)));
        nudge.addComponent(new Button("Up", () -> nudge(channel, xBox, yBox, 0, -1)));
        nudge.addComponent(new EmptySpace(new TerminalSize(3, 1)));

        nudge.addComponent(new Button("Left", () -> nudge(channel, xBox, yBox, -1, 0)));
        nudge.addComponent(new Button("0,0", () -> {
            xBox.setText("0");
            yBox.setText("0");
            applyOffsets(channel, xBox, yBox);
        }));
        nudge.addComponent(new Button("Right", () -> nudge(channel, xBox, yBox, 1, 0)));

        nudge.addComponent(new EmptySpace(new TerminalSize(3, 1)));
        nudge.addComponent(new Button("Down", () -> nudge(channel, xBox, yBox, 0, 1)));
        nudge.addComponent(new EmptySpace(new TerminalSize(3, 1)));

        panel.addComponent(nudge.withBorder(Borders.singleLine("Nudge")));

        return panel.withBorder(Borders.doubleLine(channel.getName()));
    }

    private void applyOffsets(DisplayChannel<?> channel, TextBox xBox, TextBox yBox) {
        try {
            int x = Integer.parseInt(xBox.getText().trim());
            int y = Integer.parseInt(yBox.getText().trim());
            channel.setOffsets(x, y);
        } catch (NumberFormatException e) {
            MessageDialog.showMessageDialog(gui, "Invalid offset", "X and Y must be whole numbers.",
                    MessageDialogButton.OK);
        }
    }

    private void nudge(DisplayChannel<?> channel, TextBox xBox, TextBox yBox, int dx, int dy) {
        int x = channel.getXOffset() + dx;
        int y = channel.getYOffset() + dy;
        xBox.setText(Integer.toString(x));
        yBox.setText(Integer.toString(y));
        channel.setOffsets(x, y);
    }
}
