# Changelog

All notable changes to **ChainMine Core** are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/), version follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-08-24

### Added
- Initial release on Modrinth
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
