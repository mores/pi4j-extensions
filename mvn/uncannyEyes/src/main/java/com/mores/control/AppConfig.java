package com.mores.control;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists the shared {@link DisplayMode} and each {@link DisplayChannel}'s x/y nudge offsets to a single dotfile in
 * the user's home directory ({@code ~/.uncannyEyes}), so the rig comes back up in the same mode and alignment it was
 * left in after a restart. Format is plain {@link Properties} text, e.g.:
 *
 * <pre>
 * mode=UNCANNY_EYES
 * channel.Left\ Eye.xOffset=10
 * channel.Left\ Eye.yOffset=10
 * channel.Right\ Eye.xOffset=-10
 * channel.Right\ Eye.yOffset=-10
 * </pre>
 *
 * The file is read once at startup via {@link #load()}. From then on this object IS the config: every
 * {@link #setMode(DisplayMode)} / {@link #setOffset(String, int, int)} call updates the in-memory copy and immediately
 * rewrites the *entire* file from that copy, so a change to one value (say, nudging one eye) can never clobber another
 * value that was already on disk (say, the other eye's offset, or the current mode).
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String MODE_KEY = "mode";
    private static final String CHANNEL_PREFIX = "channel.";
    private static final String X_SUFFIX = ".xOffset";
    private static final String Y_SUFFIX = ".yOffset";

    private final File file;

    private DisplayMode mode;
    private final Map<String, int[]> offsets = new LinkedHashMap<>(); // channel name -> {x, y}

    private AppConfig(File file) {
        this.file = file;
    }

    /**
     * Reads {@code ~/.uncannyEyes} if it exists and parses cleanly. Returns an empty (all-default, i.e.
     * {@code getMode(fallback)} returns {@code fallback} and {@code getOffset(name)} returns {@code null}) config if
     * the file doesn't exist yet, can't be read, or contains something unrecognized -- this method never throws, so a
     * missing or corrupt config file never prevents startup.
     */
    public static synchronized AppConfig load() {
        File file = defaultFile();
        AppConfig config = new AppConfig(file);

        if (!file.exists()) {
            log.info("No config file at {}, starting with defaults", file);
            return config;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            log.warn("Failed to read {}, starting with defaults", file, e);
            return config;
        }

        String modeName = props.getProperty(MODE_KEY);
        if (modeName != null) {
            try {
                config.mode = DisplayMode.valueOf(modeName);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown mode '{}' in {}, ignoring saved mode", modeName, file);
            }
        }

        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(CHANNEL_PREFIX) || !key.endsWith(X_SUFFIX)) {
                continue;
            }
            String name = key.substring(CHANNEL_PREFIX.length(), key.length() - X_SUFFIX.length());
            String xStr = props.getProperty(key);
            String yStr = props.getProperty(CHANNEL_PREFIX + name + Y_SUFFIX);
            if (xStr == null || yStr == null) {
                log.warn("Incomplete offset for channel '{}' in {}, ignoring", name, file);
                continue;
            }
            try {
                config.offsets.put(name, new int[] { Integer.parseInt(xStr.trim()), Integer.parseInt(yStr.trim()) });
            } catch (NumberFormatException e) {
                log.warn("Invalid offset for channel '{}' in {}, ignoring", name, file);
            }
        }

        log.info("Loaded config from {}: mode={}, offsets={}", file, config.mode, config.offsets);
        return config;
    }

    private static File defaultFile() {
        return new File(System.getProperty("user.home"), ".uncannyEyes");
    }

    /** @return the saved mode, or {@code fallback} if nothing was saved (or the file was missing/unreadable). */
    public synchronized DisplayMode getMode(DisplayMode fallback) {
        return mode != null ? mode : fallback;
    }

    /** @return the saved {@code {x, y}} offset for this channel name, or {@code null} if none was saved. */
    public synchronized int[] getOffset(String channelName) {
        int[] o = offsets.get(channelName);
        return o == null ? null : o.clone();
    }

    /** Updates the mode in memory and rewrites the config file. */
    public synchronized void setMode(DisplayMode mode) {
        this.mode = mode;
        save();
    }

    /** Updates one channel's nudge offset in memory and rewrites the config file. */
    public synchronized void setOffset(String channelName, int x, int y) {
        offsets.put(channelName, new int[] { x, y });
        save();
    }

    private void save() {
        Properties props = new Properties();
        if (mode != null) {
            props.setProperty(MODE_KEY, mode.name());
        }
        for (Map.Entry<String, int[]> entry : offsets.entrySet()) {
            props.setProperty(CHANNEL_PREFIX + entry.getKey() + X_SUFFIX, Integer.toString(entry.getValue()[0]));
            props.setProperty(CHANNEL_PREFIX + entry.getKey() + Y_SUFFIX, Integer.toString(entry.getValue()[1]));
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "uncannyEyes display config -- mode and per-channel nudge offsets");
        } catch (IOException e) {
            log.warn("Failed to write config to {}", file, e);
        }
    }
}
