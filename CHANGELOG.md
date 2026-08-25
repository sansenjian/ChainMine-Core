# Changelog

All notable changes to **ChainMine Core** are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/), version follows [Semantic Versioning](https://semver.org/).

## [1.2.3] - 2026-08-26

### Added
- **Version-aware runtime adaptation** (`VersionCompat`) — one JAR now runs across **Minecraft 1.21.2 ~ 1.21.11**:
  - `Entity.getWorld()` vs `Entity.getEntityWorld()` resolved at runtime via intermediary-name reflection (method-existence probing, no hardcoded version checks)
  - Permission check adapts between `hasPermissionLevel(int)` (≤1.21.10) and the new `Permission.Level` system (1.21.11+)
- **Verified on 4 anchor versions** — real dedicated-server boots on 1.21.2, 1.21.5, 1.21.9 and 1.21.11 (build + 13/13 tests + `Done`), covering every API combination in the range.
- Verified `tryBreakBlock` mixin target stability (`method_14266`) across 1.21.5 / 1.21.10 / 1.21.11.

### Changed
- Command permission check now goes through `VersionCompat.hasOpLevel2`.
- World lookup in mixin + breaker now goes through `VersionCompat.getServerWorld`.

## [1.2.0] - 2026-08-25

### Changed
- **Universal build for Minecraft 1.21.2 ~ 1.21.11** — one JAR covers the entire 1.21.2+ generation (verified end-to-end on 1.21.11: build, 13/13 tests, dedicated server `Done`).
- Migrated to 1.21.2+ APIs (breaking changes introduced by the 1.21.2 refactor):
  - `getServerWorld()` → `entity.getEntityWorld()`
  - `hasPermissionLevel(int)` → `PermissionPredicate` / `Permission.Level(PermissionLevel.GAMEMASTERS)`
  - `PickaxeItem`/`AxeItem`/... removed → `ItemTags.PICKAXES/AXES/SHOVELS/HOES`
  - Item registration now requires an explicit `registryKey` (avoids `Item id not set` NPE)
- Toolchain: Fabric Loom 1.17.19 + Gradle 9.7.1 + Fabric Loader 0.19.3
- Mod Menu integration temporarily removed (optional dependency; will re-add in a follow-up)
- `fabric.mod.json`: `"minecraft": ">=1.21.2 <1.22"`

### Note
- Minecraft 1.21.1 remains supported by the previous `1.1.0` release (separate build line; the 1.21.1 → 1.21.2 API break cannot be bridged by a single JAR).

## [1.1.0] - 2026-08-25

### Added
- Initial public release (Modrinth) — version 1.1.0 carries the feature set of the full development iteration:
- **Core mechanic**: sneak + mine any block to chain-break identical neighbors
- **Two scan modes** (configurable, default `CUBE`):
  - `CUBE` — regular 3D shape (default `3×3×3`); clean stone/ore clearing
  - `CONNECTED` — 6-way BFS vein mining
- **Empty-hand support** — sneak-mine dirt/gravel without a tool
- **Mining-level gate** — `PlayerEntity.canHarvest` blocks invalid empty-hand chains on tool-required ores
- **Durability protection** — auto-stop at 1 durability remaining
- **Per-tick block breaking** (default 8/tick) — prevents server TPS spikes
- **Live config hot-reload** via `/chainmine-core reload` (OP)
- **HUD counter** — 3-second on-screen feedback after each chain
- **Mod Menu integration** with config screen
- **Full localization** — English (`en_us`) and Simplified Chinese (`zh_cn`)
- **MIT licensed** source code

### Tech
- Fabric 1.21.1 · Loom 1.10-SNAPSHOT · Yarn 1.21.1+build.3 · Java 21
- 13 JUnit tests covering BFS/cubeScan, boundaries, mining-level gate
- Server-authoritative: hooks `tryBreakBlock`, breaks via `ServerWorld#breakBlock`
