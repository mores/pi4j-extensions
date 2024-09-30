package com.pi4j.extensions.events;

import com.pi4j.common.Identity;

public class PositionEvent {

    public final Identity identity;
    public final int position;

    public PositionEvent(Identity identity, int i) {
        this.identity = identity;
        this.position = i;
    }
}
