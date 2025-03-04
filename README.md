# 🌙 UnchainedSouls Plugin

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4-brightgreen)  
![Java Version](https://img.shields.io/badge/Java-17%2B-blue)  
![Paper](https://img.shields.io/badge/Server-Paper-orange)  
![License](https://img.shields.io/badge/License-Use%20At%20Your%20Own%20Risk-red)

A **Minecraft Paper plugin** that unleashes a mystical shadow/pet system. Extract souls, evolve shadow pets, and dominate with your dark companions!

> ⚠️ *Crafted with AI assistance—proceed with caution!*

---

## ✨ Features
- **Soul Extraction**: Harvest souls and shadows from mobs.  
- **Shadow Pets**: Summon, evolve, and dismiss your shadowy allies.  
- **Soul Economy**: Manage your soul currency with ease.  
- **Black Market**: Mysterious trades await (WIP).  

---

## 📋 Requirements
- **Java**: 17 or higher  
- **Server**: Paper (tested on 1.21.4)  

---

## 🗂 Project Structure
```plaintext
src/
├── main/
│   ├── java/me/unchainedsouls/unchainedSoulsBeta/
│   │   └── SoulsPlugin.java       # Core plugin logic
│   └── resources/
│       ├── plugin.yml             # Plugin metadata
│       ├── config.yml             # General settings
│       ├── shadowdata.yml         # Shadow pet data
│       └── blackmarket.yml        # Black market config
```
## 🚀 Installation
- Drop unchainedSoulsBeta-1.0-SNAPSHOT.jar into your plugins folder.
- Restart your server.
- Customize the generated configs in plugins/UnchainedSouls/.
---
## 🎮 Commands
/souls - Soul Management
Command	Description
- /souls balance	Check your soul balance.
- /souls withdraw <amount>	Withdraw souls.
- /souls deposit <amount>	Deposit souls.
- /shadow - Shadow Pet Control
## Command	Description
- /shadow list	List your shadow pets.
- /shadow summon <type>	Summon a shadow pet.
- /shadow dismiss	Dismiss your active shadow.
Extraction Commands
Command	Description
- /esouls	Extract souls from nearby mobs.
- /eshadow	Extract shadows from nearby mobs.
## 🔑 Permissions
(Customize these as needed)
unchainedsouls.souls.use - Access /souls commands.
unchainedsouls.shadow.manage - Manage shadow pets.
unchainedsouls.extract - Use extraction commands.

This plugin is being made with half AI/half of my personal knowlegde and searching on the internet. Not perfect, not planning to release it anyways. I have 0 Experience with programming and this is just testing of how much i can do with only AI/research. If you are a dev who wants to help out or take over the project, please lmk in the discord.
