# NeoForge 构建配方 —— 网络根因调研结论（2026-08-26）

## 一、"连接不上"的根因（已用实验确认）

| 实验 | 结果 |
| --- | --- |
| curl 直连 maven.neoforged.net | ❌ 失败（本机走代理，未直连） |
| Java 直连（默认，IPv6 优先） | ❌ `SocketException: Connection reset` |
| Java 直连 + 强制 IPv4 | ❌ 同样 Connection reset |
| **Java 走代理 127.0.0.1:7897** | ✅ **HTTP 200** |
| curl 走代理 127.0.0.1:7897 | ✅ HTTP 200（下载 installer 2.9MB 成功） |

**结论**：
1. **本机直连 maven.neoforged.net 被网络环境阻断**（Connection reset，IPv4/IPv6 均如此）——不是 IPv6 问题、不是沙盒问题（沙盒内外一致）
2. **必须走本地代理 `127.0.0.1:7897`**（HTTP 代理；curl 默认走它，所以 curl 一直"正常"）
3. **gradle 默认不读环境代理** → 一直失败。解法：命令行加
   `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897`
4. ⚠️ **该代理对 gradle 的大文件/长连接不稳**（fastutil 19MB 截断、installer 握手终止、clientMappings 下载失败）——curl 手动下载 + 塞 loom 缓存可绕过部分

## 二、NeoForge 模块正确构建配置（沙盒验证到 MCP step 4/10）

```
neoforge/gradle.properties:          ← 模块级！不能放根 gradle.properties（会污染 common/fabric）
    loom.platform = neoforge
```

```
neoforge/build.gradle:
plugins {
    id 'java'
    id 'dev.architectury.loom' version '1.17.491'   ← 纯 architectury-loom，不需要 neogradle！
}
loom { neoForge { } }
dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings loom.officialMojangMappings()
    neoForge "net.neoforged:neoforge:21.11.45"      ← loom 的 neoForge 配置（platform 开启后才有）
    implementation project(':common')                ← loom 自动 remap common 到 mojmap
}
```

**踩坑清单**（全部实测）：
| 尝试 | 结果 |
| --- | --- |
| neogradle userdev 插件 | 与 loom 冲突（runClient 重复）❌ |
| architectury-plugin 的 `neoForge()`/`forgeLike()` | 需要 loom 扩展/参数，纯 loom 不需要 ❌ |
| `forge` 依赖配置 + neoforge 坐标 | "Loom is not running on NeoForge" → 需要 `loom.platform=neoforge` ❌ |
| `loom.platform=neoforge` 放根 gradle.properties | 污染 common/fabric 模块（No 'neoForge' dependency）❌ |
| **模块级 gradle.properties + `neoForge` 配置** | ✅ 配置全通，MCP 10 步到 step 4 |

## 三、本地完整构建步骤（用户环境，网络正常）

```bash
# 网络正常环境（能直连 neoforged / mojang）：
./gradlew :neoforge:build
# 产物：neoforge/build/libs/chainmine-core-neoforge-<version>.jar

# 若直连被阻断（本机现状），走代理：
./gradlew :neoforge:build \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897
```

## 四、沙盒中还能做什么

- fabric 全链路已闭环（构建 + GameTest 3/3 + 跨版本 4 版本 + 发布 jar）
- neoforge 配置已定稿（上面配方），代码已就绪——只差网络正常的环境执行构建
- 若在沙盒续推：每遇到下载失败，curl 走代理手动下载 → 塞 `.gradle-1211/loom-cache/minecraftMaven/<group>/<artifact>/<version>/`（installer 已验证此法有效）
