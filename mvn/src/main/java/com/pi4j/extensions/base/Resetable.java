package com.pi4j.extensions.base;

import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputConfig;

public interface Resetable {

    public void reset();
}
