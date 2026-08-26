# ChainMine Core —— 多加载器架构（Architectury 落地）

> 目标：同一份核心逻辑（common）同时产出 **Fabric** 与 **NeoForge** 两个 jar。
> 状态：**fabric 模块已构建验证（runServer Done）；neoforge 模块源码就绪，构建需本地环境**。

## 一、项目结构

```
ChainMine/
├── common/                         ← 跨加载器共享核心（yarn 映射）
│   └── src/main/java/com/chainmine/core/
│       ├── ChainMineConstants.java     MOD_ID + 统一 LOGGER
│       ├── platform/
│       │   ├── ChainMinePlatform.java  平台抽象（config 目录 / tick / 停止 / 网络计数）
│       │   └── Platforms.java          平台实现持有者
│       ├── scan/                       VeinScanner（BFS + CUBE）+ BlockProvider
│       ├── config/                     ChainMineConfig（Gson，无加载器依赖）
│       ├── breaker/                    ChainBreaker（分 tick 破坏器）
│       └── VersionCompat.java          （Fabric 专用版本自适应，见下）
├── fabric/                          ← Fabric 模块（loom）
│   └── src/main/java/com/chainmine/
│       ├── ChainMine.java              ModInitializer 入口（Platforms.set 最先）
│       ├── ChainMineClient.java        ClientInitializer
│       ├── fabric/ChainMineFabricPlatform.java  平台实现
│       ├── mixin/                       tryBreakBlock 拦截
│       ├── network/                      payload（HUD 计数）
│       └── client/                      ModMenu 配置屏（暂注释，见 TODO）
└── neoforge/                        ← NeoForge 模块（neogradle + mojmap）
    └── src/main/java/com/chainmine/neoforge/
        ├── ChainMineNeoForge.java         @Mod 入口 + BlockEvent.BreakEvent + 命令
        └── ChainMineNeoForgePlatform.java 平台实现
```

## 二、构建体系（踩坑后定案）

| 组件 | 版本 | 备注 |
| --- | --- | --- |
| architectury-plugin | `me.shedaniel:architectury-plugin:3.5.169` | **group 是 me.shedaniel**（不是 dev.architectury） |
| architectury-loom | `dev.architectury.loom:1.17.491` | loom fork，与 fabric-loom 1.17 同步 |
| 构建 DSL | common: `architectury { common("fabric","neoforge") }` | 平台模块: `fabric()` / `neoForge()` |
| buildscript | 必须显式列仓库 | fabricmc + architectury + forge + neoforged + mavenCentral |

### 关键决策：fabric 模块用 srcDir 共享 common 源码

```
fabric/build.gradle:
sourceSets { main { java { srcDir project(':common').file('src/main/java') } } }
```

- 同一份 common 源码被 fabric（yarn）直接编译，remapJar 自动把 core 类 remap 进 fabric jar
- architectury 的 `namedElements` / `common` 配置在此环境未生效，srcDir 最稳
- neoforge 模块由 architectury 在构建时把 common 的 yarn 字节码 remap 到 mojmap

### 版本自适应（VersionCompat）

- **Fabric 专用**（intermediary 名反射）：`getWorld`/`getEntityWorld`（method_37908/method_51469）、新旧权限系统
- 使 1.21.11 编译的 jar 覆盖 **1.21.2~1.21.11**（4 锚点版本实测：1.21.2/1.21.5/1.21.9/1.21.11）
- NeoForge 不需要它（mojmap 环境直调 API）

## 三、构建命令

```bash
./gradlew :fabric:build        # fabric jar（沙盒已验证）
./gradlew :fabric:runServer    # 本地验证
./gradlew :neoforge:build      # neoforge jar（需本地环境，可访问 maven.neoforged.net）
```

## 四、TODO（本地环境）

1. **neoforge 构建验证**：settings.gradle 取消 `include 'neoforge'` 注释后 `:neoforge:build`
2. **mojmap 权限 API**：`ChainMineNeoForge.onRegisterCommands` 的 `hasPermission(2)` 需按 1.21.11 mojmap 权限系统核对
3. **NeoForge HUD 网络**：`ChainMineNeoForgePlatform.sendChainCount` 目前用系统消息兜底，可接 NeoForge payload
4. **ModMenu 集成**：fabric 模块的 `ChainMineModMenu` 已暂移（modmenu 20.0.1 与 loom 1.17 remap 冲突），恢复后需用适配版本
5. **1.21.1 线**：main 分支仍是单模块（v1.1.0），如需同步多模块结构可后续处理
