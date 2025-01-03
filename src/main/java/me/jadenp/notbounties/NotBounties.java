package me.jadenp.notbounties;

import me.jadenp.notbounties.databases.AsyncDatabaseWrapper;
import me.jadenp.notbounties.databases.proxy.ProxyMessaging;
import me.jadenp.notbounties.ui.BountyTracker;
import me.jadenp.notbounties.ui.Commands;
import me.jadenp.notbounties.ui.Events;
import me.jadenp.notbounties.ui.SkinManager;
import me.jadenp.notbounties.ui.gui.GUI;
import me.jadenp.notbounties.ui.map.BountyBoard;
import me.jadenp.notbounties.ui.map.BountyMap;
import me.jadenp.notbounties.utils.*;
import me.jadenp.notbounties.utils.challenges.ChallengeListener;
import me.jadenp.notbounties.utils.challenges.ChallengeManager;
import me.jadenp.notbounties.utils.configuration.*;
import me.jadenp.notbounties.utils.configuration.autoBounties.MurderBounties;
import me.jadenp.notbounties.utils.configuration.autoBounties.RandomBounties;
import me.jadenp.notbounties.utils.configuration.autoBounties.TimedBounties;
import me.jadenp.notbounties.utils.configuration.webhook.WebhookOptions;
import me.jadenp.notbounties.utils.externalAPIs.*;
import me.jadenp.notbounties.utils.externalAPIs.bedrock.FloodGateClass;
import me.jadenp.notbounties.utils.externalAPIs.bedrock.GeyserMCClass;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static me.jadenp.notbounties.utils.BountyManager.*;
import static me.jadenp.notbounties.utils.configuration.ConfigOptions.*;
import static me.jadenp.notbounties.utils.configuration.LanguageOptions.*;
import static me.jadenp.notbounties.utils.configuration.NumberFormatting.vaultEnabled;

/**
 * Proxy Messaging
 * Save to json file instead of yaml
 * Folia
 * Team bounties
 */
public final class NotBounties extends JavaPlugin {

    public static List<String> immunePerms = new ArrayList<>();
    public static List<String> autoImmuneMurderPerms = new ArrayList<>();
    public static List<String> autoImmuneRandomPerms = new ArrayList<>();
    public static List<String> autoImmuneTimedPerms = new ArrayList<>();
    public static final List<UUID> disableBroadcast = new ArrayList<>();
    /**
     * Player UUID, Whitelist UUIDs
     */
    public static final Map<UUID, Whitelist> playerWhitelist = new HashMap<>();
    public static final Map<String, Long> repeatBuyCommand = new HashMap<>();
    public static final Map<String, Long> repeatBuyCommand2 = new HashMap<>();


    public static final List<UUID> displayParticle = new ArrayList<>();
    public static final Map<UUID, WantedTags> wantedText = new HashMap<>();
    private static NotBounties instance;
    public static boolean latestVersion = true;
    public static final List<BountyBoard> bountyBoards = new ArrayList<>();
    public static final Map<UUID, Integer> boardSetup = new HashMap<>();
    public static long lastBountyBoardUpdate = System.currentTimeMillis();
    public static final String sessionKey = UUID.randomUUID().toString();
    public static NamespacedKey namespacedKey;
    public static int serverVersion = 20;
    public static int serverSubVersion = 0;
    public static boolean debug = false;
    private static boolean paused = false;
    private static Events events;

