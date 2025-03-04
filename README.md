# UnchainedSouls Plugin

A Minecraft Bukkit plugin implementing a shadow/pet system where players can extract and evolve shadow pets from mobs.

## Requirements

- Java 17 or higher
- Maven
- Bukkit/Paper server (tested on 1.21.4)

## Project Structure

```
src/
├── main/
│   ├── java/me/unchainedsouls/unchainedSoulsBeta/
│   │   └── SoulsPlugin.java
│   └── resources/
│       ├── plugin.yml
│       ├── config.yml
│       ├── shadowdata.yml
│       └── blackmarket.yml
```

## Building

1. Clone this repository
2. Run `mvn clean package`
3. The compiled plugin will be in `target/unchainedSoulsBeta-1.0-SNAPSHOT.jar`

## Installation

1. Copy the compiled JAR file from `target/unchainedSoulsBeta-1.0-SNAPSHOT.jar` to your server's `plugins` folder
2. Restart your server
3. The plugin will generate default configuration files in `plugins/UnchainedSouls/`

## Commands

- `/souls` - Manage your souls
  - `/souls balance` - Check your soul balance
  - `/souls withdraw <amount>` - Withdraw souls
  - `/souls deposit <amount>` - Deposit souls

- `/shadow` - Manage your shadow pets
  - `/shadow list` - Show your shadows
  - `/shadow summon <type>` - Summon a shadow
  - `/shadow dismiss` - Dismiss current shadow

- `/esouls` - Extract souls from nearby mobs
- `/eshadow` - Extract shadow from nearby mobs

## Permissions

- `unchainedsouls.souls` - Access to soul-related commands
- `unchainedsouls.shadow` - Access to shadow-related commands
- `unchainedsouls.extract.souls` - Ability to extract souls
- `unchainedsouls.extract.shadow` - Ability to extract shadows
