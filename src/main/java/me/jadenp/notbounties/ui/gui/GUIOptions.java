package me.jadenp.notbounties.ui.gui;

import me.jadenp.notbounties.Leaderboard;
import me.jadenp.notbounties.data.Whitelist;
import me.jadenp.notbounties.ui.HeadFetcher;
import me.jadenp.notbounties.ui.QueuedHead;
import me.jadenp.notbounties.ui.gui.display_items.DisplayItem;
import me.jadenp.notbounties.ui.gui.display_items.PlayerItem;
import me.jadenp.notbounties.utils.DataManager;
import me.jadenp.notbounties.features.challenges.ChallengeManager;
import me.jadenp.notbounties.features.ConfigOptions;
import me.jadenp.notbounties.features.settings.money.NumberFormatting;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static me.jadenp.notbounties.features.LanguageOptions.*;

public class GUIOptions {
    private final List<Integer> playerSlots; // this size of this is how many player slots per page
    private final int size;
    private final int sortType;
    private final CustomItem[] customItems;
    private final String name;
    private final boolean removePageItems;
    private final String headName;
    private final List<String> headLore;
    private final boolean addPage;
    private final String type;
    private final Map<Integer, CustomItem> pageReplacements = new HashMap<>();
    private final InventoryType inventoryType;
    private final int customModelData;
    private final String itemModel;

