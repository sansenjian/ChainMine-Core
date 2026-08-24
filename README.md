# ChainMine Core 🔨

> 经典 Vein Miner 的精神续作 —— 潜行挖掘，连锁破矿。

潜行（Shift）挖掘一个方块时，以该方块为中心连锁破坏**规则立方体**（默认 3×3×3）内的**同类型**方块；
也可切换为六向连通扫描（BFS）。掉落物与经验正常生成，工具耐久正常消耗（含 Unbreaking 判定）。

- **MC 版本**: 1.21.1 · **加载器**: Fabric 0.16.10+ · **Java**: 21
- **Yarn Mappings**: 1.21.1+build.3 · **Fabric API**: 0.116.15+1.21.1
- **许可证**: [MIT](./LICENSE)

## 功能特性

| 特性 | 说明 |
| --- | --- |
| 连锁触发 | 潜行挖掘时触发（可配置 `requireSneak` 关闭） |
| 空手触发 | 空手也可连锁（`allowEmptyHand`，默认开，挖泥土/沙砾无需工具） |
| 规则形状 | **CUBE 模式**（默认）：3×3×3 规则立方体，挖石头形状整齐；可切换 CONNECTED 六向连通 |
| 挖掘等级门槛 | 钻石矿等需要对应等级工具才掉落的方块，空手/等级不足**不**连锁（`canHarvest` 硬门槛） |
| 工具要求 | 默认不要求正确工具（斧头也能连锁挖石头）；`requireCorrectTool` 可开启严格模式 |
| 方块匹配 | 严格按 `Block` 类型匹配，矿石/原木/泥土各自成组 |
| 范围限制 | 单次连锁最大 64 个方块（可配置，上限 256，防卡服） |
| 分批破坏 | 每 tick 破坏 8 块（可配置），挖 64 块石头不再瞬间压垮 TPS |
| 工具保护 | 连锁途中剩余耐久 ≤1 立即停止，绝不磨爆工具 |
| 服务端执行 | 所有破坏逻辑在服务端验证执行，客户端无法伪造连锁 |
| 耐久/经验 | 每个连锁方块正常扣耐久（含 Unbreaking 免伤）与掉落经验 |
| 粒子/音效 | 原版方块碎裂粒子 + 0.5 音量碎裂音效（`BLOCK_BROKEN` 世界事件） |
| HUD 提示 | 连锁后 3 秒内显示本次连锁数量 |
| 热重载 | `/chainmine reload` 即时生效，无需重启 |

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 0.16.10+（MC 1.21.1）
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)（1.21.1 版本）
3. 将 `chainmine-core-*.jar` 放入 `mods/` 文件夹

## 使用

潜行 + 挖掘方块即可连锁（空手或任意工具均可，受挖掘等级门槛约束）。
默认挖出 3×3×3 规则立方体；想按矿脉连通走，改配置 `scanMode: "CONNECTED"`。

测试物品（红宝石）可用 `/give @s chainmine:ruby` 获取。

## 配置

配置文件：`config/chainmine.json`（首次启动自动生成），修改后执行 `/chainmine reload` 热重载。

```jsonc
{
  "requireSneak": true,                 // 是否必须潜行才触发连锁
  "maxBlocks": 64,                      // 单次连锁最大方块数（1~256，自动钳制）
  "allowEmptyHand": true,               // 空手也能触发连锁
  "requireCorrectTool": false,          // 是否要求正确工具类型（镐挖石头；默认关闭）
  "durabilityProtection": true,         // 工具保护：剩余耐久 ≤1 停止连锁
  "blocksPerTick": 8,                   // 每 tick 破坏上限（0 = 单 tick 全破）
  "scanMode": "CUBE",                   // 扫描形状：CUBE=规则立方体 / CONNECTED=六向连通
  "scanSize": 3,                        // 立方体边长（3/5/7/9，仅 CUBE 模式生效）
  "toolWhitelist": [                    // 工具白名单（仅持工具时生效）
    "pickaxe",
    "axe",
    "shovel",
    "hoe"
  ],
  "blockBlacklist": [                   // 禁止连锁的方块（按注册表 ID）
    "minecraft:bedrock",
    "minecraft:spawner"
  ]
}
```

### 命令（OP 权限，等级 2）

| 命令 | 说明 |
| --- | --- |
| `/chainmine reload` | 热重载配置 |
| `/chainmine status` | 查看当前生效的配置摘要 |

## 兼容性

- **与 FTB Ultimine 等连锁类 mod 共存**：ChainMine Core 只拦截方块**实际被破坏**的
  `tryBreakBlock` 出口，不拦截挖掘进度事件，互不干扰。
- **反作弊友好**：所有连锁破坏均在服务端 `ServerWorld#breakBlock` 执行，客户端仅接收
  HUD 计数通知，无法伪造连锁结果。
- **性能安全**：扫描采用迭代算法 + 硬上限（默认 64）；破坏按 tick 分批（默认 8/tick），
  单次运算量有界，不产生服务端 spike。

## 构建

```bash
# 需要 JDK 21
./gradlew build          # 编译 + 打包（产出 build/libs/chainmine-core-1.1.0.jar）
./gradlew runClient      # 启动开发客户端
./gradlew runServer      # 启动开发服务端
./gradlew test           # 运行单元测试（13 个用例）
```

## 项目结构

```
src/main/java/com/chainmine/
├── ChainMine.java                       # 主入口：物品/命令/网络/破坏器注册
├── ChainMineClient.java                 # 客户端入口：HUD 提示
├── config/ChainMineConfig.java          # 手写 JSON 配置 + 热重载
├── network/ChainMineNetworking.java     # S2C 网络同步（连锁计数）
├── network/ChainMineClientState.java    # 客户端 HUD 状态
├── util/VeinScanner.java                # 扫描算法：CUBE 规则立方体 + CONNECTED BFS（均无递归）
├── util/ChainMineBreaker.java           # 分 tick 破坏执行器（性能保护 + 工具保护）
├── mixin/ServerPlayerInteractionManagerMixin.java  # 挖掘拦截（tryBreakBlock）
└── client/ChainMineModMenu.java         # Mod Menu 集成（配置按钮）
```

## License

[MIT](./LICENSE) © 2026 ChainMine Dev
