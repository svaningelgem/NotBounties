package me.jadenp.notbounties.features;

import me.jadenp.notbounties.data.Bounty;
import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.data.Setter;
import me.jadenp.notbounties.utils.BountyManager;
import me.jadenp.notbounties.utils.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

import static me.jadenp.notbounties.utils.BountyManager.*;
import static me.jadenp.notbounties.features.LanguageOptions.*;


public class BountyExpire {
    private static double time;
    private static boolean offlineTracking;
    private static boolean rewardReceiver;
    private static final Map<UUID, Long> playTimes = new HashMap<>();
    private static final Map<UUID, Long> logonTimes = new HashMap<>();

    private BountyExpire(){}

    public static void loadConfiguration(ConfigurationSection configuration) {
        time = configuration.getDouble("time");
        offlineTracking = configuration.getBoolean("offline-tracking");
        rewardReceiver = configuration.getBoolean("reward-receiver");
    }

    public static void login(Player player) {
        logonTimes.put(player.getUniqueId(), System.currentTimeMillis());
        playTimes.put(player.getUniqueId(), 0L);
    }

    public static void logout(Player player) {
        if (!logonTimes.containsKey(player.getUniqueId())) {
            NotBounties.debugMessage("No recorded login time for " + player.getName(), false);
            return;
        }
        long timePlayed = System.currentTimeMillis() - logonTimes.get(player.getUniqueId());
        if (playTimes.containsKey(player.getUniqueId())) {
            playTimes.replace(player.getUniqueId(), playTimes.get(player.getUniqueId()) + timePlayed);
        } else {
            playTimes.put(player.getUniqueId(), timePlayed);
        }
        logonTimes.remove(player.getUniqueId());
    }

    public static long getTimePlayed(UUID uuid) {
        if (!playTimes.containsKey(uuid))
            return 0;
        if (logonTimes.containsKey(uuid))
            return playTimes.get(uuid) + (System.currentTimeMillis() - logonTimes.get(uuid));
        return playTimes.get(uuid);
    }
    public static boolean isExpired(UUID receiver, Setter setter) {
        // the time that matters
        // offlineTracking = true -> time since the bounty was created
        // offlineTracking = false -> change in playtime of receiver since the bounty was set
        long compareTime = offlineTracking ? System.currentTimeMillis() - setter.getTimeCreated() : getTimePlayed(receiver) - setter.getReceiverPlaytime();
        return isExpired(compareTime, setter.getUuid().equals(DataManager.GLOBAL_SERVER_ID));
    }

    public static long getExpireTime(UUID receiver, Setter setter) {
        // the time that matters
        // offlineTracking = true -> time since the bounty was created
        // offlineTracking = false -> change in playtime of receiver since the bounty was set
        long compareTime = offlineTracking ? System.currentTimeMillis() - setter.getTimeCreated() : getTimePlayed(receiver) - setter.getReceiverPlaytime();
        return getExpireTime(compareTime, setter.getUuid().equals(DataManager.GLOBAL_SERVER_ID));
    }

    public static long getLowestExpireTime(Bounty bounty) {
        if (bounty.getSetters().isEmpty()) {
            DataManager.getLocalData().removeBounty(bounty.getUUID());
            return System.currentTimeMillis();
        }
        long lowestTime = getExpireTime(bounty.getUUID(), bounty.getSetters().get(0));
        for (int i = 1; i < bounty.getSetters().size(); i++) {
            long expireTime = getExpireTime(bounty.getUUID(), bounty.getSetters().get(i));
            if (expireTime < lowestTime)
                lowestTime = expireTime;
        }
        return lowestTime;
    }

    public static long getHighestExpireTime(Bounty bounty) {
        if (bounty.getSetters().isEmpty()) {
            DataManager.getLocalData().removeBounty(bounty.getUUID());
            return System.currentTimeMillis();
        }
        long highestTime = getExpireTime(bounty.getUUID(), bounty.getSetters().get(0));
        for (int i = 1; i < bounty.getSetters().size(); i++) {
            long expireTime = getExpireTime(bounty.getUUID(), bounty.getSetters().get(i));
            if (expireTime > highestTime)
                highestTime = expireTime;
        }
        return highestTime;
    }

    /**
     * Checks if a time is longer than the bounty expire time
     * @param compareTime Time to compare
     * @param autoBounty Whether the bounty should follow auto bounty expire time
     * @return true if the compare time is larger than the expire time
     */
    private static boolean isExpired(long compareTime, boolean autoBounty) {
        return getExpireTime(compareTime, autoBounty) <= 0;
    }

    /**
     * Get the amount of time left before the boutny expires
     * @param compareTime Time to compare
     * @param autoBounty Whether the bounty should follow auto bounty expire time
     * @return the time in milliseconds until the bounty expires, or 1 year if it won't
     */
    private static long getExpireTime(long compareTime, boolean autoBounty) {
        if (autoBounty && ConfigOptions.getAutoBounties().getExpireTime() != -1) {
            if (ConfigOptions.getAutoBounties().getExpireTime() < -1)
                return 365 * 24 * 60 * 60 * 1000L; // 1 year (won't expire)
            return (long) (ConfigOptions.getAutoBounties().getExpireTime() * 1000L * 60 * 60 * 24 - compareTime);
        }
        if (time <= 0)
            return 365 * 24 * 60 * 60 * 1000L; // 1 year (won't expire)
        return (long) (time * 1000L * 60 * 60 * 24 - compareTime);
    }

    public static synchronized void removeExpiredBounties() {
        if (time <= 0 && ConfigOptions.getAutoBounties().getExpireTime() <= 0) {
            return;
        }
        // go through all the bounties and remove setters if it has been more than expire time

            Map<Bounty, List<Setter>> settersToRemove = new HashMap<>();
            for (Bounty bounty : BountyManager.getAllBounties(-1)) {
                for (Setter setter : bounty.getSetters()) {
                    if (isExpired(bounty.getUUID(), setter)) {
                        if (!setter.getUuid().equals(new UUID(0, 0))) {
                            // check if setter is online
                            NotBounties.getServerImplementation().global().run(task -> {
                                Player player = Bukkit.getPlayer(setter.getUuid());
                                if (player != null) {
                                    player.sendMessage(parse(getPrefix() + getMessage("expired-bounty"), setter.getDisplayAmount(), Bukkit.getOfflinePlayer(bounty.getUUID())));
                                }
                                if (rewardReceiver) {
                                    refundPlayer(bounty.getUUID(), setter.getAmount(), setter.getItems(), null);
                                } else {
                                    refundSetter(setter, LanguageOptions.parse(LanguageOptions.getMessage("refund-reason-expire"), Bukkit.getOfflinePlayer(bounty.getUUID())));
                                }
                            });
                        }
                        if (settersToRemove.containsKey(bounty)) {
                            settersToRemove.get(bounty).add(setter);
                        } else {
                            settersToRemove.put(bounty, new ArrayList<>(List.of(setter)));
                        }
                    }
                }
            }
            for (Map.Entry<Bounty, List<Setter>> entry : settersToRemove.entrySet()) {
                DataManager.removeSetters(entry.getKey(), entry.getValue());
            }
    }

    public static double getTime() {
        return time;
    }

}

