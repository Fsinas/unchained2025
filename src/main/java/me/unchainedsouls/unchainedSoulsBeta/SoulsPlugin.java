package me.unchainedsouls.unchainedSoulsBeta;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.*;
import org.bukkit.configuration.file.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;

public class SoulsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    // GUI Constants
    private static final String GUI_TITLE_CUSTOMIZATION = ChatColor.DARK_PURPLE + "Shadow Customization";
    private static final String GUI_TITLE_PARTICLES = ChatColor.DARK_PURPLE + "Particle Effects"; 
    private static final String GUI_TITLE_TITLES = ChatColor.DARK_PURPLE + "Shadow Titles";

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
        Map<String, Integer> unlockedAbilities = new HashMap<>();

        // Customization fields
        String customName;
        String title;
        String particleEffect;
        Map<String, Boolean> unlockedCustomizations = new HashMap<>();

        Shadow(EntityType type) {
            this.type = type;
            this.kills = 0;
            this.evolutionLevel = "Basic";
            this.evolutionPath = "";
            this.evolutionPoints = 0;
            this.customName = "";
            this.title = "";
            this.particleEffect = "";
        }
    }

    // Configuration and data storage
    private FileConfiguration blackMarketConfig;
    private final Map<UUID, Map<EntityType, Shadow>> playerShadows = new ConcurrentHashMap<>();
    private final Map<UUID, EntityType> extractableMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> activePets = new ConcurrentHashMap<>();
    private final Set<BukkitRunnable> activeParticleTasks = Collections.synchronizedSet(new HashSet<>());
    private final Random random = new Random();

    // Constants from config.yml
    private int healthUpgradeCost;
    private int damageUpgradeCost;
    private int speedUpgradeCost;
    private int evolutionThreshold1;
    private int evolutionThreshold2;
    private long particleUpdateTicks;
    private int extractionTimeout;

    private final String BLACK_MARKET_TITLE = ChatColor.DARK_PURPLE + "Black Market";
    private static final String DARK_GRIMOIRE_ID = "dark_grimoire";
    private NamespacedKey grimoireKey;

    @Override
    public void onEnable() {
        try {
            grimoireKey = new NamespacedKey(this, DARK_GRIMOIRE_ID);
            loadConfigurations();
            registerCommands();
            getServer().getPluginManager().registerEvents(this, this);
            startCleanupTask();
            getLogger().info("SoulsPlugin enabled successfully!");
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

    private void loadConfigurations() {
        try {
            // Save default config.yml
            saveDefaultConfig();

            // Ensure plugin directory exists
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // Handle blackmarket.yml
            File blackmarketFile = new File(getDataFolder(), "blackmarket.yml");
            if (!blackmarketFile.exists()) {
                saveResource("blackmarket.yml", false);
            }
            blackMarketConfig = YamlConfiguration.loadConfiguration(blackmarketFile);

            // Load constants from config
            healthUpgradeCost = getConfig().getInt("upgrade_costs.health", 50);
            damageUpgradeCost = getConfig().getInt("upgrade_costs.damage", 75);
            speedUpgradeCost = getConfig().getInt("upgrade_costs.speed", 40);
            evolutionThreshold1 = getConfig().getInt("evolution.threshold_1", 50);
            evolutionThreshold2 = getConfig().getInt("evolution.threshold_2", 100);
            particleUpdateTicks = getConfig().getLong("particles.update_interval", 5L);
            extractionTimeout = getConfig().getInt("particles.extraction_timeout", 2400);

            loadShadowData();

            getLogger().info("Successfully loaded all configuration files");
        } catch (Exception e) {
            getLogger().severe("Failed to load configurations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerCommands() {
        getCommand("souls").setExecutor(this);
        getCommand("shadow").setExecutor(this);
        getCommand("blackmarket").setExecutor(this);
        getCommand("soulsadmin").setExecutor(this);
    }

    private void handleSetEffect(Player admin, String targetName, String typeStr, String effectKey) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        try {
            EntityType type = EntityType.valueOf(typeStr.toUpperCase());
            Shadow shadow = playerShadows.get(target.getUniqueId()).get(type);
            
            if (shadow == null) {
                admin.sendMessage(ChatColor.RED + "Target player doesn't have this shadow type!");
                return;
            }

            ConfigurationSection effects = getConfig().getConfigurationSection("customization.particle_effects");
            if (effects != null && effects.contains(effectKey)) {
                shadow.particleEffect = effectKey;
                shadow.unlockedCustomizations.put("particle_" + effectKey, true);
                
                admin.sendMessage(ChatColor.GREEN + "Successfully set " + effectKey + " effect for " + 
                                target.getName() + "'s " + type.name() + " shadow!");
                target.sendMessage(ChatColor.GREEN + "An admin has given you the " + effectKey + " effect for your " + 
                                 type.name() + " shadow!");
                
                saveShadowData();
            } else {
                admin.sendMessage(ChatColor.RED + "Invalid effect key!");
            }
        } catch (IllegalArgumentException e) {
            admin.sendMessage(ChatColor.RED + "Invalid shadow type!");
        }
    }

    private void handleSetTitle(Player admin, String targetName, String typeStr, String titleKey) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        try {
            EntityType type = EntityType.valueOf(typeStr.toUpperCase());
            Shadow shadow = playerShadows.get(target.getUniqueId()).get(type);
            
            if (shadow == null) {
                admin.sendMessage(ChatColor.RED + "Target player doesn't have this shadow type!");
                return;
            }

            ConfigurationSection titles = getConfig().getConfigurationSection("customization.titles");
            if (titles != null && titles.contains(titleKey)) {
                String titleName = titles.getString(titleKey + ".name");
                shadow.title = titleName;
                shadow.unlockedCustomizations.put("title_" + titleKey, true);
                
                admin.sendMessage(ChatColor.GREEN + "Successfully set " + titleName + " title for " + 
                                target.getName() + "'s " + type.name() + " shadow!");
                target.sendMessage(ChatColor.GREEN + "An admin has given you the " + titleName + " title for your " + 
                                 type.name() + " shadow!");
                
                // Update active pet's name if it exists
                if (shadow.activePet != null && shadow.activePet.isValid()) {
                    updateShadowDisplayName(shadow);
                }
                
                saveShadowData();
            } else {
                admin.sendMessage(ChatColor.RED + "Invalid title key!");
            }
        } catch (IllegalArgumentException e) {
            admin.sendMessage(ChatColor.RED + "Invalid shadow type!");
        }
    }

    private void updateShadowDisplayName(Shadow shadow) {
        if (shadow.activePet == null || !shadow.activePet.isValid()) return;
        
        String displayName = "";
        if (!shadow.title.isEmpty()) {
            displayName += ChatColor.GOLD + "[" + shadow.title + "] ";
        }
        
        if (!shadow.customName.isEmpty()) {
            displayName += shadow.customName;
        } else {
            displayName += ChatColor.GRAY + shadow.type.name() + " Shadow";
        }
        
        shadow.activePet.setCustomName(displayName);
        shadow.activePet.setCustomNameVisible(true);
    }

    private EntityType getCurrentShadowType(Player player) {
        Entity activePet = activePets.get(player.getUniqueId());
        if (activePet != null) {
            return activePet.getType();
        }
        return null;
    }

    private void openParticleEffectsGUI(Player player, EntityType shadowType) {
        if (shadowType == null) {
            player.sendMessage(ChatColor.RED + "You must have an active shadow to modify its effects!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE_PARTICLES);

        ConfigurationSection effects = getConfig().getConfigurationSection("customization.particle_effects");
        if (effects != null) {
            int slot = 10;
            for (String effectKey : effects.getKeys(false)) {
                ConfigurationSection effect = effects.getConfigurationSection(effectKey);
                if (effect == null) continue;

                String name = effect.getString("name");
                String description = effect.getString("description");
                int cost = effect.getInt("cost");

                Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
                boolean unlocked = shadow != null && shadow.unlockedCustomizations.getOrDefault("particle_" + effectKey, false);

                List<String> lore = Arrays.asList(
                    ChatColor.GRAY + description,
                    "",
                    ChatColor.YELLOW + "Cost: " + cost + " souls",
                    "",
                    unlocked ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "LOCKED"
                );

                ItemStack effectItem = createGuiItem(Material.BLAZE_POWDER, ChatColor.GOLD + name, lore.toArray(new String[0]));
                gui.setItem(slot++, effectItem);
            }
        }

        gui.setItem(18, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Back"));
        gui.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "Close"));

        player.openInventory(gui);
    }

    private void openTitlesGUI(Player player, EntityType shadowType) {
        if (shadowType == null) {
            player.sendMessage(ChatColor.RED + "You must have an active shadow to modify its title!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE_TITLES);

        ConfigurationSection titles = getConfig().getConfigurationSection("customization.titles");
        if (titles != null) {
            int slot = 10;
            for (String titleKey : titles.getKeys(false)) {
                ConfigurationSection title = titles.getConfigurationSection(titleKey);
                if (title == null) continue;

                String name = title.getString("name");
                String description = title.getString("description");
                int cost = title.getInt("cost");

                Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
                boolean unlocked = shadow != null && shadow.unlockedCustomizations.getOrDefault("title_" + titleKey, false);

                List<String> lore = Arrays.asList(
                    ChatColor.GRAY + description,
                    "",
                    ChatColor.YELLOW + "Cost: " + cost + " souls",
                    "",
                    unlocked ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "LOCKED"
                );

                ItemStack titleItem = createGuiItem(Material.NAME_TAG, ChatColor.GOLD + name, lore.toArray(new String[0]));
                gui.setItem(slot++, titleItem);
            }
        }

        gui.setItem(18, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Back"));
        gui.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "Close"));

        player.openInventory(gui);
    }

    private void handleCustomizationGuiClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        String title = event.getView().getTitle();
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        EntityType shadowType = getCurrentShadowType(player);
        if (shadowType == null && !title.equals(GUI_TITLE_CUSTOMIZATION)) {
            player.sendMessage(ChatColor.RED + "You must have an active shadow to customize it!");
            return;
        }

        if (title.equals(GUI_TITLE_CUSTOMIZATION)) {
            switch (clicked.getType()) {
                case BLAZE_POWDER -> openParticleEffectsGUI(player, shadowType);
                case NAME_TAG -> openTitlesGUI(player, shadowType);
            }
        } else if (title.equals(GUI_TITLE_PARTICLES)) {
            handleParticleEffectSelection(player, clicked);
        } else if (title.equals(GUI_TITLE_TITLES)) {
            handleTitleSelection(player, clicked);
        }
    }

    private void handleParticleEffectSelection(Player player, ItemStack clicked) {
        if (!clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

        String effectName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        EntityType shadowType = getCurrentShadowType(player);
        if (shadowType == null) {
            player.sendMessage(ChatColor.RED + "You must have an active shadow to modify its particle effects!");
            return;
        }

        Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
        if (shadow == null) return;

        ConfigurationSection effects = getConfig().getConfigurationSection("customization.particle_effects");
        if (effects != null) {
            for (String effectKey : effects.getKeys(false)) {
                ConfigurationSection effect = effects.getConfigurationSection(effectKey);
                if (effect != null && effectName.equals(effect.getString("name"))) {
                    int cost = effect.getInt("cost");
                    if (shadow.unlockedCustomizations.getOrDefault("particle_" + effectKey, false)) {
                        shadow.particleEffect = effectKey;
                        player.sendMessage(ChatColor.GREEN + "Particle effect applied!");
                    } else if (getSouls(player) >= cost) {
                        setSouls(player, getSouls(player) - cost);
                        shadow.unlockedCustomizations.put("particle_" + effectKey, true);
                        shadow.particleEffect = effectKey;
                        player.sendMessage(ChatColor.GREEN + "Particle effect purchased and applied!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Not enough souls! Need " + cost + " souls.");
                    }
                    break;
                }
            }
        }
    }

    private void handleTitleSelection(Player player, ItemStack clicked) {
        if (!clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

        String titleName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        EntityType shadowType = getCurrentShadowType(player);
        if (shadowType == null) {
            player.sendMessage(ChatColor.RED + "You must have an active shadow to modify its title!");
            return;
        }

        Shadow shadow = playerShadows.get(player.getUniqueId()).get(shadowType);
        if (shadow == null) return;

        ConfigurationSection titles = getConfig().getConfigurationSection("customization.titles");
        if (titles != null) {
            for (String titleKey : titles.getKeys(false)) {
                ConfigurationSection title = titles.getConfigurationSection(titleKey);
                if (title != null && titleName.equals(title.getString("name"))) {
                    int cost = title.getInt("cost");
                    if (shadow.unlockedCustomizations.getOrDefault("title_" + titleKey, false)) {
                        shadow.title = titleName;
                        updateShadowDisplayName(shadow);
                        player.sendMessage(ChatColor.GREEN + "Title applied!");
                    } else if (getSouls(player) >= cost) {
                        setSouls(player, getSouls(player) - cost);
                        shadow.unlockedCustomizations.put("title_" + titleKey, true);
                        shadow.title = titleName;
                        updateShadowDisplayName(shadow);
                        player.sendMessage(ChatColor.GREEN + "Title purchased and applied!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Not enough souls! Need " + cost + " souls.");
                    }
                    break;
                }
            }
        }
    }

    private void handleShadowCommand(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showShadowHelp(player);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "customize" -> openCustomizationGUI(player);
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /shadow rename <type> <name>");
                    return;
                }
                handleShadowRename(player, args[1], args[2]);
            }
            default -> showShadowHelp(player);
        }
    }

    private void showShadowHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Shadow Commands ===");
        player.sendMessage(ChatColor.GOLD + "/shadow customize " + ChatColor.WHITE + "- Open customization menu");
        player.sendMessage(ChatColor.GOLD + "/shadow rename <type> <name> " + ChatColor.WHITE + "- Rename your shadow");
    }

    private void saveShadowData() {
        try {
            File shadowDataFile = new File(getDataFolder(), "shadowdata.yml");
            if (!shadowDataFile.exists()) {
                shadowDataFile.createNewFile();
            }

            YamlConfiguration data = new YamlConfiguration();
            for (Map.Entry<UUID, Map<EntityType, Shadow>> entry : playerShadows.entrySet()) {
                String playerUUID = entry.getKey().toString();
                ConfigurationSection playerSection = data.createSection(playerUUID);
                
                for (Map.Entry<EntityType, Shadow> shadowEntry : entry.getValue().entrySet()) {
                    String shadowType = shadowEntry.getKey().name();
                    Shadow shadow = shadowEntry.getValue();
                    ConfigurationSection shadowSection = playerSection.createSection(shadowType);
                    
                    // Save customization data
                    shadowSection.set("customName", shadow.customName);
                    shadowSection.set("title", shadow.title);
                    shadowSection.set("particleEffect", shadow.particleEffect);
                    shadowSection.set("kills", shadow.kills);
                    shadowSection.set("evolutionLevel", shadow.evolutionLevel);
                    shadowSection.set("evolutionPath", shadow.evolutionPath);
                    shadowSection.set("evolutionPoints", shadow.evolutionPoints);
                    
                    // Save unlocked customizations
                    ConfigurationSection unlockedSection = shadowSection.createSection("unlockedCustomizations");
                    for (Map.Entry<String, Boolean> unlocked : shadow.unlockedCustomizations.entrySet()) {
                        unlockedSection.set(unlocked.getKey(), unlocked.getValue());
                    }
                }
            }
            
            data.save(shadowDataFile);
            getLogger().info("Successfully saved shadow data");
        } catch (Exception e) {
            getLogger().severe("Failed to save shadow data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadShadowData() {
        try {
            File shadowDataFile = new File(getDataFolder(), "shadowdata.yml");
            if (!shadowDataFile.exists()) {
                getLogger().info("No shadow data file found, creating new one");
                return;
            }

            YamlConfiguration data = YamlConfiguration.loadConfiguration(shadowDataFile);
            for (String playerUUID : data.getKeys(false)) {
                UUID uuid = UUID.fromString(playerUUID);
                Map<EntityType, Shadow> shadows = new HashMap<>();
                ConfigurationSection playerSection = data.getConfigurationSection(playerUUID);
                
                if (playerSection != null) {
                    for (String shadowType : playerSection.getKeys(false)) {
                        try {
                            EntityType type = EntityType.valueOf(shadowType);
                            ConfigurationSection shadowSection = playerSection.getConfigurationSection(shadowType);
                            
                            if (shadowSection != null) {
                                Shadow shadow = new Shadow(type);
                                shadow.customName = shadowSection.getString("customName", "");
                                shadow.title = shadowSection.getString("title", "");
                                shadow.particleEffect = shadowSection.getString("particleEffect", "");
                                shadow.kills = shadowSection.getInt("kills", 0);
                                shadow.evolutionLevel = shadowSection.getString("evolutionLevel", "Basic");
                                shadow.evolutionPath = shadowSection.getString("evolutionPath", "");
                                shadow.evolutionPoints = shadowSection.getInt("evolutionPoints", 0);
                                
                                // Load unlocked customizations
                                ConfigurationSection unlockedSection = shadowSection.getConfigurationSection("unlockedCustomizations");
                                if (unlockedSection != null) {
                                    for (String key : unlockedSection.getKeys(false)) {
                                        shadow.unlockedCustomizations.put(key, unlockedSection.getBoolean(key));
                                    }
                                }
                                
                                shadows.put(type, shadow);
                            }
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Invalid shadow type in data file: " + shadowType);
                        }
                    }
                }
                
                playerShadows.put(uuid, shadows);
            }
            
            getLogger().info("Successfully loaded shadow data");
        } catch (Exception e) {
            getLogger().severe("Failed to load shadow data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleBlackMarketPurchase(Player player, ItemStack item) {
        // Implementation for black market purchases
    }

    private void openCustomizationGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE_CUSTOMIZATION);

        ItemStack particles = createGuiItem(
            Material.BLAZE_POWDER,
            ChatColor.GOLD + "Particle Effects",
            ChatColor.GRAY + "Customize your shadow's particle effects"
        );

        ItemStack titles = createGuiItem(
            Material.NAME_TAG,
            ChatColor.GOLD + "Titles",
            ChatColor.GRAY + "Choose a title for your shadow"
        );

        gui.setItem(11, particles);
        gui.setItem(15, titles);
        gui.setItem(22, createGuiItem(Material.BARRIER, ChatColor.RED + "Close"));

        player.openInventory(gui);
    }

    private void handleShadowRename(Player player, String typeStr, String newName) {
        try {
            EntityType type = EntityType.valueOf(typeStr.toUpperCase());
            Shadow shadow = playerShadows.get(player.getUniqueId()).get(type);
            
            if (shadow == null) {
                player.sendMessage(ChatColor.RED + "You don't have this shadow type!");
                return;
            }

            shadow.customName = newName;
            if (shadow.activePet != null && shadow.activePet.isValid()) {
                updateShadowDisplayName(shadow);
            }
            
            player.sendMessage(ChatColor.GREEN + "Shadow renamed successfully!");
            saveShadowData();
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid shadow type!");
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    cleanupInvalidPets();
                } catch (Exception e) {
                    getLogger().warning("Error during cleanup: " + e.getMessage());
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void cleanupTasks() {
        activeParticleTasks.forEach(BukkitRunnable::cancel);
    }

    private void cleanupInvalidPets() {
        synchronized (activePets) {
            Iterator<Map.Entry<UUID, Entity>> iterator = activePets.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Entity> entry = iterator.next();
                Entity pet = entry.getValue();
                if (!pet.isValid()) {
                    iterator.remove();
                    Player owner = Bukkit.getPlayer(entry.getKey());
                    if (owner != null) {
                        owner.sendMessage(ChatColor.RED + "Your shadow pet was lost!");
                    }
                }
            }
        }
    }

    private void removeActivePets() {
        activePets.values().stream().filter(Entity::isValid).forEach(Entity::remove);
    }

    private int getSouls(Player player) {
        return getConfig().getInt("souls." + player.getUniqueId(), 0);
    }

    private void setSouls(Player player, int souls) {
        getConfig().set("souls." + player.getUniqueId(), souls);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        try {
            switch (command.getName().toLowerCase()) {
                case "souls" -> handleSoulsCommand(player, args);
                case "shadow" -> handleShadowCommand(player, args);
                case "blackmarket" -> openBlackMarket(player);
                case "soulsadmin" -> {
                    if (!player.hasPermission("unchainedsouls.admin")) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                        return true;
                    }
                    handleAdminCommand(player, args);
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error executing command: " + command.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while executing the command.");
        }

        return true;
    }

    private void handleSoulsCommand(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showSoulsHelp(player);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "balance" -> showBalance(player);
            case "withdraw", "deposit" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /souls " + subCommand + " <amount>");
                    return;
                }
                try {
                    int amount = Integer.parseInt(args[1]);
                    handleTransaction(player, subCommand, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Please enter a valid number!");
                }
            }
            default -> player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /souls help for commands.");
        }
    }

    private void handleAdminCommand(Player admin, String[] args) {
        if (args.length < 3) {
            admin.sendMessage(ChatColor.RED + "Usage: /soulsadmin <give|take|set> <player> <amount>");
            return;
        }

        try {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                admin.sendMessage(ChatColor.RED + "Player not found!");
                return;
            }

            int amount = Integer.parseInt(args[2]);
            int currentSouls = getSouls(target);

            switch (args[0].toLowerCase()) {
                case "give" -> setSouls(target, currentSouls + amount);
                case "take" -> setSouls(target, Math.max(0, currentSouls - amount));
                case "set" -> setSouls(target, amount);
                default -> {
                    admin.sendMessage(ChatColor.RED + "Invalid action! Use give, take, or set.");
                    return;
                }
            }

            admin.sendMessage(ChatColor.GREEN + "Successfully modified " + target.getName() + "'s souls!");
            target.sendMessage(ChatColor.GREEN + "Your soul balance has been updated!");
        } catch (NumberFormatException e) {
            admin.sendMessage(ChatColor.RED + "Please enter a valid number!");
        }
    }

    private void showSoulsHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Souls Commands ===");
        player.sendMessage(ChatColor.GOLD + "/souls balance " + ChatColor.WHITE + "- Check your soul balance");
        player.sendMessage(ChatColor.GOLD + "/souls withdraw <amount> " + ChatColor.WHITE + "- Withdraw souls");
        player.sendMessage(ChatColor.GOLD + "/souls deposit <amount> " + ChatColor.WHITE + "- Deposit souls");
    }

    private void showBalance(Player player) {
        int souls = getSouls(player);
        player.sendMessage(ChatColor.GREEN + "Your soul balance: " + ChatColor.GOLD + souls + " souls");
    }

    private void handleTransaction(Player player, String type, int amount) {
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Amount must be greater than zero!");
            return;
        }

        int currentSouls = getSouls(player);
        if (type.equals("withdraw")) {
            if (currentSouls < amount) {
                player.sendMessage(ChatColor.RED + "You don't have enough souls!");
                return;
            }
            setSouls(player, currentSouls - amount);
            player.sendMessage(ChatColor.GREEN + "Successfully withdrew " + amount + " souls!");
        } else {
            setSouls(player, currentSouls + amount);
            player.sendMessage(ChatColor.GREEN + "Successfully deposited " + amount + " souls!");
        }
    }

    private void openBlackMarket(Player player) {
        // Implementation for black market will be added later
        player.sendMessage(ChatColor.GREEN + "Black Market is coming soon!");
    }
}
