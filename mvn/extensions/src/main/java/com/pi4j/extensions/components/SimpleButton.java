package com.pi4j.extensions.components;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;

import com.pi4j.extensions.base.DigitalSensor;
import com.pi4j.extensions.base.Resetable;
import com.pi4j.extensions.Utils;

import static com.pi4j.io.gpio.digital.DigitalInput.DEFAULT_DEBOUNCE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleButton extends DigitalSensor implements Resetable {

    private static Logger log = LoggerFactory.getLogger(SimpleButton.class);

    /**
     * Specifies if button state is inverted, e.g. HIGH = depressed, LOW = pressed This will also automatically switch
     * the pull resistance to PULL_UP
     */
    private final boolean inverted;
    /**
     * Runnable Code when button is depressed
     */
    private Runnable onUp;
    /**
     * Runnable Code when button is pressed
     */
    private Runnable onDown;
    /**
     * Handler while button is pressed
     */
    private Runnable whileDown;
    /**
     * Timer while button is pressed
     */
    private Duration whilePressedDelay;

    /**
     * what needs to be done while button is pressed (and whilePressed is != null)
     */
    private final Runnable whileDownWorker = () -> {
        while (isDown()) {
            Utils.delay(whilePressedDelay);
            if (isDown() && whileDown != null) {
                log.trace("whileDown triggered");
                whileDown.run();
            }
        }
    };

    private ExecutorService executor;

    /**
     * Creates a new button component
     *
     * @param pi4j
     *            Pi4J context
     */
    public SimpleButton(Context pi4j, int address, boolean inverted) {
        this(pi4j, address, inverted, DEFAULT_DEBOUNCE);
    }

    /**
     * Creates a new button component with custom GPIO address and debounce time.
     *
     * @param pi4j
     *            Pi4J context
     * @param address
     *            GPIO address of button
     * @param inverted
     *            Specify if button state is inverted
     * @param debounce
     *            Debounce time in microseconds
     */
    public SimpleButton(Context pi4j, int address, boolean inverted, long debounce) {
        super(pi4j, DigitalInput.newConfigBuilder(pi4j).id("BCM" + address).name("Button #" + address).address(address)
                .debounce(debounce).pull(inverted ? PullResistance.PULL_UP : PullResistance.PULL_DOWN).build());

        this.inverted = inverted;

        /*
         * Gets a DigitalStateChangeEvent directly from the Provider, as this Class is a listener. This runs in a
         * different Thread than main. Calls the methods onUp, onDown and whilePressed. WhilePressed gets executed in an
         * own Thread, as to not block other resources.
         */
        digitalInput.addListener(digitalStateChangeEvent -> {
            DigitalState state = getState();

            log.trace("{} Button switched to {}", address, state);

            switch (state) {
            case HIGH -> {
                if (onDown != null) {
                    log.trace("{} onDown triggered", address);
                    onDown.run();
                }

                if (whileDown != null) {
                    log.trace("{} whileDown triggered", address);
                    executor.submit(whileDownWorker);
                }
            }
            case LOW -> {
                if (onUp != null) {
                    log.trace("{} onUp triggered", address);
                    onUp.run();
                }
            }
            case UNKNOWN -> log.error("{} Button is in State UNKNOWN", address);
            }
        });
    }

    /**
     * Checks if button is currently pressed.
     * <P>
     * For a not-inverted button this means: if the button is pressed, then full voltage is present at the GPIO-Pin.
     * Therefore, the DigitalState is HIGH
     *
     * @return true if button is pressed
     */
    public boolean isDown() {
        return getState() == DigitalState.HIGH;
    }

    /**
     * Checks if button is currently depressed (= NOT pressed)
     * <P>
     * For a not-inverted button this means: if the button is depressed, then no voltage is present at the GPIO-Pin.
     * Therefore, the DigitalState is LOW
     *
     * @return true if button is depressed
     */
    public boolean isUp() {
        return getState() == DigitalState.LOW;
    }

    /**
     * Sets or disables the handler for the onDown event.
     * <P>
     * This event gets triggered whenever the button is pressed. Only a single event handler can be registered at once.
     *
     * @param task
     *            Event handler to call or null to disable
     */
    public void onDown(Runnable task) {
        onDown = task;
    }

    /**
     * Sets or disables the handler for the onUp event.
     * <P>
     * This event gets triggered whenever the button is no longer pressed. Only a single event handler can be registered
     * at once.
     *
     * @param task
     *            Event handler to call or null to disable
     */
    public void onUp(Runnable task) {
        onUp = task;
    }

    /**
     * Sets or disables the handler for the whilePressed event.
     * <P>
     * This event gets triggered whenever the button is pressed. Only a single event handler can be registered at once.
     *
     * @param task
     *            Event handler to call or null to disable
     */
    public void whilePressed(Runnable task, Duration delay) {
        whileDown = task;
        whilePressedDelay = delay;
        if (executor != null) {
            executor.shutdownNow();
        }
        if (task != null) {
            executor = Executors.newSingleThreadExecutor();
        }
    }

    public boolean isInInitialState() {
        return onDown == null && onUp == null && whileDown == null && executor == null;
    }

    /**
     * disables all the handlers for the onUp, onDown and WhileDown Events
     */
    @Override
    public void reset() {
        onDown = null;
        onUp = null;
        whileDown = null;
        if (executor != null) {
            executor.shutdown();
        }
        executor = null;
    }

    /**
     * Returns the current state of the Digital State
     *
     * @return Current DigitalInput state (Can be HIGH, LOW or UNKNOWN)
     */
    private DigitalState getState() {
        return switch (digitalInput.state()) {
        case HIGH -> inverted ? DigitalState.LOW : DigitalState.HIGH;
        case LOW -> inverted ? DigitalState.HIGH : DigitalState.LOW;
        default -> DigitalState.UNKNOWN;
        };
    }

}
