package com.pi4j.extensions.components;

import java.time.Duration;

import com.pi4j.context.Context;
import com.pi4j.Pi4J;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.extensions.base.PIN;
import com.pi4j.extensions.Utils;

public class SimpleButtonTest {

    private static Logger log = LoggerFactory.getLogger(SimpleButtonTest.class);

    private static Context pi4j;

    public void setUp() {
        log.info("setUp");

        pi4j = Pi4J.newAutoContext();
    }

    public void tearDown() {
        log.info("tearDown");

        pi4j.shutdown();
    }

    public void testOne() {
        log.info("testOne");

        try {
            final var button5 = new SimpleButton(pi4j, PIN.D5, Boolean.TRUE);

            button5.onDown(() -> log.info("Button 5 pressed"));
            button5.whilePressed(() -> log.info("Still pressing button 5"), Duration.ofSeconds(1));
            button5.onUp(() -> log.info("Stopped pressing button 5"));

            final var button6 = new SimpleButton(pi4j, PIN.D6, Boolean.TRUE);

            button6.onDown(() -> log.info("Button 6 pressed"));
            button6.whilePressed(() -> log.info("Still pressing button 6"), Duration.ofSeconds(1));
            button6.onUp(() -> log.info("Stopped pressing button 6"));

            // Wait for 25 seconds while handling events before exiting
            log.info("Press the button to see it in action!");
            Utils.delay(Duration.ofSeconds(25));

            // Unregister all event handlers to exit this application in a clean way
            button5.reset();
            button6.reset();

            /*
             * if you want to deRegister only a single function, you can do so like this: button.onUp(null);
             */

            log.info("Simple button demo finished.");
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }
}