    public GUIOptions(ConfigurationSection settings) {
        InventoryType inventoryType1;
        type = settings.getName();
        name = settings.isSet("gui-name") ? color(settings.getString("gui-name")) : "Custom GUI";
        playerSlots = new ArrayList<>();
        if (settings.isString("inventory-type")) {
            try {
                inventoryType1 = InventoryType.valueOf(Objects.requireNonNull(settings.getString("inventory-type")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[NotBounties] Invalid inventory type \"" + settings.getString("inventory-type") + "\" for gui: " + type);
                inventoryType1 = InventoryType.CHEST;
            }
        } else {
            inventoryType1 = InventoryType.CHEST;
        }
        inventoryType = inventoryType1;
        if (inventoryType == InventoryType.CHEST) {
            size = settings.isSet("size") ? settings.getInt("size") : 54;
        } else {
            size = inventoryType.getDefaultSize();
        }
        addPage = settings.isSet("add-page") && settings.getBoolean("add-page");
        sortType = settings.isSet("sort-type") ? settings.getInt("sort-type") : 1;
        removePageItems = settings.getBoolean("remove-page-items");
        headName = settings.isSet("head-name") ? settings.getString("head-name") : "{player}";
        headLore = settings.isSet("head-lore") ? settings.getStringList("head-lore") : new ArrayList<>();
        customModelData = settings.isSet("custom-model-data") ? settings.getInt("custom-model-data") : -1;
        itemModel = settings.isSet("item-model") ? settings.getString("item-model") : null;
        customItems = new CustomItem[size];
        if (settings.isConfigurationSection("layout"))
            for (String key : Objects.requireNonNull(settings.getConfigurationSection("layout")).getKeys(false)) {
                String item = settings.getString("layout." + key + ".item");
                int[] slots = new int[0];
                if (settings.isList("layout." + key + ".slot")) {
                    List<String> slotsList = settings.getStringList("layout." + key + ".slot");
                    for (String slot : slotsList) {
                        int[] newSlots = getRange(slot);
                        int[] tempSlots = new int[slots.length + newSlots.length];
                        System.arraycopy(slots, 0, tempSlots, 0, slots.length);
                        System.arraycopy(newSlots, 0, tempSlots, slots.length, newSlots.length);
                        slots = tempSlots;
                    }
                } else {
                    slots = getRange(settings.getString("layout." + key + ".slot"));
                }

                if (GUI.getCustomItems().containsKey(item)) {
                    CustomItem customItem = GUI.getCustomItems().get(item);
                    for (int i : slots) {
                        if (i >= customItems.length) {
                            Bukkit.getLogger().warning("[NotBounties] Error loading custom item in GUI: " + type);
                            Bukkit.getLogger().warning("Slot " + i + " is bigger than the inventory size! (max=" + (size-1) + ")");
                            continue;
                        }

                        if (customItems[i] != null) {
                            if (getPageType(customItem.getCommands()) > 0) {
                                pageReplacements.put(i, customItems[i]);
                            }
                        }
                        customItems[i] = customItem;
                    }
                } else {
                    // unknown item
                    Bukkit.getLogger().warning(() -> "Unknown item \"" + item + "\" in gui: " + settings.getName());
                }

            }
        for (String bSlots : settings.getStringList("player-slots")) {
            int[] range = getRange(bSlots);
            for (int j : range) {
                playerSlots.add(j);
            }
        }
    }

    public boolean isAddPage() {
        return addPage;
    }

    public CustomItem getCustomItem(int slot, long page, int entryAmount) {
        // Check if slot is in bounds (-999 is a click outside the GUI)
        if (slot >= customItems.length || slot < 0 || customItems[slot] == null)
            return null;
        CustomItem item = customItems[slot];
        if (!type.equals("select-price") && !type.equals("bounty-hunt-time")) {
            // next
            if (getPageType(item.getCommands()) == 1 && page * playerSlots.size() >= entryAmount) {
                if (pageReplacements.containsKey(slot))
                    return pageReplacements.get(slot);
                return null;
            }
            // back
            if (getPageType(item.getCommands()) == 2 && page == 1) {
                if (pageReplacements.containsKey(slot))
                    return pageReplacements.get(slot);
                return null;
            }
        }
        return item;
    }

    /**
     * Get the formatted custom inventory
     *
     * @param player Player that owns the inventory and will be parsed for any placeholders
     * @param page   Page of gui - This will change the items in player slots and page items if enabled
     * @return Custom GUI Inventory
     */
    public Inventory createInventory(Player player, long page, long maxPage, List<DisplayItem> displayItems, String title, Object[] data) {
        if (page < 1) {
            page = 1;
        }
        Inventory inventory = inventoryType == InventoryType.CHEST ? Bukkit.createInventory(player, size, title) : Bukkit.createInventory(player, inventoryType, title);
        ItemStack[] contents = inventory.getContents();
        String[] replacements;
        if (data.length == 0) {
            replacements = new String[]{"", "", "", "", page + "", maxPage + ""};
        } else if (type.equals("leaderboard") && data[0] instanceof String string) {
            try {
                Leaderboard leaderboard = Leaderboard.valueOf(string.toUpperCase());
                replacements = new String[]{leaderboard.toString(), leaderboard.getDisplayName(), "", "", page + "", maxPage + ""};
            } catch (IllegalArgumentException ignored) {
                replacements = new String[] {string, string, "", "", page + "", maxPage + ""};
            }

        } else if (type.equals("select-price") || type.equals("confirm-bounty") || type.equals("bounty-hunt-time")) {
            double tax = page * ConfigOptions.getMoney().getBountyTax() + DataManager.getPlayerData(player.getUniqueId()).getWhitelist().getList().size() * Whitelist.getCost();
            double total = page + tax;
            replacements = new String[]{"",
                    NumberFormatting.getCurrencyPrefix() + NumberFormatting.formatNumber(page) + NumberFormatting.getCurrencySuffix(), // amount
                    NumberFormatting.getCurrencyPrefix() + NumberFormatting.formatNumber(tax) + NumberFormatting.getCurrencySuffix(), // tax
                    NumberFormatting.getCurrencyPrefix() + NumberFormatting.formatNumber(total) + NumberFormatting.getCurrencySuffix(), // amount_tax
                    page + "", maxPage + ""
            };
        } else if (type.equals("confirm") && data[0] instanceof Number number) {
            String amount = NumberFormatting.getCurrencyPrefix() + NumberFormatting.formatNumber(number.doubleValue()) + NumberFormatting.getCurrencySuffix();
            replacements = new String[]{"",
                    amount, // amount
                    amount, // tax
                    amount, // amount_tax
                    page + "", maxPage + ""
            };
        } else {
            replacements = new String[]{"", "", "", "", page + "", maxPage + ""};
        }
        // set up regular items
        for (int i = 0; i < contents.length; i++) {
            if (customItems[i] == null)
                continue;
            // check if item is a page item
            if (removePageItems) {
                // next
                if (getPageType(customItems[i].getCommands()) == 1 && page * playerSlots.size() >= displayItems.size()) {
                    ItemStack replacement = pageReplacements.containsKey(i) ? pageReplacements.get(i).getFormattedItem(player, replacements) : null;
                    contents[i] = replacement;
                    continue;
                }
                // back
                if (getPageType(customItems[i].getCommands()) == 2 && page == 1) {
                    ItemStack replacement = pageReplacements.containsKey(i) ? pageReplacements.get(i).getFormattedItem(player, replacements) : null;
                    contents[i] = replacement;
                    continue;
                }
            }
            contents[i] = customItems[i].getFormattedItem(player, replacements);
        }
        // set up player slots
        List<QueuedHead> unloadedHeads = new ArrayList<>();
        boolean isSinglePlayerSlot = type.equals("select-price") || type.equals("confirm-bounty") || type.equals("bounty-item-select") || type.equals("challenges") || type.equals("bounty-hunt-time");
        for (int i = isSinglePlayerSlot ? 0 : (int) ((page - 1) * playerSlots.size()); i < Math.min(playerSlots.size() * page, displayItems.size()); i++) {
            DisplayItem displayItem = displayItems.get(i);
            ItemStack item = displayItem.getFormattedItem(player, headName, headLore, customModelData, itemModel);
            int slot = isSinglePlayerSlot ? playerSlots.get(i) : (playerSlots.get((int) (i - playerSlots.size() * (page-1))));

            if (displayItem instanceof PlayerItem playerItem)
                unloadedHeads.add(new QueuedHead(playerItem.getUuid(), item, slot));
            contents[slot] = item;
        }
        if (type.equals("challenges")) {
            ItemStack[] items = ChallengeManager.getDisplayItems(player);
            for (int i = 0; i < Math.min(playerSlots.size(), items.length); i++) {
                contents[playerSlots.get(i)] = items[i];
            }
        } else {
            new HeadFetcher().loadHeads(player, new PlayerGUInfo(page, maxPage, type, data, displayItems, title), unloadedHeads);
        }

        if  (type.equals("bounty-item-select") && data.length > 1 && data[1] instanceof ItemStack[][] items && items.length >= page) {
            // load in saved items
            ItemStack[] savedContents = items[(int) (page-1)];

            for (int i = 0; i < savedContents.length; i++) {
                contents[playerSlots.get(i+1)] = savedContents[i];
            }
        }

        inventory.setContents(contents);
        return inventory;

    }


    public int getSortType() {
        return sortType;
    }

    public List<Integer> getPlayerSlots() {
        return playerSlots;
    }

    public String getName() {
        return name;
    }

    /**
     * Get an array of desired numbers from a string from (x)-(y). Both x and y are inclusive.
     * <p>"1" -> [1]</p>
     * <p>"3-6" -> [3, 4, 5, 6]</p>
     *
     * @param str String to parse
     * @return desired range of numbers sorted numerically or an empty list if there is a formatting error
     */
    private static int[] getRange(String str) {
        try {
            // check if it is a single number
            int i = Integer.parseInt(str);
            // return if an exception was not thrown
            return new int[]{i};
        } catch (NumberFormatException e) {
            // there is a dash we need to get out
        }
        String[] split = str.split("-");
        try {
            int bound1 = Integer.parseInt(split[0]);
            int bound2 = Integer.parseInt(split[1]);
            int[] result = new int[Math.abs(bound1 - bound2) + 1];

            for (int i = Math.min(bound1, bound2); i < Math.max(bound1, bound2) + 1; i++) {
                result[i - Math.min(bound1, bound2)] = i;
            }
            return result;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // formatting error
            return new int[0];
        }
    }

    /**
     * Parses through commands for "[next]" and "[back]"
     *
     * @param commands Commands of the CustomItem
     * @return 1 for next, 2 for back, 0 for no page item
     */
    public static int getPageType(List<String> commands) {
        for (String command : commands) {
            if (command.startsWith("[next]"))
                return 1;
            if (command.startsWith("[back]"))
                return 2;
        }
        return 0;
    }

    public String getType() {
        return type;
    }

    public InventoryType getInventoryType() {
        return inventoryType;
    }
}

