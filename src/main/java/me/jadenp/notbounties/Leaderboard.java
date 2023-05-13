package me.jadenp.notbounties;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static me.jadenp.notbounties.ConfigOptions.*;
import static me.jadenp.notbounties.ConfigOptions.papiEnabled;

public enum Leaderboard {
    //(all/kills/claimed/deaths/set/immunity)
    ALL,
    KILLS,
    CLAIMED,
    DEATHS,
    SET,
    IMMUNITY;

    /**
     * Gets the stat from either local storage or the database if connected
     * @param uuid UUID of the player
     * @return stat
     */
    public int getStat(UUID uuid){
        NotBounties nb = NotBounties.getInstance();
        switch (this) {
            case ALL:
                if (nb.SQL.isConnected())
                    return nb.data.getAllTime(uuid.toString());
                return nb.allTimeBounty.get(uuid.toString());
            case KILLS:
                if (nb.SQL.isConnected())
                    return nb.data.getClaimed(uuid.toString());
                return nb.bountiesClaimed.get(uuid.toString());
            case CLAIMED:
                if (nb.SQL.isConnected())
                    return nb.data.getTotalClaimed(uuid.toString());
                return nb.allClaimed.get(uuid.toString());
            case DEATHS:
                if (nb.SQL.isConnected())
                    return nb.data.getReceived(uuid.toString());
                return nb.bountiesReceived.get(uuid.toString());
            case SET:
                if (nb.SQL.isConnected())
                    return nb.data.getSet(uuid.toString());
                return nb.bountiesSet.get(uuid.toString());
            case IMMUNITY:
                if (nb.SQL.isConnected())
                    return nb.data.getImmunity(uuid.toString());
                return nb.immunitySpent.get(uuid.toString());
            default:
                return 0;
        }
    }

    /**
     * Correctly displays the player's stat
     * @param player Player to display to
     */
    public String displayStats(OfflinePlayer player, boolean send, boolean shorten){
        String msg = "";
        switch (this){
            case ALL:
            case CLAIMED:
            case IMMUNITY:
                msg = parseStats(speakings.get(0) + getStatMsg(shorten), getStat(player.getUniqueId()), true, player);

                break;
            case KILLS:
            case DEATHS:
            case SET:
                msg = parseStats(speakings.get(0) + getStatMsg(shorten), getStat(player.getUniqueId()), false, player);
                break;
        }
        if (send && player.isOnline()) {
            Player p = player.getPlayer();
            assert p != null;
            p.sendMessage(msg);
        }
        return msg;

    }

    public String getStatMsg(boolean shorten){
        switch (this){
            case ALL:
                return shorten ? speakings.get(50) : speakings.get(44);
            case KILLS:
                return shorten ? speakings.get(51) : speakings.get(45);
            case CLAIMED:
                return shorten ? speakings.get(52) : speakings.get(46);
            case DEATHS:
                return shorten ? speakings.get(53) : speakings.get(47);
            case SET:
                return shorten ? speakings.get(54) : speakings.get(48);
            case IMMUNITY:
                return shorten ? speakings.get(55) : speakings.get(49);
        }
        return "";
    }

