package com.mores.control;

/**
 * Something that can put pixels on a single physical display. Implementations range from "draw once" (test patterns) to
 * "own a background animation thread" (Uncanny Eyes).
 */
public interface ScreenRenderer {

    /** Begin showing this content. Called once when the mode is selected. */
    void start();

    /** Stop showing this content and release any resources / threads. Called before switching away. */
    void stop();

    /**
     * Re-push current content to the display. Called after the x/y offset changes. Renderers with their own animation
     * loop (Uncanny Eyes) can leave this as a no-op, since the next frame will naturally pick up the new offset.
     */
    default void refresh() {
        // no-op by default
    }
}
