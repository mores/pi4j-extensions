package com.pi4j.extensions.components;

import com.pi4j.context.Context;
import com.pi4j.Pi4J;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleButtonTest {

    private static Logger log = LoggerFactory.getLogger(SimpleButtonTest.class);

    private static Context pi4j;

    public static void setUp() {
        log.info("setUp");

        pi4j = Pi4J.newAutoContext();
    }

    public static void tearDown() {
        log.info("tearDown");

        pi4j.shutdown();
    }

    public void testOne() {
        log.info("testOne");
    }
}