    /**
     * Gets the top stats of the leaderboard type in descending order
     * @param amount Amount of values you want returned
     * @return Map of UUID and stat value in descending order
     */
    public LinkedHashMap<String, Integer> getTop(int skip, int amount){
        NotBounties nb = NotBounties.getInstance();
        if (nb.SQL.isConnected())
            return nb.data.getTopStats(this, hiddenNames, skip, amount);
        Map<String, Integer> map;
        switch (this){
            case ALL:
                map = sortByValue(nb.allTimeBounty);
                break;
            case KILLS:
                map = sortByValue(nb.bountiesClaimed);
                break;
            case CLAIMED:
                map = sortByValue(nb.allClaimed);
                break;
            case DEATHS:
                map = sortByValue(nb.bountiesReceived);
                break;
            case SET:
                map = sortByValue(nb.bountiesSet);
                break;
            case IMMUNITY:
                map = sortByValue(nb.immunitySpent);
                break;
            default:
                map = new HashMap<>();
        }
        LinkedHashMap<String, Integer> top = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()){
            if (amount == 0)
                break;
            amount--;
            top.put(entry.getKey(), entry.getValue());
        }
        return top;
    }

    public void displayTopStat(CommandSender sender, int amount){
        NotBounties nb = NotBounties.getInstance();
        if (sender instanceof Player)
            sender.sendMessage(parse(speakings.get(37), (Player) sender));
        else
            sender.sendMessage(parse(speakings.get(37), null));
        boolean useCurrency = this == Leaderboard.IMMUNITY || this == Leaderboard.CLAIMED || this == Leaderboard.ALL;
        LinkedHashMap<String, Integer> map = getTop(0, amount);
        int i = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()){
            OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
            String name = p.getName();
            if (name == null && nb.loggedPlayers.containsValue(entry.getKey())){
                for (Map.Entry<String, String> logged : nb.loggedPlayers.entrySet()){
                    if (logged.getValue().equals(entry.getKey())) {
                        name = logged.getKey();
                        break;
                    }
                    name = "???";
                }
            } else {
                name = "???";
            }
            sender.sendMessage(parseBountyTopString(i, name, entry.getValue(), useCurrency, p));
            i++;
        }
        sender.sendMessage(ChatColor.GRAY + "" + ChatColor.STRIKETHROUGH + "                                                   ");
    }


    private static String parseStats(String text, int amount, boolean useCurrency, OfflinePlayer player){
        text = useCurrency ? text.replaceAll("\\{amount}", currencyPrefix + amount + currencySuffix) : text.replaceAll("\\{amount}", amount + "");

        if (papiEnabled && player != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public static LinkedHashMap<String, Integer> sortByValue(Map<String, Integer> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Integer>> list =
                new LinkedList<>(hm.entrySet());

        // Sort the list
        list.sort((o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));

        // put data from sorted list to hashmap
        LinkedHashMap<String, Integer> temp = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }
    public static LinkedHashMap<String, Integer> sortByName(Map<String, Integer> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Integer>> list =
                new LinkedList<>(hm.entrySet());

        // Sort the list
        list.sort((o1, o2) -> (Objects.requireNonNull(Bukkit.getOfflinePlayer(UUID.fromString(o2.getKey())).getName())).compareTo(Objects.requireNonNull(Bukkit.getOfflinePlayer(UUID.fromString(o1.getKey())).getName())));

        // put data from sorted list to hashmap
        LinkedHashMap<String, Integer> temp = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    public static String parseBountyTopString(int rank, @NotNull String playerName, int amount, boolean useCurrency, OfflinePlayer player){
        String text = speakings.get(36);
        text = text.replaceAll("\\{rank}", rank + "");
        text = text.replaceAll("\\{player}", playerName);
        if (useCurrency)
            text = text.replaceAll("\\{amount}", currencyPrefix + amount + currencySuffix);
        else
            text = text.replaceAll("\\{amount}", amount + "");

        if (papiEnabled && player != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public LinkedHashMap<String, Integer> getSortedList(int skip, int amount, int sortType){
        LinkedHashMap<String, Integer> top = getTop(skip, amount);
        if (sortType == 2)
            return reverseMap(top);
        if (sortType == 3)
            return sortByName(top);
        if (sortType == 4)
            return reverseMap(sortByName(top));
        return top;
    }

    public static LinkedHashMap<String, Integer> reverseMap(LinkedHashMap<String, Integer> map){
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.reverse(keys);
        LinkedHashMap<String, Integer> newMap = new LinkedHashMap<>();
        for (String key : keys){
            newMap.put(key, map.get(key));
        }
        return newMap;
    }
}