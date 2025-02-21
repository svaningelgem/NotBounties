package me.jadenp.notbounties.ui.map;

import me.jadenp.notbounties.data.Bounty;
import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.utils.challenges.ChallengeManager;
import me.jadenp.notbounties.utils.challenges.ChallengeType;
import me.jadenp.notbounties.utils.configuration.ConfigOptions;
import me.jadenp.notbounties.utils.configuration.LanguageOptions;
import me.jadenp.notbounties.utils.configuration.NumberFormatting;
import me.jadenp.notbounties.utils.external_api.LocalTime;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;

import static me.jadenp.notbounties.utils.BountyManager.getBounty;
import static me.jadenp.notbounties.utils.BountyManager.hasBounty;

public class BountyMap implements Listener {

    private static BufferedImage bountyPoster;
    private static BufferedImage deadBounty;
    private static Font playerFont = null;
    public static File posterDirectory;
    private static final Map<UUID, MapView> mapViews = new HashMap<>();
    private static File mapData;

    public static void initialize(){
        posterDirectory = new File(NotBounties.getInstance().getDataFolder() + File.separator + "posters");
        //noinspection ResultOfMethodCallIgnored
        posterDirectory.mkdir();
        if (!new File(posterDirectory + File.separator + "bounty poster.png").exists())
            NotBounties.getInstance().saveResource("posters/bounty poster.png", false);
        if (!new File(posterDirectory + File.separator + "poster template.png").exists())
            NotBounties.getInstance().saveResource("posters/poster template.png", false);
        if (!new File(posterDirectory + File.separator + "dead bounty.png").exists())
            NotBounties.getInstance().saveResource("posters/dead bounty.png", false);
        if (!new File(posterDirectory + File.separator + "READ_ME.txt").exists())
            NotBounties.getInstance().saveResource("posters/READ_ME.txt", false);
        mapData = new File(posterDirectory + File.separator + "mapdata.yml");
        if (!mapData.exists())
            NotBounties.getInstance().saveResource("posters/mapdata.yml", false);

        try {
            BufferedImage poster = ImageIO.read(new File(posterDirectory + File.separator + "bounty poster.png"));;
            if (poster.getWidth() > 128 || poster.getHeight() > 128) {
                poster = downscale(poster, 128, 128);
            }
            bountyPoster = poster;
            BufferedImage dead = ImageIO.read(new File(posterDirectory + File.separator + "dead bounty.png"));
            if (dead.getWidth() > 128 || dead.getHeight() > 128) {
                dead = downscale(dead, 128, 128);
            }
            deadBounty = dead;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[NotBounties] Could not load a poster image.");
            Bukkit.getLogger().warning(e.toString());
        }
    }

    public static BufferedImage getBountyPoster() {
        return bountyPoster;
    }

    public static BufferedImage getDeadBounty() {
        return deadBounty;
    }

    /**
     * Downscale a BufferedImage to the specified width and height.
     * If the aspect ratio of the source image does not match the new dimensions,
     * the image will be squished to fit.
     *
     * @param src         the source BufferedImage
     * @param targetWidth the desired width
     * @param targetHeight the desired height
     * @return a new BufferedImage containing the downscaled image
     */
    public static BufferedImage downscale(BufferedImage src, int targetWidth, int targetHeight) {
        // Create a new image with the target dimensions.
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

        // Get the Graphics2D context of the new image.
        Graphics2D g2d = scaled.createGraphics();

        // Set rendering hints for quality downscaling.
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the original image into the new image, scaling it to fit the target dimensions.
        g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);

        // Dispose the graphics object to free resources.
        g2d.dispose();

