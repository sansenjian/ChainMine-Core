# ChainMine Core —— 多加载器重构架构说明（骨架）

> 目标：让 1.21.2+ 线同时产出 **Fabric** 与 **NeoForge** 两个 jar，共享同一份核心逻辑。
> 本文档描述骨架阶段已完成的抽取与剩余接入步骤。
> 状态：**骨架已搭（不接入构建）**，等待 1.21.5 验证收尾后正式接入。

---

## 一、目标结构

```
ChainMine/
├── common/                     ← 跨加载器共享核心（本骨架已完成）
│   ├── build.gradle            （fabric-loom + yarn 编译，预留 architectury）
│   └── src/
│       ├── main/java/com/chainmine/core/
│       │   ├── ChainMineConstants.java    MOD_ID + 统一 LOGGER
│       │   ├── platform/
│       │   │   ├── ChainMinePlatform.java 平台抽象接口（4 个方法）
│       │   │   └── Platforms.java         平台实现持有者（get/set）
│       │   ├── scan/
│       │   │   ├── BlockProvider.java     方块读取接口（从 VeinScanner 拆出）
│       │   │   └── VeinScanner.java       BFS + cubeScan（原样搬移）
│       │   ├── config/
│       │   │   └── ChainMineConfig.java   Gson 配置（解耦 FabricLoader/LOGGER）
│       │   └── breaker/
│       │       └── ChainBreaker.java      分 tick 破坏器（解耦 tick 事件/网络回调）
│       └── test/java/com/chainmine/core/scan/
│           └── VeinScannerTest.java       13 个用例（原样搬移）
├── fabric/                      ← Fabric 加载器模块（TODO）
│   ├── Mixin（tryBreakBlock） + 注册 + payload 网络 + HUD + ModMenu
│   └── ChainMineFabricPlatform implements ChainMinePlatform
├── neoforge/                    ← NeoForge 加载器模块（TODO）
│   ├── BlockEvent.BreakEvent 拦截 + DeferredRegister + 网络 + GUI
│   └── ChainMineNeoForgePlatform implements ChainMinePlatform
└── （原 src/main 在迁移完成后删除）
```

## 二、架构路线（为什么 common 用 yarn 编译）

- **common 源码用 yarn 映射编写**（与现有 Fabric 代码一致 → 几乎零改动搬入）
- **fabric 模块**：直接消费 common（同 yarn 映射）
- **neoforge 模块**：构建时由 **Architectury 插件自动把 common 的 yarn 字节码 remap 到 mojmap**
  （NeoForge 原生映射），无需维护两份源码

## 三、本次已完成的解耦

| 原依赖 | 解耦方式 |
| --- | --- |
| `FabricLoader.getInstance().getConfigDir()`（Config） | → `Platforms.get().getConfigDir()` |
| `ChainMine.LOGGER`（Config） | → `ChainMineConstants.LOGGER`（slf4j 独立） |
| `ServerTickEvents` / `ServerLifecycleEvents`（Breaker） | → `Platforms.get().registerServerTick()` / `onServerStopped()` |
| `ChainMineNetworking.sendChainCount`（Breaker） | → `Platforms.get().sendChainCount()` |
| `VeinScanner.BlockProvider`（内部接口） | → 拆为独立 `scan/BlockProvider`（common 共享） |

**剩余未抽取（留在加载器模块）**：挖掘拦截（Mixin vs BreakEvent）、物品注册、命令、网络 payload、HUD、ModMenu。

## 四、接入步骤（后续执行）

1. 根 `settings.gradle`：`include 'common', 'fabric', 'neoforge'`（改多项目）
2. 根 `build.gradle`：`architectury { common("fabric", "neoforge") }`
3. `common/build.gradle`：放开 architectury 插件
4. 新建 `fabric/` 模块：迁入现有 src/main 的平台代码 + `ChainMineFabricPlatform`
5. 新建 `neoforge/` 模块：`BlockEvent.BreakEvent` 拦截 + `ChainMineNeoForgePlatform`
6. 验证：`./gradlew :fabric:build :neoforge:build`，产出 fabric + neoforge 双 jar
7. 迁移完成后删除旧 `src/main`（避免重复编译）

## 五、注意事项

- **1.21.5 验证**（后台任务）收尾后，gradle.properties 需恢复 1.21.11 基线（备份在 `gradle.properties.12111-backup`）
- common 的 `build.gradle` 引用了根 gradle.properties 的版本变量，接入前保持根配置为 1.21.2+ 线
- NeoForge 侧 `BreakEvent` 在服务器逻辑线程触发，与 `ChainBreaker` 的线程模型一致（服务端主线程）
- 配置、扫描、破坏器 100% 复用；平台差异仅剩 4 个薄文件（Mixin/事件、注册、网络、HUD）
