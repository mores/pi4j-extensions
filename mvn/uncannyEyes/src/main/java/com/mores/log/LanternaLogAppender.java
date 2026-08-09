package com.mores.log;

import com.googlecode.lanterna.gui2.TextBox;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;

@Plugin(name = "Lanterna", category = "Core", elementType = "appender", printObject = true)
public class LanternaLogAppender extends AbstractAppender {

    // Volatile reference ensures cross-thread visibility
    private static volatile TextBox targetTextBox;

    public LanternaLogAppender(String name, Filter filter, Layout<? extends Serializable> layout,
            boolean ignoreExceptions, Property[] properties) {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    public static void setTargetTextBox(TextBox textBox) {
        targetTextBox = textBox;
    }

    @PluginFactory
    public static LanternaLogAppender createAppender(@PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter, @PluginElement("Layout") Layout<? extends Serializable> layout) {

        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        return new LanternaLogAppender(name, filter, layout, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        // If the UI isn't ready yet, silently ignore or drop early startup logs
        if (targetTextBox == null) {
            return;
        }

        // Format message using the exact PatternLayout defined in your log4j2.xml
        String logMessage = new String(getLayout().toByteArray(event)).trim();

        // Lanterna UI modifications MUST happen inside its own window management event thread
        targetTextBox.getTextGUI().getGUIThread().invokeLater(() -> {
            targetTextBox.addLine(logMessage);
            // Push caret line focus downwards to follow scrolling logs
            targetTextBox.setCaretPosition(targetTextBox.getLineCount() - 1, 0);
        });
    }
}
