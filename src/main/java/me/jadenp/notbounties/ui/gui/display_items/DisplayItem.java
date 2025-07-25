package me.jadenp.notbounties.ui.gui.display_items;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DisplayItem {
    /**
     * Get a formatted item for a player.
     * @param player Player to format the item for.
     * @param headName Name of the item.
     * @param lore lore of the item.
     * @return The formatted item.
     */
    ItemStack getFormattedItem(Player player, String headName, List<String> lore, int customModelData, @Nullable String itemModel);
    String parseText(String text, Player player);
}
