package me.jadenp.notbounties.features.webhook;

import org.bukkit.configuration.ConfigurationSection;

public class WebhookField {
    private final String name;
    private final String value;
    private final boolean inline;
    public WebhookField(ConfigurationSection configuration) {
        name = configuration.getString("name");
        value = configuration.getString("value");
        inline = configuration.getBoolean("inline");
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean isInline() {
        return inline;
    }
}
