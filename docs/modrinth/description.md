# ChainMine Core

**Mine entire veins in one swing** — the spiritual successor of Vein Miner, built for Fabric 1.21.1 with modern features out of the box.

---

## ✨ Features

- **Sneak to chain-break** — Hold Shift and mine any block to chain-break a regular 3×3×3 cube of the same block type.
- **Two scan modes** — `CUBE` (default, regular 3D shape — perfect for clean stone/ore clearing) and `CONNECTED` (6-way BFS — for organic vein mining).
- **Empty hand & any tool** — Even without a tool, just sneak-mine dirt/gravel to chain them. Works with pickaxe, axe, shovel, hoe.
- **Mining-level gate** — Uses vanilla `canHarvest()`: empty hand or wrong-tier tools cannot chain-break blocks that require a specific tool level (e.g. diamond ore still needs iron+ pickaxe to actually drop loot).
- **Durability protection** — Never breaks your tool mid-chain. The chain stops automatically when remaining durability reaches 1.
- **Per-tick block breaking** — 8 blocks/tick by default (configurable). Mining 64 blocks no longer causes a server TPS spike.
- **Server-authoritative** — All block breaking happens server-side via `ServerWorld#breakBlock`. Anti-cheat friendly.
- **HUD counter** — Brief on-screen counter after each chain.
- **Live config reload** — `/chainmine-core reload` updates without restart (OP only).
- **Mod Menu integration** — Configure via the standard Mod Menu UI.
- **Full localization** — English & Simplified Chinese included.

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) **0.16.10+** for Minecraft **1.21.1**
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1
3. *(Optional)* Install [Mod Menu](https://modrinth.com/mod/modmenu) for the in-game config screen
4. Drop `chainmine-core-1.1.0.jar` into your `mods/` folder

---

## ⚙️ Configuration

Config file: `config/chainmine-core.json` (auto-generated on first run). Reload with `/chainmine-core reload`.

```jsonc
{
  "requireSneak": true,                 // Sneak to trigger (set false to chain on any break)
  "maxBlocks": 64,                      // Max blocks per chain (1-256, auto-clamped)
  "allowEmptyHand": true,               // Empty hand can trigger chains
  "requireCorrectTool": false,          // Strict mode: only the correct tool type works
  "durabilityProtection": true,         // Stop chain before tool breaks
  "blocksPerTick": 8,                   // 0 = all in one tick (may lag); 8 = smooth
  "scanMode": "CUBE",                   // CUBE = regular 3D shape, CONNECTED = 6-way BFS
  "scanSize": 3,                        // Cube edge length (3/5/7/9)
  "toolWhitelist": ["pickaxe", "axe", "shovel", "hoe"],
  "blockBlacklist": ["minecraft:bedrock", "minecraft:spawner"]
}
```

### Commands (OP, permission level 2)

| Command | Description |
| --- | --- |
| `/chainmine-core reload` | Hot-reload configuration |
| `/chainmine-core status` | View current config summary |

---

## 🔧 Compatibility

- **Coexists with FTB Ultimine / Ore Excavation** — ChainMine Core hooks `ServerPlayerInteractionManager#tryBreakBlock` (where the block is *actually* removed), not the mining-progress event. It doesn't fight other chain-mods for the same event.
- **Anti-cheat friendly** — All destruction is server-side; clients only receive the HUD counter payload, which cannot be forged.
- **Performance safe** — Iterative BFS + per-tick block limit guarantee bounded work per activation.

---

## 📷 Gallery

*In-game screenshots coming soon. Build tested on dedicated server: `Done (6s)`, zero crashes, chainmine-core 1.1.0 loaded successfully.*

---

## 🛠 Development

- **Source**: https://github.com/sansenjian/ChainMine-Core
- **Java**: 21 · **Gradle**: 8.14.3 · **Loom**: 1.10-SNAPSHOT · **Yarn**: 1.21.1+build.3
- **Tests**: 13 JUnit cases (`./gradlew test`)
- **License**: MIT

---

## 简体中文说明

ChainMine Core 是经典 Vein Miner 的精神续作。潜行（Shift）挖掘一个方块，即可连锁破坏一个规则立方体（默认 3×3×3）内所有相同类型的方块。支持空手和任意工具（受挖掘等级限制：钻石矿等需要对应等级工具才会掉落）。所有逻辑服务端执行，13 个单元测试通过。MIT 协议开源。
