package com.mores.control;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mores.control.renderer.AlignmentPatternRenderer;
import com.mores.control.renderer.CyborgRenderer;
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
 * <p>
 * Every mode switch and every offset change (nudge) is persisted via the optional {@link AppConfig} to
 * {@code ~/.uncannyEyes}, so the rig comes back up in the same mode and alignment after a restart -- see
 * {@link com.mores.control.Main} for how the saved config is loaded and applied on startup.
 */
public class DisplayGroupController {

    private static Logger log = LoggerFactory.getLogger(DisplayGroupController.class);

    private final List<DisplayChannel<?>> channels;
    private final AppConfig config;

    @Getter
    private DisplayMode mode;

    private List<ScreenRenderer> activeRenderers = List.of();

    /**
     * @param config
     *            persists mode and per-channel nudge offsets to {@code ~/.uncannyEyes} (see {@link AppConfig}). May be
     *            {@code null} to disable persistence entirely (e.g. in tests).
     */
    public DisplayGroupController(List<DisplayChannel<?>> channels, DisplayMode initialMode, AppConfig config) {
        this.channels = List.copyOf(channels);
        this.config = config;
        setMode(initialMode);
    }

    public List<DisplayChannel<?>> getChannels() {
        return channels;
    }

    /**
     * Change what's being shown, across every display at once. Stops whatever was active, starts the new mode, and
     * persists the new mode to {@code ~/.uncannyEyes} so it's restored on the next startup.
     */
    public synchronized void setMode(DisplayMode newMode) {
        log.info("switching mode {} -> {} (all {} displays)", mode, newMode, channels.size());

        for (ScreenRenderer r : activeRenderers) {
            r.stop();
        }

        this.mode = newMode;
        this.activeRenderers = createRenderers(newMode);

        for (ScreenRenderer r : activeRenderers) {
            r.start();
        }

        if (config != null) {
            config.setMode(newMode);
        }
    }

    /**
     * Called after a channel's offset changes. DisplayChannel#setOffsets() builds a brand new GraphicsDisplay/Graphics
     * pair rather than moving the existing one (attachDriver() can't be safely re-issued on the same instance -- see
     * DisplayChannel's javadoc), so any already-running renderer is now holding a stale Graphics reference to a
     * GraphicsDisplay nobody draws to anymore. The only correct fix is to rebuild whatever renderer(s) are currently
     * active so they re-fetch each channel's CURRENT Graphics -- which is exactly what re-running setMode(mode) does.
     * This applies equally whether the offset changed under a per-channel renderer (Alignment/Test Pattern) or the
     * single shared Uncanny Eyes engine; a shared engine holding one stale channel's Graphics would otherwise leave
     * that display frozen after a nudge instead of moving.
     */
    public synchronized void onOffsetChanged(DisplayChannel<?> channel) {
        if (config != null) {
            config.setOffset(channel.getName(), channel.getXOffset(), channel.getYOffset());
        }
        setMode(mode);
    }

    private List<ScreenRenderer> createRenderers(DisplayMode m) {
        if (m == DisplayMode.UNCANNY_EYES) {
            // One engine, given every display, so both eyes share motion/blink/squint state.
            return List.of(new UncannyEyesRenderer(channels.stream().map(DisplayChannel::getGraphics).toList()));
        }

        if (m == DisplayMode.CYBORG) {
            // One renderer, given every display, so all screens wander in lock-step off the same shared RNG.
            return List.of(new CyborgRenderer(channels.stream().map(DisplayChannel::getGraphics).toList()));
        }

        List<ScreenRenderer> renderers = new ArrayList<>(channels.size());
        for (DisplayChannel<?> channel : channels) {
            ScreenRenderer r = switch (m) {
                case TEST_PATTERN ->
                        new TestPatternRenderer(channel.getGraphics(), channel.getWidth(), channel.getHeight());
                case ALIGNMENT_PATTERN ->
                        new AlignmentPatternRenderer(channel.getGraphics(), channel.getWidth(), channel.getHeight());
                case UNCANNY_EYES, CYBORG -> throw new IllegalStateException("handled above");
            };
            renderers.add(r);
        }
        return renderers;
    }
}
