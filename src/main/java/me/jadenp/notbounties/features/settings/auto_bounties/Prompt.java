package me.jadenp.notbounties.features.settings.auto_bounties;

import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.features.LanguageOptions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;

public class Prompt implements Listener {
    private static int attempts;
    private static List<String> cancelWords;
    private static long timeLimit;
    private static final Map<UUID, CommandPrompt> commandPrompts = new HashMap<>();
    public static void loadConfiguration(ConfigurationSection configuration) {
        attempts = configuration.getInt("attempts");
        cancelWords = new ArrayList<>();
        configuration.getStringList("cancel-words").forEach(word -> cancelWords.add(word.toLowerCase()));
        timeLimit = configuration.getLong("time-limit");
    }

    public static void addCommandPrompt(UUID uuid, CommandPrompt commandPrompt) {
        commandPrompts.put(uuid, commandPrompt);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void asyncChatEvent(AsyncPlayerChatEvent event) {
        if (commandPrompts.containsKey(event.getPlayer().getUniqueId()) && !NotBounties.isPaused()) {
            CommandPrompt commandPrompt = commandPrompts.get(event.getPlayer().getUniqueId());
            if (commandPrompt.isExpired() || commandPrompt.isSilentCancel()) {
                removePrompt(event.getPlayer().getUniqueId());
                return;
            }
            if (cancelWords.contains(event.getMessage().replace(" ", "").toLowerCase())) {
                removePrompt(event.getPlayer().getUniqueId());
                event.getPlayer().sendMessage(LanguageOptions.parse(LanguageOptions.getPrefix() + LanguageOptions.getMessage("prompt-cancel"), event.getPlayer()));
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            commandPrompt.executeCommand(event.getMessage());
        }
    }

    public static void successfulExecute(UUID uuid) {
        removePrompt(uuid);
    }

    public static void failExecute(UUID uuid) {
        if (commandPrompts.containsKey(uuid)) {
            CommandPrompt commandPrompt = commandPrompts.get(uuid);
            if (commandPrompt.getAttemptsUsed() >= attempts) {
                removePrompt(uuid);
            } else {
                commandPrompt.reprompt();
            }
        }
    }

    private static void removePrompt(UUID uuid) {
        if (commandPrompts.containsKey(uuid)) {
            CommandPrompt commandPrompt = commandPrompts.get(uuid);
            commandPrompt.expire();
            commandPrompts.remove(uuid);
        }
    }

    public static long getTimeLimit() {
        return timeLimit;
    }
}