    @Override
    public void onLoad() {
        instance = this;
        // register api flags
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null)
            new WorldGuardClass().registerFlags();
        if (getServer().getPluginManager().getPlugin("Lands") != null)
            new LandsClass().registerClaimFlag();
    }


    @Override
    public void onEnable() {
        Random random = new Random(System.currentTimeMillis());
        // Plugin startup logic
        if (random.nextInt(10) == 3) {
            // 10% chance to receive this Easter egg
            String display = """
                    
                    ████████████████████████████████████████████████████████████████████
                    █▄─▀█▄─▄█─▄▄─█─▄─▄─█▄─▄─▀█─▄▄─█▄─██─▄█▄─▀█▄─▄█─▄─▄─█▄─▄█▄─▄▄─█─▄▄▄▄█
                    ██─█▄▀─██─██─███─████─▄─▀█─██─██─██─███─█▄▀─████─████─███─▄█▀█▄▄▄▄─█
                    ▀▄▄▄▀▀▄▄▀▄▄▄▄▀▀▄▄▄▀▀▄▄▄▄▀▀▄▄▄▄▀▀▄▄▄▄▀▀▄▄▄▀▀▄▄▀▀▄▄▄▀▀▄▄▄▀▄▄▄▄▄▀▄▄▄▄▄▀""";
            Bukkit.getLogger().info(display);
        }

        // load commands and events
        Objects.requireNonNull(this.getCommand("notbounties")).setExecutor(new Commands());
        events = new Events();
        Bukkit.getServer().getPluginManager().registerEvents(events, this);
        Bukkit.getServer().getPluginManager().registerEvents(new GUI(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new CurrencySetup(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new BountyMap(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PVPRestrictions(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new WebhookOptions(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new Prompt(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new BountyTracker(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new ChallengeListener(), this);

        this.saveDefaultConfig();


        BountyMap.initialize();
        namespacedKey = new NamespacedKey(this, "bounty-entity");

        try {
            // get the text version - ex: 1.20.3
            String fullServerVersion = Bukkit.getBukkitVersion().substring(0, Bukkit.getBukkitVersion().indexOf("-"));
            fullServerVersion = fullServerVersion.substring(2); // remove the '1.' in the version
            if (fullServerVersion.contains(".")) {
                // get the subversion - ex: 3
                serverSubVersion = Integer.parseInt(fullServerVersion.substring(fullServerVersion.indexOf(".") + 1));
                fullServerVersion = fullServerVersion.substring(0, fullServerVersion.indexOf(".")); // remove the subversion
            }
            serverVersion = Integer.parseInt(fullServerVersion);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            Bukkit.getLogger().warning("[NotBounties] Could not get the server version. Some features may not function properly.");
            serverVersion = 20;
            serverSubVersion = 0;
        }

        Bukkit.getServer().getPluginManager().registerEvents(new RemovePersistentEntitiesEvent(), this);


        try {
            loadConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (ChallengeManager.isEnabled()) {
            try {
                ChallengeManager.readChallengeData();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        DataManager.loadBounties();

        if (sendBStats) {
            int pluginId = 20776;
            Metrics metrics = new Metrics(this, pluginId);
            metrics.addCustomChart(new Metrics.SingleLineChart("active_bounties", new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return BountyManager.getAllBounties(-1).size();
                }
            }));
        }


        // clean out posters that aren't supposed to be there
        if (!saveTemplates) {
            BountyMap.cleanPosters();
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BountyExpansion().register();
        }

        // load skins for bounties
        for (Bounty bounty : BountyManager.getAllBounties(-1))
            SkinManager.saveSkin(bounty.getUUID());

        // register plugin messaging to a proxy
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "notbounties:main");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "notbounties:main", new ProxyMessaging());

        // force login players that are already on the server - this will happen if the plugin is loaded without a restart
        for (Player player : Bukkit.getOnlinePlayers()) {
            Events.login(player);
        }

        // update checker
        if (updateNotification) {
            new UpdateChecker(this, 104484).getVersion(version -> {
                if (getDescription().getVersion().contains("dev"))
                    return;
                if (getDescription().getVersion().equals(version))
                    return;
                // split the numbers
                String[] versionSplit = version.split("\\.");
                String[] currentSplit = getDescription().getVersion().split("\\.");
                // convert to integers
                int[] versionNumbers = new int[versionSplit.length];
                for (int i = 0; i < versionSplit.length; i++) {
                    try {
                        versionNumbers[i] = Integer.parseInt(versionSplit[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                int[] currentNumbers = new int[currentSplit.length];
                for (int i = 0; i < currentNumbers.length; i++) {
                    try {
                        currentNumbers[i] = Integer.parseInt(currentSplit[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                boolean needsUpdate = false;
                for (int i = 0; i < currentNumbers.length; i++) {
                    if (currentNumbers[i] < versionNumbers[i]) {
                        needsUpdate = true;
                        break;
                    }
                }
                if (!needsUpdate && currentNumbers.length < versionNumbers.length)
                    needsUpdate = true;
                latestVersion = !needsUpdate;

                if (needsUpdate) {
                    Bukkit.getLogger().info("[NotBounties] " + parse(getMessage("update-notification").replace("{current}", NotBounties.getInstance().getDescription().getVersion()).replace("{latest}", version), null));
                }
            });
        }

        // make bounty tracker work & big bounty particle & time immunity
        new BukkitRunnable() {
            List<BountyBoard> queuedBoards = new ArrayList<>();

            @Override
            public void run() {
                if (paused)
                    return;
                // update bounty tracker
                BountyTracker.update();

                // big bounty particle
                if (BigBounty.getThreshold() != -1) {
                    if (BigBounty.isParticle())
                        for (UUID uuid : displayParticle) {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                            if (player.isOnline()) {
                                for (Player viewer : Bukkit.getOnlinePlayers()) {
                                    if ((NotBounties.serverVersion >= 17 && viewer.canSee(Objects.requireNonNull(player.getPlayer()))) && viewer.getWorld().equals(player.getPlayer().getWorld()) && viewer.getLocation().distance(player.getPlayer().getLocation()) < 256 && !isVanished(player.getPlayer()))
                                        viewer.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getPlayer().getEyeLocation().add(0, 1, 0), 0, 0, 0, 0);
                                }
                            }
                        }
                }

                Immunity.update();
                RandomBounties.update();
                TimedBounties.update();

                PVPRestrictions.checkCombatExpiry();
                ChallengeManager.checkChallengeChange();

                if (lastBountyBoardUpdate + boardUpdate * 1000 < System.currentTimeMillis() && !Bukkit.getOnlinePlayers().isEmpty()) {
                    // update bounty board
                    if (queuedBoards.isEmpty()) {
                        queuedBoards = new ArrayList<>(bountyBoards);
                    }
                    int minUpdate = boardStaggeredUpdate == 0 ? queuedBoards.size() : boardStaggeredUpdate;
                    List<Bounty> bountyCopy = getPublicBounties(boardType);
                    for (int i = 0; i < Math.min(queuedBoards.size(), minUpdate); i++) {
                        BountyBoard board = queuedBoards.get(i);
                        if (bountyCopy.size() >= board.getRank()) {
                            board.update(bountyCopy.get(board.getRank() - 1));
                        } else {
                            board.update(null);
                        }
                    }
                    if (Math.min(queuedBoards.size(), minUpdate) > 0) {
                        queuedBoards.subList(0, Math.min(queuedBoards.size(), minUpdate)).clear();
                    }
                    lastBountyBoardUpdate = System.currentTimeMillis();
                }

            }
        }.runTaskTimer(this, 100, 40);
        // auto save bounties & do some ram cleaning
        new BukkitRunnable() {
            @Override
            public void run() {
                if (paused)
                    return;
                MurderBounties.cleanPlayerKills();
                // if they have expire-time enabled
                BountyExpire.removeExpiredBounties();

                SkinManager.removeOldData();

                RemovePersistentEntitiesEvent.checkRemovedEntities();

                save();
                try {
                    BountyMap.save();
                } catch (IOException e) {
                    Bukkit.getLogger().severe(e.toString());
                }


            }
        }.runTaskTimerAsynchronously(this, autoSaveInterval * 60 * 20L + 69, autoSaveInterval * 60 * 20L);
        // Check for banned players
        //         * Runs every hour and will check a few players at a time
        //         * Every player will be guaranteed to be checked after 12 hours
        new BukkitRunnable() {
            List<Bounty> bountyListCopy = new ArrayList<>();
            int playersPerRun = 1;

            @Override
            public void run() {
                if (!removeBannedPlayers || paused)
                    return;
                if (bountyListCopy.isEmpty()) {
                    bountyListCopy = BountyManager.getAllBounties(-1);
                    playersPerRun = bountyListCopy.size() / 12 + 1;
                }
                for (int i = 0; i < playersPerRun; i++) {
                    if (i >= bountyListCopy.size())
                        break;
                    Bounty bounty = bountyListCopy.get(i);
                    OfflinePlayer player = Bukkit.getOfflinePlayer(bounty.getUUID());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isBanned() || (liteBansEnabled && !(new LiteBansClass().isPlayerNotBanned(bounty.getUUID())))) {
                                BountyManager.removeBounty(bounty.getUUID());
                            }
                        }
                    }.runTaskAsynchronously(NotBounties.getInstance());
                }
                if (playersPerRun > 0) {
                    if (bountyListCopy.size() > playersPerRun)
                        bountyListCopy.subList(0, playersPerRun).clear();
                    else
                        bountyListCopy.clear();
                }
            }
        }.runTaskTimer(NotBounties.getInstance(), 101, 72006);

        // wanted text
        new BukkitRunnable() {
            static final int maxUpdateTime = 10;
            int lastUpdateTime = 0; // time it took for the stands to update last
            long lastRunTime = 0;
            @Override
            public void run() {
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    if (lastUpdateTime > maxUpdateTime) {
                        //    (the amount of ms since last update)      (2 x the amount of ms last update took)
                        if (System.currentTimeMillis() - lastRunTime < (lastUpdateTime - maxUpdateTime) * 2L) {
                            return;
                        }
                    }
                    long startTime = System.currentTimeMillis();
                    for (Map.Entry<UUID, WantedTags> entry : wantedText.entrySet()) {
                        entry.getValue().updateArmorStand();
                    }
                    lastUpdateTime = (int) (System.currentTimeMillis() - startTime);
                    if (lastUpdateTime > maxUpdateTime) {
                        lastRunTime = System.currentTimeMillis();
                        if (debug)
                            Bukkit.getLogger().info("[NotBountiesDebug] Took " + lastUpdateTime + "ms to update wanted tags. Pausing for a few updates.");
                    }
                }
            }
        }.runTaskTimer(this, 20, 1);

    }


    public static NotBounties getInstance() {
        return instance;
    }


    private void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("immune-permissions", immunePerms);
        configuration.set("immunity-murder", autoImmuneMurderPerms);
        configuration.set("immunity-random", autoImmuneMurderPerms);
        configuration.set("immunity-timed", autoImmuneMurderPerms);
        for (Map.Entry<UUID, String> entry : LoggedPlayers.getLoggedPlayers().entrySet()) {
            // uuid: name
            configuration.set("logged-players." + entry.getKey(), entry.getValue());
        }
            int i = 0;
            for (Bounty bounty : DataManager.getLocalBounties()) {
                configuration.set("bounties." + i + ".uuid", bounty.getUUID().toString());
                configuration.set("bounties." + i + ".name", bounty.getName());
                int f = 0;
                for (Setter setters : bounty.getSetters()) {
                    configuration.set("bounties." + i + "." + f + ".name", setters.getName());
                    configuration.set("bounties." + i + "." + f + ".uuid", setters.getUuid().toString());
                    configuration.set("bounties." + i + "." + f + ".amount", setters.getAmount());
                    configuration.set("bounties." + i + "." + f + ".time-created", setters.getTimeCreated());
                    configuration.set("bounties." + i + "." + f + ".notified", setters.isNotified());
                    List<String> whitelist = setters.getWhitelist().getList().stream().map(UUID::toString).toList();
                    configuration.set("bounties." + i + "." + f + ".whitelist", whitelist);
                    configuration.set("bounties." + i + "." + f + ".blacklist", setters.getWhitelist().isBlacklist());
                    configuration.set("bounties." + i + "." + f + ".playtime", setters.getReceiverPlaytime());
                    if (!setters.getItems().isEmpty())
                        configuration.set("bounties." + i + "." + f + ".items", SerializeInventory.itemStackArrayToBase64(setters.getItems().toArray(new ItemStack[0])));
                    f++;
                }
                i++;
            }
            // save stats
            for (Map.Entry<UUID, PlayerStat> entry : DataManager.getLocalStats()) {
                String uuid = entry.getKey().toString();
                PlayerStat stat = entry.getValue();
                if (stat.kills() != 0.0)
                    configuration.set("data." + uuid + ".kills", stat.kills());
                if (stat.set() != 0.0)
                    configuration.set("data." + uuid + ".set", stat.set());
                if (stat.deaths() != 0.0)
                    configuration.set("data." + uuid + ".deaths", stat.deaths());
                if (stat.all() != 0.0)
                    configuration.set("data." + uuid + ".all-time", stat.all());
                if (stat.immunity() != 0.0)
                    configuration.set("data." + uuid + ".immunity", stat.immunity());
                if (stat.claimed() != 0.0)
                    configuration.set("data." + uuid + ".all-claimed", stat.claimed());
            }

        for (Map.Entry<UUID, Long> entry : TimedBounties.getNextBounties().entrySet()) {
            configuration.set("data." + entry.getKey() + ".next-bounty", entry.getValue());
        }
        for (Map.Entry<UUID, TimeZone> entry : LocalTime.getSavedTimeZones().entrySet()) {
            configuration.set("data." + entry.getKey() + ".time-zone", entry.getValue().getID());
        }
        if (variableWhitelist) {
            for (Map.Entry<UUID, Whitelist> mapElement : playerWhitelist.entrySet()) {
                List<String> stringList = mapElement.getValue().getList().stream().map(UUID::toString).toList();
                configuration.set("data." + mapElement.getKey().toString() + ".whitelist", stringList);
                if (mapElement.getValue().isBlacklist())
                    configuration.set("data." + mapElement.getKey().toString() + ".blacklist", true);
            }
        }
        for (Map.Entry<UUID, Double> entry : refundedBounties.entrySet()) {
            configuration.set("data." + entry.getKey().toString() + ".refund", entry.getValue());
        }
        for (Map.Entry<UUID, List<ItemStack>> entry : refundedItems.entrySet()) {
            configuration.set("data." + entry.getKey().toString() + ".refund-items", SerializeInventory.itemStackArrayToBase64(entry.getValue().toArray(new ItemStack[0])));
        }
        if (bountyCooldown > 0)
            for (Map.Entry<UUID, Long> entry : bountyCooldowns.entrySet()) {
                configuration.set("data." + entry.getKey().toString() + ".last-set", entry.getValue());
            }
        configuration.set("disable-broadcast", disableBroadcast.stream().map(UUID::toString).toList());
        i = 0;
        for (Map.Entry<UUID, List<RewardHead>> mapElement : headRewards.entrySet()) {
            configuration.set("head-rewards." + i + ".setter", mapElement.getKey().toString());
            List<String> encodedText = new ArrayList<>();
            for (RewardHead rewardHead : mapElement.getValue()) {
                encodedText.add(RewardHead.encodeRewardHead(rewardHead));
            }
            configuration.set("head-rewards." + i + ".uuid", encodedText);
            i++;
        }
        i = 0;
        for (Map.Entry<Integer, UUID> mapElement : BountyTracker.getTrackedBounties().entrySet()) {
            configuration.set("tracked-bounties." + i + ".number", mapElement.getKey());
            configuration.set("tracked-bounties." + i + ".uuid", mapElement.getValue().toString());
            i++;
        }
        for (AsyncDatabaseWrapper database : DataManager.getDatabases()) {
            if (!database.isPermDatabase() && System.currentTimeMillis() - database.getLastSync() < DataManager.CONNECTION_REMEMBRANCE_MS)
                configuration.set("database-sync-times." + database.getName(), database.getLastSync());
        }
        if (RandomBounties.isEnabled())
            configuration.set("next-random-bounty", RandomBounties.getNextRandomBounty());
        i = 0;
        for (BountyBoard board : bountyBoards) {
            Location location = board.getLocation();
            // can't save a null world or location
            if (location == null || location.getWorld() == null)
                continue;
            configuration.set("bounty-boards." + i + ".location.world", location.getWorld().getUID().toString());
            configuration.set("bounty-boards." + i + ".location.x", location.getX());
            configuration.set("bounty-boards." + i + ".location.y", location.getY());
            configuration.set("bounty-boards." + i + ".location.z", location.getZ());
            configuration.set("bounty-boards." + i + ".location.yaw", location.getYaw());
            configuration.set("bounty-boards." + i + ".location.pitch", location.getPitch());
            configuration.set("bounty-boards." + i + ".rank", board.getRank());
            configuration.set("bounty-boards." + i + ".direction", board.getDirection().toString());
            i++;
        }
        configuration.set("next-challenge-change", ChallengeManager.getNextChallengeChange());
        configuration.set("server-id", DataManager.getDatabaseServerID(false).toString());
        if (NotBounties.isPaused())
            configuration.set("paused", true);
        // save last location of wanted tags
        List<String> wantedTagLocations = DataManager.locationListToStringList(wantedText.values().stream().map(WantedTags::getLastLocation).toList());
        if (!wantedTagLocations.isEmpty())
            configuration.set("wanted-tags", wantedTagLocations);

        File bounties = new File(NotBounties.getInstance().getDataFolder() + File.separator + "bounties.yml");
        try {
            configuration.save(bounties);
            ChallengeManager.saveChallengeData();
        } catch (IOException e) {
            Bukkit.getLogger().warning(e.toString());
        }
        saveBackup(configuration);
    }

    private void saveBackup(YamlConfiguration configuration) {
        if (!bountyBackups) {
            return;
        }
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yy-MM-dd");
        File backupDirectory = new File(this.getDataFolder() + File.separator + "backups");
        File today = new File(backupDirectory + File.separator + simpleDateFormat.format(date) + "_bounties.yml");
        if (!backupDirectory.exists() && backupDirectory.mkdir()) {
            Bukkit.getLogger().info("[NotBounties] Created backups directory.");
        }
        // try to create a daily backup
        try {
            if (today.createNewFile()) {
                configuration.save(today);
            }
        } catch (IOException e) {
            Bukkit.getLogger().warning(e.toString());
        }
        // delete old backups
        File[] files = backupDirectory.listFiles();
        if (files != null) {
            Map<File, Long> fileDates = new HashMap<>();
            for (File value : files) {
                try {
                    if (value.getName().contains("_"))
                        fileDates.put(value, simpleDateFormat.parse(value.getName().substring(0, value.getName().indexOf("_"))).getTime());
                } catch (ParseException ignored) {
                    // file not in correct format
                }
            }
            Map<File, Long> sortedMap =
                    fileDates.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                    (e1, e2) -> e1, LinkedHashMap::new));
            // find out which files need to be deleted
            final long maxWeeklyBackup = 1000L * 60 * 60 * 24 * 7 * 3;
            final long maxDailyBackup = 1000L * 60 * 60 * 24 * 7;
            int weeklyBackups = 0;
            long lastWeeklyBackup = 0;
            List<File> pendingDeletion = new ArrayList<>();
            for (Map.Entry<File, Long> entry : sortedMap.entrySet()) {
                long timeSinceCreation = System.currentTimeMillis() - entry.getValue();
                if (timeSinceCreation > maxWeeklyBackup) {
                    // too long ago to be useful
                    pendingDeletion.add(entry.getKey());
                    continue;
                }
                if (timeSinceCreation > maxDailyBackup) {
                    // been over 7 days since this backup was created
                    if (weeklyBackups < 3 || timeSinceCreation - lastWeeklyBackup >= maxDailyBackup - (1000L * 60 * 60 * 24)) {
                        // only keep 3 weekly backups
                        weeklyBackups++;
                        lastWeeklyBackup = timeSinceCreation;
                    } else {
                        // delete
                        pendingDeletion.add(entry.getKey());
                    }
                }
            }
            for (File file : pendingDeletion) {
                if (!file.delete()) {
                    Bukkit.getLogger().info("[NotBounties] Could not delete old backup.");
                }
            }
        }

    }

    public void loadConfig() throws IOException {
        // close gui

        ConfigOptions.reloadOptions();

        if (!NotBounties.isPaused()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    // check players to display particles
                    displayParticle.clear();
                    List<Bounty> topBounties = getAllBounties(2);

                    for (Bounty bounty : topBounties) {
                        if (bounty.getTotalDisplayBounty() >= BigBounty.getThreshold()) {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(bounty.getUUID());
                            if (player.isOnline()) {
                                displayParticle.add(Objects.requireNonNull(player.getPlayer()).getUniqueId());
                            }
                        } else {
                            break;
                        }
                    }
                }
            }.runTaskLater(NotBounties.getInstance(), 40);
        }


    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        SkinManager.shutdown();
        DataManager.shutdown();
        // close all GUIs
        for (Player player : Bukkit.getOnlinePlayers()) {
            GUI.safeCloseGUI(player, true);
        }
        // remove wanted tags
        for (Map.Entry<UUID, WantedTags> entry : wantedText.entrySet()) {
            entry.getValue().removeStand();
        }
        // save bounties & logged players
        save();
        try {
            BountyMap.save();
        } catch (IOException e) {
            Bukkit.getLogger().severe("Error saving bounty maps!");
            Bukkit.getLogger().severe(e.toString());
        }
        wantedText.clear();
        for (BountyBoard board : bountyBoards) {
            board.remove();
        }
        bountyBoards.clear();
    }

    public static Whitelist getPlayerWhitelist(UUID uuid) {
        if (playerWhitelist.containsKey(uuid)) {
            return playerWhitelist.get(uuid);
        }
        Whitelist whitelist = new Whitelist(new ArrayList<>(), false);
        playerWhitelist.put(uuid, whitelist);

        return whitelist;
    }


    public void sendDebug(CommandSender sender) {
        sender.sendMessage(parse(getPrefix() + ChatColor.WHITE + "NotBounties debug info:", null));
        long numConnected = DataManager.getDatabases().stream().filter(AsyncDatabaseWrapper::isConnected).count();
        String connected = numConnected > 0 ? ChatColor.GREEN + "" + numConnected : ChatColor.RED + "" + numConnected;
        String numConfigured = ChatColor.WHITE + "" + DataManager.getDatabases().size();
        int bounties = BountyManager.getAllBounties(-1).size();
        sender.sendMessage(ChatColor.GOLD + "Databases > " + ChatColor.YELLOW + "Configured: " + numConfigured + ChatColor.YELLOW + " Connected: " + connected);
        sender.sendMessage(ChatColor.GOLD + "General > " + ChatColor.YELLOW + "Author: " + ChatColor.GRAY + "Not_Jaden" + ChatColor.YELLOW + " Plugin Version: " + ChatColor.WHITE + getDescription().getVersion() + ChatColor.YELLOW + " Server Version: " + ChatColor.WHITE + "1." + serverVersion + "." + serverSubVersion + ChatColor.YELLOW + " Debug Mode: " + ChatColor.WHITE + debug);
        sender.sendMessage(ChatColor.GOLD + "Stats > " + ChatColor.YELLOW + "Bounties: " + ChatColor.WHITE + bounties + ChatColor.YELLOW + " Tracked Bounties: " + ChatColor.WHITE + BountyTracker.getTrackedBounties().size() + ChatColor.YELLOW + " Bounty Boards: " + ChatColor.WHITE + bountyBoards.size());
        String vault = vaultEnabled ? ChatColor.GREEN + "Vault" : ChatColor.RED + "Vault";
        String papi = papiEnabled ? ChatColor.GREEN + "PlaceholderAPI" : ChatColor.RED + "PlaceholderAPI";
        String hdb = HDBEnabled ? ChatColor.GREEN + "HeadDataBase" : ChatColor.RED + "HeadDataBase";
        String liteBans = liteBansEnabled ? ChatColor.GREEN + "LiteBans" : ChatColor.RED + "LiteBans";
        String skinsRestorer = skinsRestorerEnabled ? ChatColor.GREEN + "SkinsRestorer" : ChatColor.RED + "SkinsRestorer";
        String betterTeams = BountyClaimRequirements.betterTeamsEnabled ? ChatColor.GREEN + "BetterTeams" : ChatColor.RED + "BetterTeams";
        String townyAdvanced = BountyClaimRequirements.townyAdvancedEnabled ? ChatColor.GREEN + "TownyAdvanced" : ChatColor.RED + "TownyAdvanced";
        String floodgate = floodgateEnabled ? ChatColor.GREEN + "Floodgate" : ChatColor.RED + "Floodgate";
        String geyser = geyserEnabled ? ChatColor.GREEN + "GeyserMC" : ChatColor.RED + "GeyserMC";
        String kingdoms = BountyClaimRequirements.kingdomsXEnabled ? ChatColor.GREEN + "Kingdoms" : ChatColor.RED + "Kingdoms";
        String lands = BountyClaimRequirements.landsEnabled ? ChatColor.GREEN + "Lands" : ChatColor.RED + "Lands";
        String worldGuard = BountyClaimRequirements.worldGuardEnabled ? ChatColor.GREEN + "WorldGuard" : ChatColor.RED + "WorldGuard";
        sender.sendMessage(ChatColor.GOLD + "Plugin Hooks > " + ChatColor.GRAY + "[" + vault + ChatColor.GRAY + "|" + papi + ChatColor.GRAY + "|" + hdb + ChatColor.GRAY + "|" + liteBans + ChatColor.GRAY + "|" + skinsRestorer + ChatColor.GRAY + "|" + betterTeams + ChatColor.GRAY + "|" + townyAdvanced + ChatColor.GRAY + "|" + geyser + ChatColor.GRAY + "|" + floodgate + ChatColor.GRAY + "|" + kingdoms + ChatColor.GRAY + "|" + lands + ChatColor.GRAY + "|" + worldGuard + ChatColor.GRAY + "]");
        sender.sendMessage(ChatColor.GRAY + "Reloading the plugin will refresh connections.");
        TextComponent discord = new TextComponent(net.md_5.bungee.api.ChatColor.of(new Color(114, 137, 218)) + "Support Discord: " + ChatColor.GRAY + ChatColor.UNDERLINE + "https://discord.gg/zEsUzwYEx7");
        discord.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/zEsUzwYEx7"));
        TextComponent spigot = new TextComponent(net.md_5.bungee.api.ChatColor.of(new Color(240, 149, 45)) + "Spigot: " + ChatColor.GRAY + ChatColor.UNDERLINE + "https://www.spigotmc.org/resources/notbounties.104484/");
        spigot.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/notbounties.104484/"));
        TextComponent github = new TextComponent(net.md_5.bungee.api.ChatColor.of(new Color(230, 237, 243)) + "Github: " + ChatColor.GRAY + ChatColor.UNDERLINE + "https://github.com/No-Not-Jaden/NotBounties");
        github.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/No-Not-Jaden/NotBounties"));
        sender.spigot().sendMessage(discord);
        sender.spigot().sendMessage(spigot);
        sender.spigot().sendMessage(github);
        sender.sendMessage("");
    }

    public static void removeBountyBoard() {
        for (BountyBoard board : bountyBoards) {
            board.remove();
        }
        bountyBoards.clear();
    }


    public static void addBountyBoard(BountyBoard board) {
        bountyBoards.add(board);
    }


    public static int removeSpecificBountyBoard(ItemFrame frame) {
        ListIterator<BountyBoard> bountyBoardListIterator = bountyBoards.listIterator();
        int removes = 0;
        while (bountyBoardListIterator.hasNext()) {
            BountyBoard board = bountyBoardListIterator.next();
            if (frame.equals(board.getFrame())) {
                board.remove();
                bountyBoardListIterator.remove();
                removes++;
            }
        }
        return removes;
    }

    public static void removeWantedTag(UUID uuid) {
        if (!wantedText.containsKey(uuid))
            return;
        wantedText.get(uuid).disable();
        wantedText.remove(uuid);
    }




    public static Map<UUID, String> getNetworkPlayers() {
        return DataManager.getNetworkPlayers();
    }

    public static boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }
        return false;
    }

    /**
     * Returns if the server version is above the specified version
     * @param majorVersion Major version of the server. In 1.20.4, the major version is 20
     * @param subVersion Sub version of the server. In 1.20.4, the sub version is 4
     * @return True if the current server version is higher than the specified one
     */
    public static boolean isAboveVersion(int majorVersion, int subVersion) {
        return serverVersion > majorVersion || (majorVersion == serverVersion && subVersion < serverSubVersion);
    }

    public static void debugMessage(String message, boolean warning) {
        if (!debug)
            return;
        message = "[NotBountiesDebug] " + message;
        NotBounties notBounties = NotBounties.getInstance();
        if (notBounties.isEnabled()) {
            String finalMessage = message;
            new BukkitRunnable() {
                @Override
                public void run() {
                    consoleMessage(finalMessage, warning);
                }
            }.runTask(notBounties);
        } else {
            consoleMessage(message, warning);
        }
    }

    public static boolean isPaused() {
        return paused;
    }

    public static void setPaused(boolean paused) {
        if (paused) {
            for (BountyBoard board : bountyBoards)
                board.remove();
            // close all GUIs
            for (Player player : Bukkit.getOnlinePlayers()) {
                GUI.safeCloseGUI(player, true);
            }
            // remove wanted tags
            for (Map.Entry<UUID, WantedTags> entry : wantedText.entrySet()) {
                entry.getValue().removeStand();
            }
            for (Player player : Bukkit.getOnlinePlayers())
                events.onQuit(new PlayerQuitEvent(player, ""));
            NotBounties.paused = true;
        } else {
            NotBounties.paused = false;
            for (Player player : Bukkit.getOnlinePlayers())
                events.onJoin(new PlayerJoinEvent(player, ""));
        }
    }

    private static void consoleMessage(String message, boolean warning) {
        if (warning)
            Bukkit.getLogger().warning(message);
        else
            Bukkit.getLogger().info(message);
    }

    public static boolean isBedrockPlayer(UUID uuid) {
        if (Bukkit.getPlayer(uuid) != null) {
            if (ConfigOptions.floodgateEnabled) {
                FloodGateClass floodGateClass = new FloodGateClass();
                return floodGateClass.isBedrockPlayer(uuid);
            } else if (ConfigOptions.geyserEnabled) {
                GeyserMCClass geyserMCClass = new GeyserMCClass();
                return geyserMCClass.isBedrockPlayer(uuid);
            }
        }
        String first = uuid.toString().substring(0, 18);
        return first.equals("00000000-0000-0000");
    }

    public static String getXuid(UUID uuid) {
        if (Bukkit.getPlayer(uuid) != null) {
            if (ConfigOptions.floodgateEnabled) {
                FloodGateClass floodGateClass = new FloodGateClass();
                if (floodGateClass.isBedrockPlayer(uuid)) {
                    return floodGateClass.getXuid(uuid);
                }
            } else if (ConfigOptions.geyserEnabled) {
                GeyserMCClass geyserMCClass = new GeyserMCClass();
                if (geyserMCClass.isBedrockPlayer(uuid)) {
                    return geyserMCClass.getXuid(uuid);
                }
            }
        }
        if (isBedrockPlayer(uuid)) {
            String last = uuid.toString().replace("-", "").substring(16);
            try {
                return Long.parseLong(last, 16) + "";
            } catch (NumberFormatException ignored){
                // Not parsable hex
            }
        }
        return "";
    }

    private static final String ADMIN_PERMISSION = "notbounties.admin";
    public static String getAdminPermission(){
        return ADMIN_PERMISSION;
    }

}
