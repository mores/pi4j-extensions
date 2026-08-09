package com.mores.control;

/**
 * The content that can be shown on a given physical display. Add new entries here as you add new renderers.
 */
public enum DisplayMode {
    UNCANNY_EYES("Uncanny Eyes"), TEST_PATTERN("Test Pattern"), ALIGNMENT_PATTERN("Alignment Pattern");

    private final String label;

    DisplayMode(String label) {
        this.label = label;
    }

    /** Used by RadioBoxList so the TUI shows a friendly name instead of the enum constant. */
    @Override
    public String toString() {
        return label;
    }
}
