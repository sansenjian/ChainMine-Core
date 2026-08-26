# GameTest 集成测试 —— 调研记录与实现指引

> 状态：**待调研**。fabric 1.21.11 的 GameTest API 与旧版不兼容，需要先按下面的调研结论实现，再本地验证。

## 为什么暂停（实测结论）

用旧 API（`net.minecraft.test.GameTest` + `FabricGameTest` 接口）写的用例在 1.21.11 编译失败：

| 旧写法 | 1.21.11 现状 |
| --- | --- |
| `@net.minecraft.test.GameTest` | **不存在**（MC 注解类被移除/替换） |
| `net.fabricmc.fabric.api.gametest.v1.FabricGameTest`（接口） | **不存在**（旧 fabric API 移除） |
| `FabricGameTest.EMPTY_STRUCTURE` | **不存在**（无内置空结构常量） |

## 1.21.11 的正确 API（javap 实测）

```
net.fabricmc.fabric.api.gametest.v1.GameTest  （注解，不是接口！）
  attributes: structure() / environment() / maxTicks() / setupTicks() /
              required() / rotation() / manualOnly() / maxAttempts() /
              requiredSuccesses() / skyAccess()
net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker
net.minecraft.test.TestContext                  （MC 侧上下文，存在）
```

**要点**：
1. 用 **fabric 的 `@GameTest` 注解**（fabric-gametest-api-v1），不用 MC 的
2. **必须提供 structure 资源**（`structure()` 指向 `data/<modid>/gametest/structures/<name>.nbt`）——没有 EMPTY_STRUCTURE 常量
3. 测试方法签名待确认（fabric 新版可能不再传 TestContext，见 CustomTestMethodInvoker / fabric 文档）

## 待办实现步骤

1. 调研 fabric 1.21.11 的 gametest 方法签名（fabric 官方 wiki / fabric-example-mod 的 gametest 分支）
2. 生成 3x3x3 空结构 nbt（用 MC 的 `data gen` 或直接找现成模板 nbt）
3. 按新 API 重写 `ChainMineGameTests`：
   - cubeModeBreaks27Blocks：3x3x3 石头 + CUBE 扫描 + ChainBreaker 分 tick 破坏 + 全变 AIR
   - maxBlocksHardLimit：CONNECTED 扫描上限
   - durabilityProtection：工具剩余 1 耐久停止
4. `./gradlew :fabric:runGametest` 本地验证（沙盒已确认 maven.neoforged.net 不可达，但 fabric 侧可跑）

## 为什么值得做

GameTest 在**真实服务端世界**验证破坏链路（方块真的变 AIR、耐久真的扣），比真实服务器冒烟测试更进一步——可自动化、可进 CI。当前"真实旧版本服务器实测"已覆盖启动级验证，GameTest 补的是**行为级**验证。

## 备选（当前已覆盖的行为验证）

| 手段 | 验证了什么 | 状态 |
| --- | --- | --- |
| common JUnit（19 用例） | BFS/CUBE 算法、配置解析/clamp | ✅ 已跑通 |
| 真实服务器实测（1.21.2/1.21.5/1.21.9/1.21.11） | 加载、mixin、命令、配置、跨版本 | ✅ 已跑通 |
| GameTest | 真实世界的方块破坏行为 | ⏳ 待调研实现 |
