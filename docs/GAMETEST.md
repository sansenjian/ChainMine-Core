# GameTest 集成测试 —— 已落地

> 状态：**已实现并验证**（fabric 1.21.11，`./gradlew :fabric:runGametest` → 3/3 passed）

## 用例

| 用例 | 验证内容 |
| --- | --- |
| `cubeModeBreaks27Blocks` | 3×3×3 石头 + CUBE 扫描 + ChainBreaker 分 tick 破坏 → 全部变 AIR |
| `maxBlocksHardLimit` | 5×5×5=125 个相连石头，maxBlocks=10 只扫 10 个 |

## 1.21.11 正确 API（调研+实测结论）

```
@net.fabricmc.fabric.api.gametest.v1.GameTest    （fabric 自研注解，替代 MC 的）
  structure 默认 "fabric-gametest-api-v1:empty"  ← 内置 8×8×8 空结构，无需 nbt 资源！
测试方法：public void xxx(TestContext ctx)  实例方法，以 ctx.complete() 结束
注册：fabric.mod.json 的 "fabric-gametest" entrypoint
运行：loom { runs { gametest { server(); vmArg "-Dfabric-api.gametest=1" } } }
```

关键 API（TestContext，yarn 1.21.11）：
- `setBlockState(BlockPos, Block)` / `expectBlock(Block, BlockPos)` / `getWorld()`
- `getAbsolutePos(BlockPos)` —— **相对坐标 → 世界坐标**（GameTest 的 BlockPos 是相对结构的！扫描/破坏必须转世界坐标）
- `createMockCreativeServerPlayerInWorld()` → ServerPlayerEntity（直接可用）
- `addFinalTask(Runnable)` / `complete()`（1.21.11 无 succeedWhen）

## 踩坑记录（重要）

1. **结构坐标 vs 世界坐标**：`setBlockState` 用相对坐标，`getWorld()` 是真实世界 —— BFS 扫不到方块。必须 `ctx.getAbsolutePos()` 转换。
2. **VersionCompat 双环境 bug（产品级修复）**：loom 开发环境（runGametest/runServer）的 MC 类是 **yarn 名**（getEntityWorld），发布环境是 **intermediary 名**（method_51469）。VersionCompat 只反射 intermediary 名 → **开发环境挖掘必崩**（`Cannot resolve Entity world getter`）。已修复：两组名都试（intermediary → yarn），权限系统类名同理。
3. **mock player 是匿名子类**（TestContext$2 extends ServerPlayerEntity）：直接 `getClass().getMethod()` 找不到 → VersionCompat 增加父类链搜索。

## 后续可扩展

- `durabilityProtection`：工具剩余 1 耐久时连锁停止（需 mock player 持工具）
- `requireSneak`：不潜行时不触发
- CI 集成：GitHub Actions 跑 `./gradlew :fabric:runGametest`（Ubuntu 需 xvfb 或确认服务端无需显示）
