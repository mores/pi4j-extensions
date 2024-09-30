package com.pi4j.extensions.events;

import com.pi4j.common.Identity;

public class PressEvent {

    public final Identity identity;
    public final boolean pressed;

    public PressEvent(Identity identity, boolean pressed) {
        this.identity = identity;
        this.pressed = pressed;
    }
}
