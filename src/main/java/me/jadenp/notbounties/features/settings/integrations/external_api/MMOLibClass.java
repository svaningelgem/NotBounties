package me.jadenp.notbounties.features.settings.integrations.external_api;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import me.jadenp.notbounties.data.Bounty;
import me.jadenp.notbounties.utils.BountyManager;
import me.jadenp.notbounties.features.ConfigOptions;
import me.jadenp.notbounties.features.settings.money.NumberFormatting;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MMOLibClass implements Listener {
    private static boolean enabled;
    private static final List<BountyStatModifier> modifiers = new ArrayList<>();
    private static final String NOTBOUNTIES_STAT_KEY = "NotBounties";

    private MMOLibClass(){}

    public static void loadConfiguration(ConfigurationSection configuration) {
        removeAllModifiers();

        enabled = configuration.getBoolean("enabled") && ConfigOptions.getIntegrations().isMmoLibEnabled();
        modifiers.clear();
        for (String key : configuration.getKeys(false)) {
            if (configuration.isConfigurationSection(key))
                modifiers.add(new BountyStatModifier(Objects.requireNonNull(configuration.getConfigurationSection(key))));
        }

        // add modifiers to online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            Bounty bounty = BountyManager.getBounty(player.getUniqueId());
            if (bounty != null)
                addStats(player, bounty.getTotalBounty());
        }
    }

    public static void removeAllModifiers() {
        if (ConfigOptions.getIntegrations().isMmoLibEnabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                MMOPlayerData playerData = MMOPlayerData.get(player);
                StatMap statMap = playerData.getStatMap();
                for (StatInstance instance : statMap.getInstances()) {
                    instance.removeIf(NOTBOUNTIES_STAT_KEY::equals);
                }
            }
        }
    }

    public static void addStats(Player player, double bountyAmount) {
        if (enabled) {
            MMOPlayerData playerData = MMOPlayerData.get(player);
            for (BountyStatModifier modifier : modifiers) {
                if (bountyAmount >= modifier.minBounty && (modifier.maxBounty == -1 || bountyAmount < modifier.maxBounty)) {
                    // applicable
                    modifier.getStatModifier().register(playerData);
                }
            }
        }

    }

    public static void removeStats(Player player) {
        if (enabled) {
            MMOPlayerData playerData = MMOPlayerData.get(player);
            StatMap statMap = playerData.getStatMap();
            for (StatInstance instance : statMap.getInstances()) {
                instance.removeIf(NOTBOUNTIES_STAT_KEY::equals);
            }
        }
    }

    private static class BountyStatModifier {
        private final double minBounty;
        private final double maxBounty;
        private final StatModifier statModifier;
        public BountyStatModifier(ConfigurationSection configuration) {
            double maxBounty1 = -1;
            double minBounty1 = 0;
            String bounty = configuration.isSet("bounty") ? configuration.getString("bounty") : "0";
            try {
                if (bounty != null && bounty.contains("-")) {
                    minBounty1 = NumberFormatting.tryParse(bounty.substring(0, bounty.indexOf("-")));
                    maxBounty1 = NumberFormatting.tryParse(bounty.substring(bounty.indexOf("-") + 1));
                } else {
                    minBounty1 = NumberFormatting.tryParse(bounty);
                }
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("[NotBounties] Invalid format for \"bounty\" in the MMOLib." + configuration.getName() + " configuration section!");

            }
            maxBounty = maxBounty1;
            minBounty = minBounty1;

            String stat = configuration.getString("stat");
            if (stat == null) {
                stat = "UNKNOWN";
                Bukkit.getLogger().warning("[NotBounties] No stat string found in the MMOLib." + configuration.getName() + " configuration section!");
            }
            ModifierType modifierType;
            if (configuration.getBoolean("multiplicative")) {
                try {
                    modifierType = ModifierType.ADDITIVE_MULTIPLIER;
                } catch (NoSuchFieldError e){
                    // not using latest version of MMOLib
                    modifierType = ModifierType.RELATIVE;
                }
            } else {
                modifierType = ModifierType.FLAT;
            }
            statModifier = new StatModifier(NOTBOUNTIES_STAT_KEY, stat, configuration.getDouble("value"), modifierType);
        }

        public StatModifier getStatModifier() {
            return statModifier;
        }
    }
}
