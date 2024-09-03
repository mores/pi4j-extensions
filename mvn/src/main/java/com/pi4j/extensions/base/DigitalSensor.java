package com.pi4j.extensions.base;

import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputConfig;

public abstract class DigitalSensor {
    /**
     * Pi4J digital input instance used by this component (that's the low-level Pi4J Class)
     */
    protected final DigitalInput digitalInput;

    protected DigitalSensor(Context pi4j, DigitalInputConfig config) {
        digitalInput = pi4j.create(config);
    }

    public int pinNumber() {
        return digitalInput.address().intValue();
    }

}
