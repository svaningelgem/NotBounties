package me.jadenp.notbounties.utils.configuration;

import me.jadenp.notbounties.ui.Head;
import me.jadenp.notbounties.ui.SkinManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public record RewardHead(UUID uuid, double amount) {
    public RewardHead {
        SkinManager.isSkinLoaded(uuid);
    }

    public ItemStack getItem() {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        ItemStack skull = Head.createPlayerSkull(uuid, SkinManager.getSkin(uuid).getUrl());
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        assert skullMeta != null;
        skullMeta.setDisplayName(LanguageOptions.parse(LanguageOptions.getMessage("reward-head-name"), amount, player));
        List<String> lore = new ArrayList<>();
        LanguageOptions.getListMessage("reward-head-lore").forEach(str -> lore.add(LanguageOptions.parse(str, amount, player)));
        skullMeta.setLore(lore);
        skull.setItemMeta(skullMeta);
        return skull;
    }

}
