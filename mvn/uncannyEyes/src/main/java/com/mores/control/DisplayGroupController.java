package com.mores.control;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mores.control.renderer.AlignmentPatternRenderer;
import com.mores.control.renderer.TestPatternRenderer;
import com.mores.control.renderer.UncannyEyesRenderer;
import lombok.Getter;

/**
 * Owns the single, shared {@link DisplayMode} for the whole rig and whichever {@link ScreenRenderer}(s) are currently
 * active. Mode is intentionally NOT a per-{@link DisplayChannel} property: both eyes must switch content at the same
 * moment, and Uncanny Eyes mode in particular must be driven by one {@link UncannyEyesRenderer} that owns every display
 * at once (see its javadoc) so motion/blink/squint stay synchronized between the two eyes. Modes that don't hold
 * cross-display state (test/alignment patterns) still get one renderer instance per channel internally, but they are
 * all started and stopped together as a unit.
 */
public class DisplayGroupController {

    private static Logger log = LoggerFactory.getLogger(DisplayGroupController.class);

    private final List<DisplayChannel<?>> channels;

    @Getter
    private DisplayMode mode;

    private List<ScreenRenderer> activeRenderers = List.of();

    /**
     * For static-image modes, lets a per-channel offset change refresh just that channel's renderer. Empty in modes
     * (like Uncanny Eyes) that are driven by a single shared renderer rather than one-per-channel.
     */
    private final Map<DisplayChannel<?>, ScreenRenderer> perChannelRenderer = new IdentityHashMap<>();

    public DisplayGroupController(List<DisplayChannel<?>> channels, DisplayMode initialMode) {
        this.channels = List.copyOf(channels);
        setMode(initialMode);
    }

    public List<DisplayChannel<?>> getChannels() {
        return channels;
    }

    /** Change what's being shown, across every display at once. Stops whatever was active, starts the new mode. */
    public synchronized void setMode(DisplayMode newMode) {
        log.info("switching mode {} -> {} (all {} displays)", mode, newMode, channels.size());

        for (ScreenRenderer r : activeRenderers) {
            r.stop();
        }
        perChannelRenderer.clear();

        this.mode = newMode;
        this.activeRenderers = createRenderers(newMode);

        for (ScreenRenderer r : activeRenderers) {
            r.start();
        }
    }

    /**
     * Re-push content after a single channel's offset changed. For shared renderers (Uncanny Eyes) this is a no-op --
     * the animation loop naturally picks up the new offset on its next frame. For static renderers, refresh just that
     * channel so we don't need to redraw displays whose offset didn't change.
     */
    public synchronized void refreshChannel(DisplayChannel<?> channel) {
        ScreenRenderer r = perChannelRenderer.get(channel);
        if (r != null) {
            r.refresh();
        }
    }

    private List<ScreenRenderer> createRenderers(DisplayMode m) {
        if (m == DisplayMode.UNCANNY_EYES) {
            // One engine, given every display, so both eyes share motion/blink/squint state.
            return List.of(new UncannyEyesRenderer(channels.stream().map(DisplayChannel::getGraphics).toList()));
        }

        List<ScreenRenderer> renderers = new ArrayList<>(channels.size());
        for (DisplayChannel<?> channel : channels) {
            ScreenRenderer r = switch (m) {
                case TEST_PATTERN ->
                        new TestPatternRenderer(channel.getGraphics(), channel.getWidth(), channel.getHeight());
                case ALIGNMENT_PATTERN ->
                        new AlignmentPatternRenderer(channel.getGraphics(), channel.getWidth(), channel.getHeight());
                case UNCANNY_EYES -> throw new IllegalStateException("handled above");
            };
            perChannelRenderer.put(channel, r);
            renderers.add(r);
        }
        return renderers;
    }
}
