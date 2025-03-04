package me.unchainedsouls.unchainedSoulsBeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Main plugin class for UnchainedSouls, implementing a shadow/pet system with MiniMessage support and black market.
 * Compatible with Paper 1.21.4.
 */
public class SoulsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    // MiniMessage instance for text formatting
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // GUI Constants
    private static final String GUI_TITLE_CUSTOMIZATION = "<dark_purple>Shadow Customization";
    private static final String GUI_TITLE_PARTICLES = "<dark_purple>Particle Effects";
    private static final String GUI_TITLE_TITLES = "<dark_purple>Shadow Titles";
    private static final String BLACK_MARKET_TITLE = "<dark_purple>Black Market";

    // Shadow pet data structure
    private static class Shadow {
        EntityType type;
        int kills;
        String evolutionLevel;
        Entity activePet;
        double health = 1.0;
        double damage = 1.0;
        double speed = 1.0;
        String evolutionPath;
        int evolutionPoints;
        String customName = "";
        String title = "";
        String particleEffect = "";
        Map<String, Integer> unlockedAbilities = new HashMap<>();
        Map<String, Boolean> unlockedCustomizations = new HashMap<>();

        Shadow(EntityType type) {
            this.type = type;
            this.kills = 0;
            this.evolutionLevel = "Basic";
            this.evolutionPath = "";
            this.evolutionPoints = 0;
        }
    }

    // Data storage
    private FileConfiguration blackMarketConfig;
    private final Map<UUID, Map<EntityType, Shadow>> playerShadows = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> activePets = new ConcurrentHashMap<>();
    private final Set<BukkitRunnable> activeParticleTasks = Collections.synchronizedSet(new HashSet<>());
    private final Random random = new Random();

    // Config constants
    private int healthUpgradeCost;
    private int damageUpgradeCost;
    private int speedUpgradeCost;
    private int evolutionThreshold1;
    private int evolutionThreshold2;
    private long particleUpdateTicks;
    private int extractionTimeout;

    // Lifecycle Methods

    @Override
    public void onEnable() {
        try {
            loadConfigurations();
            registerCommands();
            getServer().getPluginManager().registerEvents(this, this);
            startCleanupTask();
            getLogger().info("SoulsPlugin enabled successfully on Paper 1.21.4!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable SoulsPlugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            cleanupTasks();
            saveShadowData();
            removeActivePets();
            saveConfig();
            getLogger().info("SoulsPlugin disabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin disable", e);
        }
    }

    // Configuration Management

    /** Loads all configuration files and initializes constants. */
    private void loadConfigurations() {
        try {
            saveDefaultConfig();
            if (!getDataFolder().exists()) getDataFolder().mkdirs();

            File blackmarketFile = new File(getDataFolder(), "blackmarket.yml");
            if (!blackmarketFile.exists()) saveResource("blackmarket.yml", false);
            blackMarketConfig = YamlConfiguration.loadConfiguration(blackmarketFile);

            healthUpgradeCost = getConfig().getInt("upgrade_costs.health", 50);
            damageUpgradeCost = getConfig().getInt("upgrade_costs.damage", 75);
            speedUpgradeCost = getConfig().getInt("upgrade_costs.speed", 40);
            evolutionThreshold1 = getConfig().getInt("evolution.threshold_1", 50);
            evolutionThreshold2 = getConfig().getInt("evolution.threshold_2", 100);
            particleUpdateTicks = getConfig().getLong("particles.update_interval", 5L);
            extractionTimeout = getConfig().getInt("particles.extraction_timeout", 2400);

            loadShadowData();
            getLogger().info("Configurations loaded successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load configurations", e);
        }
    }

    /** Registers plugin commands. */
    private void registerCommands() {
        Objects.requireNonNull(getCommand("souls")).setExecutor(this);
        Objects.requireNonNull(getCommand("shadow")).setExecutor(this);
        Objects.requireNonNull(getCommand("blackmarket")).setExecutor(this);
        Objects.requireNonNull(getCommand("soulsadmin")).setExecutor(this);
    }

    // Command Handling

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Only players can use this command!"));
            return true;
        }

        try {
            switch (command.getName().toLowerCase()) {
                case "souls" -> handleSoulsCommand(player, args);
                case "shadow" -> handleShadowCommand(player, args);
                case "blackmarket" -> openBlackMarket(player);
                case "soulsadmin" -> {
                    if (!player.hasPermission("unchainedsouls.admin")) {
                        player.sendMessage(MINI_MESSAGE.deserialize("<red>No permission!"));
                        return true;
                    }
                    handleAdminCommand(player, args);
                }
                default -> { return false; }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error executing command: " + command.getName(), e);
            player.sendMessage(MINI_MESSAGE.deserialize("<red>An error occurred!"));
        }
        return true;
    }

    /** Handles /souls command with MiniMessage formatting. */
    private void handleSoulsCommand(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showSoulsHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "balance" -> showBalance(player);
            case "withdraw", "deposit" -> {
                if (args.length < 2) {
                    player.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /souls " + args[0] + " <amount>"));
                    return;
                }
                try {
                    int amount = Integer.parseInt(args[1]);
                    handleTransaction(player, args[0], amount);
                } catch (NumberFormatException e) {
                    player.sendMessage(MINI_MESSAGE.deserialize("<red>Invalid number!"));
                }
            }
            default -> player.sendMessage(MINI_MESSAGE.deserialize("<red>Unknown subcommand. Use /souls help."));
        }
    }

    /** Handles /shadow command for shadow pet management. */
    private void handleShadowCommand(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showShadowHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "customize" -> openCustomizationGUI(player);
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /shadow rename <type> <name>"));
                    return;
                }
                handleShadowRename(player, args[1], args[2]);
            }
            default -> showShadowHelp(player);
        }
    }

    /** Handles /soulsadmin command for admin functions. */
    private void handleAdminCommand(Player admin, String[] args) {
        if (args.length < 3) {
            admin.sendMessage(MINI_MESSAGE.deserialize("<red>Usage: /soulsadmin <give|take|set> <player> <amount>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            admin.sendMessage(MINI_MESSAGE.deserialize("<red>Player not found!"));
            return;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            int currentSouls = getSouls(target);

            switch (args[0].toLowerCase()) {
                case "give" -> setSouls(target, currentSouls + amount);
                case "take" -> setSouls(target, Math.max(0, currentSouls - amount));
                case "set" -> setSouls(target, amount);
                default -> {
                    admin.sendMessage(MINI_MESSAGE.deserialize("<red>Use give, take, or set!"));
                    return;
                }
            }
            admin.sendMessage(MINI_MESSAGE.deserialize("<green>Updated " + target.getName() + "'s souls!"));
            target.sendMessage(MINI_MESSAGE.deserialize("<green>Your soul balance was updated!"));
        } catch (NumberFormatException e) {
            admin.sendMessage(MINI_MESSAGE.deserialize("<red>Invalid number!"));
        }
    }

    // GUI Management

    /** Opens the shadow customization GUI. */
    private void openCustomizationGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MINI_MESSAGE.deserialize(GUI_TITLE_CUSTOMIZATION));
        gui.setItem(11, createGuiItem(Material.BLAZE_POWDER, "<gold>Particle Effects", "<gray>Customize particle effects"));
        gui.setItem(15, createGuiItem(Material.NAME_TAG, "<gold>Titles", "<gray>Choose a shadow title"));
        gui.setItem(22, createGuiItem(Material.BARRIER, "<red>Close"));
        player.openInventory(gui);
    }

    /** Opens the particle effects selection GUI. */
    private void openParticleEffectsGUI(Player player, EntityType shadowType) {
        if (shadowType == null) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>No active shadow to customize!"));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, MINI_MESSAGE.deserialize(GUI_TITLE_PARTICLES));
        ConfigurationSection effects = getConfig().getConfigurationSection("customization.particle_effects");
        if (effects != null) {
            int slot = 10;
            Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
            for (String key : effects.getKeys(false)) {
                ConfigurationSection effect = effects.getConfigurationSection(key);
                if (effect == null) continue;

                String name = effect.getString("name", "Unknown");
                String desc = effect.getString("description", "");
                int cost = effect.getInt("cost", 0);
                boolean unlocked = shadow.unlockedCustomizations.getOrDefault("particle_" + key, false);

                List<String> lore = Arrays.asList("<gray>" + desc, "",
                        "<yellow>Cost: " + cost + " souls", "",
                        unlocked ? "<green>UNLOCKED" : "<red>LOCKED");
                gui.setItem(slot++, createGuiItem(Material.BLAZE_POWDER, "<gold>" + name, lore.toArray(new String[0])));
            }
        }
        gui.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back"));
        gui.setItem(22, createGuiItem(Material.BARRIER, "<red>Close"));
        player.openInventory(gui);
    }

    /** Opens the titles selection GUI. */
    private void openTitlesGUI(Player player, EntityType shadowType) {
        if (shadowType == null) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>No active shadow to customize!"));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, MINI_MESSAGE.deserialize(GUI_TITLE_TITLES));
        ConfigurationSection titles = getConfig().getConfigurationSection("customization.titles");
        if (titles != null) {
            int slot = 10;
            Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
            for (String key : titles.getKeys(false)) {
                ConfigurationSection title = titles.getConfigurationSection(key);
                if (title == null) continue;

                String name = title.getString("name", "Unknown");
                String desc = title.getString("description", "");
                int cost = title.getInt("cost", 0);
                boolean unlocked = shadow.unlockedCustomizations.getOrDefault("title_" + key, false);

                List<String> lore = Arrays.asList("<gray>" + desc, "",
                        "<yellow>Cost: " + cost + " souls", "",
                        unlocked ? "<green>UNLOCKED" : "<red>LOCKED");
                gui.setItem(slot++, createGuiItem(Material.NAME_TAG, "<gold>" + name, lore.toArray(new String[0])));
            }
        }
        gui.setItem(18, createGuiItem(Material.ARROW, "<yellow>Back"));
        gui.setItem(22, createGuiItem(Material.BARRIER, "<red>Close"));
        player.openInventory(gui);
    }

    /** Opens the black market GUI with items from blackmarket.yml. */
    private void openBlackMarket(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MINI_MESSAGE.deserialize(BLACK_MARKET_TITLE));
        ConfigurationSection items = blackMarketConfig.getConfigurationSection("items");
        if (items != null) {
            int slot = 10;
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;

                Material material = Material.valueOf(item.getString("material", "STONE").toUpperCase());
                String name = item.getString("name", "Unknown Item");
                int cost = item.getInt("cost", 100);
                List<String> lore = item.getStringList("lore");
                lore.add("");
                lore.add("<yellow>Cost: " + cost + " souls");

                ItemStack stack = createGuiItem(material, "<gold>" + name, lore.toArray(new String[0]));
                stack.getItemMeta().getPersistentDataContainer().set(
                        new NamespacedKey(this, "blackmarket_item"), PersistentDataType.STRING, key);
                gui.setItem(slot++, stack);
            }
        }
        gui.setItem(22, createGuiItem(Material.BARRIER, "<red>Close"));
        player.openInventory(gui);
    }

    // Event Handlers

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().getTitle());
        if (!title.contains("Shadow Customization") && !title.contains("Particle Effects") &&
                !title.contains("Shadow Titles") && !title.contains("Black Market")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
        } else if (title.contains("Shadow Customization")) {
            if (clicked.getType() == Material.BLAZE_POWDER) openParticleEffectsGUI(player, getCurrentShadowType(player));
            else if (clicked.getType() == Material.NAME_TAG) openTitlesGUI(player, getCurrentShadowType(player));
        } else if (title.contains("Particle Effects")) {
            handleParticleEffectSelection(player, clicked);
        } else if (title.contains("Shadow Titles")) {
            handleTitleSelection(player, clicked);
        } else if (title.contains("Black Market")) {
            handleBlackMarketPurchase(player, clicked);
        }
    }

    // Utility Methods

    /** Gets the type of the player's currently active shadow pet. */
    private EntityType getCurrentShadowType(Player player) {
        Entity activePet = activePets.get(player.getUniqueId());
        return activePet != null ? activePet.getType() : null;
    }

    /** Applies or purchases a particle effect for a shadow. */
    private void handleParticleEffectSelection(Player player, ItemStack clicked) {
        String effectName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().getDisplayName());
        EntityType shadowType = getCurrentShadowType(player);
        if (shadowType == null) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>No active shadow!"));
            return;
        }

        Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
        ConfigurationSection effects = getConfig().getConfigurationSection("customization.particle_effects");
        for (String key : effects.getKeys(false)) {
            ConfigurationSection effect = effects.getConfigurationSection(key);
            if (effect.getString("name").equals(effectName)) {
                int cost = effect.getInt("cost");
                if (shadow.unlockedCustomizations.getOrDefault("particle_" + key, false)) {
                    shadow.particleEffect = key;
                    player.sendMessage(MINI_MESSAGE.deserialize("<green>Particle effect applied!"));
                } else if (getSouls(player) >= cost) {
                    setSouls(player, getSouls(player) - cost);
                    shadow.unlockedCustomizations.put("particle_" + key, true);
                    shadow.particleEffect = key;
                    player.sendMessage(MINI_MESSAGE.deserialize("<green>Particle effect purchased and applied!"));
                } else {
                    player.sendMessage(MINI_MESSAGE.deserialize("<red>Need " + cost + " souls!"));
                }
                saveShadowData();
                break;
            }
        }
    }

    /** Applies or purchases a title for a shadow. */
    private void handleTitleSelection(Player player, ItemStack clicked) {
        String titleName = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().getDisplayName());
        EntityType shadowType = getCurrentShadowType(player);
        if (shadowType == null) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>No active shadow!"));
            return;
        }

        Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
        ConfigurationSection titles = getConfig().getConfigurationSection("customization.titles");
        for (String key : titles.getKeys(false)) {
            ConfigurationSection title = titles.getConfigurationSection(key);
            if (title.getString("name").equals(titleName)) {
                int cost = title.getInt("cost");
                if (shadow.unlockedCustomizations.getOrDefault("title_" + key, false)) {
                    shadow.title = titleName;
                    updateShadowDisplayName(shadow);
                    player.sendMessage(MINI_MESSAGE.deserialize("<green>Title applied!"));
                } else if (getSouls(player) >= cost) {
                    setSouls(player, getSouls(player) - cost);
                    shadow.unlockedCustomizations.put("title_" + key, true);
                    shadow.title = titleName;
                    updateShadowDisplayName(shadow);
                    player.sendMessage(MINI_MESSAGE.deserialize("<green>Title purchased and applied!"));
                } else {
                    player.sendMessage(MINI_MESSAGE.deserialize("<red>Need " + cost + " souls!"));
                }
                saveShadowData();
                break;
            }
        }
    }

    /** Handles black market purchases. */
    private void handleBlackMarketPurchase(Player player, ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        String itemKey = meta.getPersistentDataContainer().get(
                new NamespacedKey(this, "blackmarket_item"), PersistentDataType.STRING);
        if (itemKey == null) return;

        ConfigurationSection item = blackMarketConfig.getConfigurationSection("items." + itemKey);
        int cost = item.getInt("cost", 100);
        if (getSouls(player) < cost) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>You need " + cost + " souls to buy this!"));
            return;
        }

        setSouls(player, getSouls(player) - cost);
        String action = item.getString("action", "message");
        switch (action.toLowerCase()) {
            case "message" -> player.sendMessage(MINI_MESSAGE.deserialize(item.getString("value", "<green>Purchase successful!")));
            case "effect" -> {
                Shadow shadow = playerShadows.get(player.getUniqueId()).get(getCurrentShadowType(player));
                if (shadow != null) {
                    shadow.particleEffect = item.getString("value");
                    shadow.unlockedCustomizations.put("particle_" + item.getString("value"), true);
                    player.sendMessage(MINI_MESSAGE.deserialize("<green>Effect unlocked and applied!"));
                }
            }
            // Add more actions (e.g., item give, ability unlock) as needed
        }
        saveShadowData();
        player.sendMessage(MINI_MESSAGE.deserialize("<green>Purchase completed!"));
    }

    /** Updates the display name of an active shadow pet with MiniMessage. */
    private void updateShadowDisplayName(Shadow shadow) {
        if (shadow.activePet == null || !shadow.activePet.isValid()) return;
        String displayName = shadow.title.isEmpty() ? "" : "<gold>[" + shadow.title + "] ";
        displayName += shadow.customName.isEmpty() ? "<gray>" + shadow.type.name() + " Shadow" : shadow.customName;
        shadow.activePet.customName(MINI_MESSAGE.deserialize(displayName));
        shadow.activePet.setCustomNameVisible(true);
    }

    /** Renames a shadow pet. */
    private void handleShadowRename(Player player, String typeStr, String newName) {
        try {
            EntityType type = EntityType.valueOf(typeStr.toUpperCase());
            Shadow shadow = playerShadows.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).get(type);
            if (shadow == null) {
                player.sendMessage(MINI_MESSAGE.deserialize("<red>You don’t have this shadow type!"));
                return;
            }
            shadow.customName = newName;
            if (shadow.activePet != null && shadow.activePet.isValid()) updateShadowDisplayName(shadow);
            player.sendMessage(MINI_MESSAGE.deserialize("<green>Shadow renamed successfully!"));
            saveShadowData();
        } catch (IllegalArgumentException e) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>Invalid shadow type!"));
        }
    }

    /** Creates a formatted GUI item with MiniMessage support. */
    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(name));
            meta.lore(Arrays.stream(lore).map(MINI_MESSAGE::deserialize).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Saves shadow data to shadowdata.yml. */
    private void saveShadowData() {
        try {
            File file = new File(getDataFolder(), "shadowdata.yml");
            YamlConfiguration data = new YamlConfiguration();
            for (Map.Entry<UUID, Map<EntityType, Shadow>> entry : playerShadows.entrySet()) {
                ConfigurationSection playerSection = data.createSection(entry.getKey().toString());
                for (Map.Entry<EntityType, Shadow> shadowEntry : entry.getValue().entrySet()) {
                    Shadow shadow = shadowEntry.getValue();
                    ConfigurationSection shadowSection = playerSection.createSection(shadowEntry.getKey().name());
                    shadowSection.set("customName", shadow.customName);
                    shadowSection.set("title", shadow.title);
                    shadowSection.set("particleEffect", shadow.particleEffect);
                    shadowSection.set("kills", shadow.kills);
                    shadowSection.set("evolutionLevel", shadow.evolutionLevel);
                    shadowSection.set("evolutionPath", shadow.evolutionPath);
                    shadowSection.set("evolutionPoints", shadow.evolutionPoints);
                    ConfigurationSection unlocked = shadowSection.createSection("unlockedCustomizations");
                    shadow.unlockedCustomizations.forEach(unlocked::set);
                }
            }
            data.save(file);
            getLogger().info("Shadow data saved successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to save shadow data", e);
        }
    }

    /** Loads shadow data from shadowdata.yml. */
    private void loadShadowData() {
        File file = new File(getDataFolder(), "shadowdata.yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            for (String uuidStr : data.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<EntityType, Shadow> shadows = new HashMap<>();
                ConfigurationSection playerSection = data.getConfigurationSection(uuidStr);
                if (playerSection != null) {
                    for (String typeStr : playerSection.getKeys(false)) {
                        EntityType type = EntityType.valueOf(typeStr);
                        ConfigurationSection shadowSection = playerSection.getConfigurationSection(typeStr);
                        Shadow shadow = new Shadow(type);
                        shadow.customName = shadowSection.getString("customName", "");
                        shadow.title = shadowSection.getString("title", "");
                        shadow.particleEffect = shadowSection.getString("particleEffect", "");
                        shadow.kills = shadowSection.getInt("kills", 0);
                        shadow.evolutionLevel = shadowSection.getString("evolutionLevel", "Basic");
                        shadow.evolutionPath = shadowSection.getString("evolutionPath", "");
                        shadow.evolutionPoints = shadowSection.getInt("evolutionPoints", 0);
                        ConfigurationSection unlocked = shadowSection.getConfigurationSection("unlockedCustomizations");
                        if (unlocked != null) {
                            for (String key : unlocked.getKeys(false)) {
                                shadow.unlockedCustomizations.put(key, unlocked.getBoolean(key));
                            }
                        }
                        shadows.put(type, shadow);
                    }
                }
                playerShadows.put(uuid, shadows);
            }
            getLogger().info("Shadow data loaded successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load shadow data", e);
        }
    }

    /** Shows help for /souls command with MiniMessage. */
    private void showSoulsHelp(Player player) {
        player.sendMessage(MINI_MESSAGE.deserialize("<gold>=== Souls Commands ==="));
        player.sendMessage(MINI_MESSAGE.deserialize("<gold>/souls balance <white>- Check soul balance"));
        player.sendMessage(MINI_MESSAGE.deserialize("<gold>/souls withdraw <amount> <white>- Withdraw souls"));
        player.sendMessage(MINI_MESSAGE.deserialize("<gold>/souls deposit <amount> <white>- Deposit souls"));
    }

    /** Shows help for /shadow command with MiniMessage. */
    private void showShadowHelp(Player player) {
        player.sendMessage(MINI_MESSAGE.deserialize("<gold>=== Shadow Commands ==="));
        player.sendMessage(MINI_MESSAGE.deserialize("<gold>/shadow customize <white>- Open customization menu"));
        player.sendMessage(MINI_MESSAGE.deserialize("<gold>/shadow rename <type> <name> <white>- Rename a shadow"));
    }

    /** Displays the player's soul balance with MiniMessage. */
    private void showBalance(Player player) {
        player.sendMessage(MINI_MESSAGE.deserialize("<green>Soul Balance: <gold>" + getSouls(player) + " souls"));
    }

    /** Handles soul transactions (withdraw/deposit). */
    private void handleTransaction(Player player, String type, int amount) {
        if (amount <= 0) {
            player.sendMessage(MINI_MESSAGE.deserialize("<red>Amount must be positive!"));
            return;
        }
        int currentSouls = getSouls(player);
        if (type.equals("withdraw")) {
            if (currentSouls < amount) {
                player.sendMessage(MINI_MESSAGE.deserialize("<red>Not enough souls!"));
                return;
            }
            setSouls(player, currentSouls - amount);
            player.sendMessage(MINI_MESSAGE.deserialize("<green>Withdrew " + amount + " souls!"));
        } else {
            setSouls(player, currentSouls + amount);
            player.sendMessage(MINI_MESSAGE.deserialize("<green>Deposited " + amount + " souls!"));
        }
    }

    /** Gets the player's soul balance. */
    private int getSouls(Player player) {
        return getConfig().getInt("souls." + player.getUniqueId(), 0);
    }

    /** Sets the player's soul balance. */
    private void setSouls(Player player, int souls) {
        getConfig().set("souls." + player.getUniqueId(), souls);
        saveConfig();
    }

    /** Starts a task to clean up invalid pets. */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                synchronized (activePets) {
                    activePets.entrySet().removeIf(entry -> {
                        if (!entry.getValue().isValid()) {
                            Player owner = Bukkit.getPlayer(entry.getKey());
                            if (owner != null) owner.sendMessage(MINI_MESSAGE.deserialize("<red>Your shadow pet was lost!"));
                            return true;
                        }
                        return false;
                    });
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    /** Cleans up particle tasks on disable. */
    private void cleanupTasks() {
        activeParticleTasks.forEach(BukkitRunnable::cancel);
        activeParticleTasks.clear();
    }

    /** Removes all active pets on disable. */
    private void removeActivePets() {
        activePets.values().stream().filter(Entity::isValid).forEach(Entity::remove);
        activePets.clear();
    }
}