        return scaled;
    }

    private static final List<String> posterFiles = Arrays.asList("bounty poster.png", "dead bounty.png", "mapdata.yml", "poster template.png", "READ_ME.txt", "playerfont.ttf", "Name Line.json", "Reward Line.json");
    public static void cleanPosters() {
        int deletedFiles = 0;
        File[] files = posterDirectory.listFiles();
        if (files == null)
            return;
        for (File file : files) {
            if (!posterFiles.contains(file.getName()))
                if (file.delete())
                    deletedFiles++;
        }
        if (deletedFiles > 0)
            Bukkit.getLogger().info("[NotBounties] Cleaned out " + deletedFiles + " poster templates.");
    }

    // method to initialize maps for 1.20.5 & update challenge for holding your own map
    @EventHandler
    public void onMapHold(PlayerItemHeldEvent event) {
        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (newItem == null || !isPoster(newItem) || !ConfigOptions.postersEnabled)
            return;
        MapMeta mapMeta = (MapMeta) newItem.getItemMeta();
        assert mapMeta != null;

        // will have persistent data of uuid if a isPoster returns true
        UUID uuid = UUID.fromString(Objects.requireNonNull(mapMeta.getPersistentDataContainer().get(NotBounties.getNamespacedKey(), PersistentDataType.STRING)));
        if (uuid.equals(event.getPlayer().getUniqueId()))
            ChallengeManager.updateChallengeProgress(uuid, ChallengeType.HOLD_POSTER, 1);

        loadItem(newItem);
    }

    private void loadItem(ItemStack item) {
        if (item == null || !isPoster(item) || !ConfigOptions.postersEnabled)
            return;
        MapMeta mapMeta = (MapMeta) item.getItemMeta();
        assert mapMeta != null;
        MapView mapView = mapMeta.hasMapView() && mapMeta.getMapView() != null ? mapMeta.getMapView() : null;
        UUID uuid = UUID.fromString(Objects.requireNonNull(mapMeta.getPersistentDataContainer().get(NotBounties.getNamespacedKey(), PersistentDataType.STRING)));
        if (mapView == null || !mapView.isVirtual()) {
            if (mapViews.containsKey(uuid)) {
                mapMeta.setMapView(mapViews.get(uuid));
                item.setItemMeta(mapMeta);
            } else {
                if (mapView != null) {
                    mapView.setLocked(ConfigOptions.lockMap);
                    mapView.getRenderers().forEach(mapView::removeRenderer);
                    mapView.addRenderer(new Renderer(uuid));
                } else {
                    mapView = getMapView(uuid);
                    mapMeta.setMapView(mapView);
                    item.setItemMeta(mapMeta);
                }
                mapViews.put(uuid, mapView);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!ConfigOptions.postersEnabled)
            return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getType() == EntityType.ITEM_FRAME || (NotBounties.getServerVersion() >= 17 && entity.getType() == EntityType.GLOW_ITEM_FRAME)) {
                ItemFrame itemFrame = (ItemFrame) entity;
                loadItem(itemFrame.getItem());
            }
        }
    }

    // craft maps with a player skull
    // check for tracking crafting
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        // may have to cancel event or set result to null instead of returning or may have to listen to the craft event
        if (!ConfigOptions.craftPoster || NotBounties.isPaused() || !ConfigOptions.postersEnabled)
            return;
        ItemStack[] matrix = event.getInventory().getMatrix();
        boolean hasMap = false;
        ItemStack head = null;
        for (ItemStack itemStack : matrix) {
            if (itemStack == null)
                continue;
            if (itemStack.getType() == Material.MAP) {
                if (!hasMap) {
                    hasMap = true;
                } else {
                    // already a map in previous slot
                    return;
                }
            } if (itemStack.getType() == Material.PLAYER_HEAD) {
                if (head == null) {
                    head = itemStack;
                } else {
                    // already a head in previous slot
                    return;
                }
            }
        }
        if (head == null || !hasMap)
            // not enough requirements
            return;
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        assert meta != null;
        if (meta.getOwningPlayer() == null) {
            NotBounties.debugMessage("Player head in crafting matrix has a null owner!", false);
            return;
        }
        UUID trackedPlayer = meta.getOwningPlayer().getUniqueId();
        ItemStack map = hasBounty(trackedPlayer) ? getMap(Objects.requireNonNull(getBounty(trackedPlayer))) : getMap(trackedPlayer, 0, System.currentTimeMillis());
        event.getInventory().setResult(map);
    }
    // complete tracker crafting
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!ConfigOptions.craftPoster || !(event.getInventory() instanceof CraftingInventory inventory) || NotBounties.isPaused() || !ConfigOptions.postersEnabled)
            return;
        if (inventory.getResult() == null || inventory.getResult().getType() != Material.FILLED_MAP)
            return;
        if (event.getRawSlot() == 0) {
            event.setCancelled(true);
        } else {
            return;
        }
        // update result
        int amountCrafted = 1;
        switch (event.getAction()){
            case PICKUP_ONE,PICKUP_ALL,PICKUP_HALF,PICKUP_SOME:
                new BukkitRunnable() {

                    final int previousAmount = event.getCursor() != null ? event.getCursor().getAmount() : 0;
                    final ItemStack result = inventory.getResult();
                    @Override
                    public void run() {
                        assert result != null;
                        result.setAmount(previousAmount + 1);
                        event.getWhoClicked().getOpenInventory().setCursor(result);
                    }
                }.runTaskLater(NotBounties.getInstance(), 1);
                break;
            case DROP_ONE_CURSOR,DROP_ALL_CURSOR,DROP_ALL_SLOT,DROP_ONE_SLOT:
                event.getWhoClicked().getWorld().dropItem(event.getWhoClicked().getEyeLocation(), inventory.getResult());
                break;
            case MOVE_TO_OTHER_INVENTORY:
                int minAmount = 65;
                ItemStack[] matrix = inventory.getMatrix();
                for (ItemStack itemStack : matrix) {
                    if (itemStack != null) {
                        if (itemStack.getAmount() < minAmount) {
                            minAmount = itemStack.getAmount();
                        }
                    }
                }
                NumberFormatting.givePlayer((Player) event.getWhoClicked(), inventory.getResult(), minAmount);
                amountCrafted = minAmount;
                break;
            case HOTBAR_MOVE_AND_READD,HOTBAR_SWAP:
                // hit a number button to move to a hotbar slot - slot must be empty
                if (event.getWhoClicked().getOpenInventory().getBottomInventory().getItem(event.getHotbarButton()) == null)
                    event.getWhoClicked().getOpenInventory().getBottomInventory().setItem(event.getHotbarButton(), inventory.getResult());
                break;
            default:
                return;
        }
        // update matrix
        boolean changed = false;
        ItemStack[] matrix = inventory.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] != null) {
                if (matrix[i].getAmount() > amountCrafted) {
                    matrix[i].setAmount(matrix[i].getAmount() - amountCrafted);
                } else {
                    matrix[i] = null;
                    changed = true;
                }
            }
        }
        inventory.setMatrix(matrix);
        if (changed)
            inventory.setResult(null);

    }

    // wash trackers
    @EventHandler
    public void onPlayerItemDrop(PlayerDropItemEvent event) {
        if (!ConfigOptions.washPoster || !ConfigOptions.postersEnabled || !isPoster(event.getItemDrop().getItemStack()) || NotBounties.isPaused())
            return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!event.getItemDrop().isValid())
                    return;
                ItemStack map = new ItemStack(Material.MAP, event.getItemDrop().getItemStack().getAmount());
                if ((NotBounties.getServerVersion() < 17 && event.getItemDrop().getLocation().getBlock().getType() == Material.CAULDRON) || (NotBounties.getServerVersion() >= 17 && event.getItemDrop().getLocation().getBlock().getType() == Material.WATER_CAULDRON)) {
                    event.getItemDrop().getWorld().dropItem(event.getItemDrop().getLocation(), map);
                    event.getItemDrop().remove();
                }
            }
        }.runTaskLater(NotBounties.getInstance(), 40);
    }

    public static boolean isPoster(ItemStack itemStack) {
        if (itemStack.getType() != Material.FILLED_MAP)
            return false;
        MapMeta meta = (MapMeta) itemStack.getItemMeta();
        if (meta == null)
            return false;

        return meta.getPersistentDataContainer().has(NotBounties.getNamespacedKey());

    }

    public static void loadFont() {
        if (new File(posterDirectory + File.separator + "playerfont.ttf").exists()) {
            try {
                playerFont = Font.createFont(Font.TRUETYPE_FONT, new File(BountyMap.posterDirectory + File.separator + "playerfont.ttf"));
            } catch (Throwable throwable) {
                Bukkit.getLogger().warning("[NotBounties] Detected a custom font, but an error occurred when reading it! This may be because of a corrupted font file or an invalid java font configuration file.");
                Bukkit.getLogger().warning("Error: " + throwable.getMessage());
                Bukkit.getLogger().warning("Reverting to default font.");
                playerFont = new Font("Serif", Font.PLAIN, 20);
            }
        } else {
            playerFont = new Font("Serif", Font.PLAIN, 20);
        }

    }

    public static Font getPlayerFont(float fontSize, boolean bold) {
        if (bold)
            return playerFont.deriveFont(fontSize).deriveFont(Collections.singletonMap(TextAttribute.WEIGHT, TextAttribute.WEIGHT_ULTRABOLD));
        return playerFont.deriveFont(fontSize);
    }

    public static ItemStack getMap(Bounty bounty) {
        return getMap(bounty.getUUID(), bounty.getTotalDisplayBounty(), bounty.getLatestUpdate());
    }

    public static ItemStack getMap(UUID uuid, double displayBounty, long updateTime) {
        OfflinePlayer parser = Bukkit.getOfflinePlayer(uuid);
        MapView mapView = getMapView(uuid);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        assert meta != null;
        try {
            meta.setMapView(mapView);
        } catch (NullPointerException e) {
            // error with the map id
            NotBounties.debugMessage("Error setting map view for poster!", true);
            NotBounties.debugMessage(e.toString(), true);
            return null;
        }
        meta.setDisplayName(LanguageOptions.parse(LanguageOptions.getMessage("map-name"), displayBounty, parser));
        ArrayList<String> lore = new ArrayList<>();
        for (String str : LanguageOptions.getListMessage("map-lore")) {
            lore.add(LanguageOptions.parse(str, displayBounty, updateTime, LocalTime.TimeFormat.SERVER, parser));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.values()[5]);
        meta.getPersistentDataContainer().set(NotBounties.getNamespacedKey(), PersistentDataType.STRING, uuid.toString());
        mapItem.setItemMeta(meta);
        return mapItem;
    }

    public static MapView getMapView(UUID uuid) {
        MapView mapView;
        if (mapViews.containsKey(uuid)) {
            mapView = mapViews.get(uuid);
        } else {
            mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
            mapViews.put(uuid, mapView);
        }
        mapView.setUnlimitedTracking(false);
        mapView.getRenderers().forEach(mapView::removeRenderer);
        mapView.setLocked(ConfigOptions.lockMap);
        mapView.setTrackingPosition(false);
        mapView.addRenderer(new Renderer(uuid));
        return mapView;
    }

    public static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPreMultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPreMultiplied, null);
    }


}
