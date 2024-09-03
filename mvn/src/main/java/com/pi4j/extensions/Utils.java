package com.pi4j.extensions;

import java.time.Duration;

public class Utils {
    public static void delay(Duration duration) {

        try {
            long nanos = duration.toNanos();
            long millis = nanos / 1_000_000;
            int remainingNanos = (int) (nanos % 1_000_000);
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
