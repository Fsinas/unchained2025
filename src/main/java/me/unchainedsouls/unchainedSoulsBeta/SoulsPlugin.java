package me.unchainedsouls.unchainedSoulsBeta;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * Main class for the Unchained Souls plugin.
 * This class initializes the plugin and handles its lifecycle events.
 */
public class SoulsPlugin extends JavaPlugin {

    // Logger instance for logging plugin events
    private static final Logger LOGGER = Logger.getLogger("UnchainedSouls");

    /**
     * Called when the plugin is enabled.
     * This is where initialization logic should be placed.
     */
    @Override
    public void onEnable() {
        // Log that the plugin is being enabled
        LOGGER.info("[UnchainedSouls] Plugin is being enabled...");

        // Perform any necessary initialization here
        initializeConfig();

        // Log that the plugin has been successfully enabled
        LOGGER.info("[UnchainedSouls] Plugin has been successfully enabled!");
    }

    /**
     * Called when the plugin is disabled.
     * This is where cleanup logic should be placed.
     */
    @Override
    public void onDisable() {
        // Log that the plugin is being disabled
        LOGGER.info("[UnchainedSouls] Plugin is being disabled...");

        // Perform any necessary cleanup here

        // Log that the plugin has been successfully disabled
        LOGGER.info("[UnchainedSouls] Plugin has been successfully disabled!");
    }

    /**
     * Initializes the plugin's configuration files.
     * Ensures that default configuration files are created if they do not exist.
     */
    private void initializeConfig() {
        // Save the default config.yml if it doesn't exist
        saveDefaultConfig();

        // Log that the configuration has been initialized
        LOGGER.info("[UnchainedSouls] Default configuration has been initialized.");
    }
}
```

### Step 4: Review the Code
- The class `SoulsPlugin` extends `JavaPlugin`, which is required for all Bukkit plugins.
- The `onEnable` method logs the enabling process and initializes the configuration.
- The `onDisable` method logs the disabling process.
- The `initializeConfig` method ensures the default `config.yml` file is created if it doesn't already exist.
- The code adheres to Java conventions and is fully functional.

### Final Output
The complete file content is provided below:

```
package me.unchainedsouls.unchainedSoulsBeta;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * Main class for the Unchained Souls plugin.
 * This class initializes the plugin and handles its lifecycle events.
 */
public class SoulsPlugin extends JavaPlugin {

    // Logger instance for logging plugin events
    private static final Logger LOGGER = Logger.getLogger("UnchainedSouls");

    /**
     * Called when the plugin is enabled.
     * This is where initialization logic should be placed.
     */
    @Override
    public void onEnable() {
        // Log that the plugin is being enabled
        LOGGER.info("[UnchainedSouls] Plugin is being enabled...");

        // Perform any necessary initialization here
        initializeConfig();

        // Log that the plugin has been successfully enabled
        LOGGER.info("[UnchainedSouls] Plugin has been successfully enabled!");
    }

    /**
     * Called when the plugin is disabled.
     * This is where cleanup logic should be placed.
     */
    @Override
    public void onDisable() {
        // Log that the plugin is being disabled
        LOGGER.info("[UnchainedSouls] Plugin is being disabled...");

        // Perform any necessary cleanup here

        // Log that the plugin has been successfully disabled
        LOGGER.info("[UnchainedSouls] Plugin has been successfully disabled!");
    }

    /**
     * Initializes the plugin's configuration files.
     * Ensures that default configuration files are created if they do not exist.
     */
    private void initializeConfig() {
        // Save the default config.yml if it doesn't exist
        saveDefaultConfig();

        // Log that the configuration has been initialized
        LOGGER.info("[UnchainedSouls] Default configuration has been initialized.");
    }
}
