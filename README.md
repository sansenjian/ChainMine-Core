# ChainMine 🔨

> 经典 Vein Miner 的精神续作 —— 潜行挖掘，连锁破矿。

手持镐/斧/锹/锄潜行（Shift）挖掘一个方块时，自动连锁破坏**六向连通**的**同类型**方块，
掉落物与经验正常生成，工具耐久正常消耗（含 Unbreaking 判定）。

- **MC 版本**: 1.21.1 · **加载器**: Fabric 0.16.10+ · **Java**: 21
- **Yarn Mappings**: 1.21.1+build.3 · **Fabric API**: 0.141.6+1.21.1
- **许可证**: [MIT](./LICENSE)

## 功能特性

| 特性 | 说明 |
| --- | --- |
| 连锁触发 | 潜行挖掘时触发（可配置 `requireSneak` 关闭） |
| 工具白名单 | 仅限镐、斧、锹、锄（可配置） |
| 方块匹配 | 严格按 `Block` 类型匹配，矿石/原木/泥土各自成组 |
| 范围限制 | 单次连锁最大 64 个方块（可配置，默认上限 256，防卡服） |
| 搜索算法 | 迭代 BFS，六向扩散，**无递归**（防栈溢出） |
| 服务端执行 | 所有破坏逻辑在服务端验证执行，客户端无法伪造连锁 |
| 耐久/经验 | 每个连锁方块正常扣耐久（含 Unbreaking 免伤）与掉落经验 |
| 粒子/音效 | 原版方块碎裂粒子 + 0.5 音量碎裂音效（`BLOCK_BROKEN` 世界事件） |
| HUD 提示 | 连锁后 3 秒内显示本次连锁数量 |
| 热重载 | `/chainmine reload` 即时生效，无需重启 |

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 0.16.10+（MC 1.21.1）
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)（任意 1.21.1 版本）
3. 将 `chainmine-*.jar` 放入 `mods/` 文件夹

## 使用

潜行 + 用镐/斧/锹/锄挖掘矿石、原木、泥土等任意方块，即可连锁破坏整条矿脉。

测试物品（红宝石）可用 `/give @s chainmine:ruby` 获取。

## 配置

配置文件：`config/chainmine.json`（首次启动自动生成），修改后执行 `/chainmine reload` 热重载。

```jsonc
{
  "requireSneak": true,                 // 是否必须潜行才触发连锁
  "maxBlocks": 64,                      // 单次连锁最大方块数（1~256，自动钳制）
  "toolWhitelist": [                    // 触发工具白名单
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

- **与 FTB Ultimine 等连锁类 mod 共存**：ChainMine 只拦截方块**实际被破坏**的
  `tryBreakBlock` 出口，不拦截挖掘进度事件，互不干扰；且要求玩家潜行 + 工具白名单，
  双重条件默认关闭，避免误触发其他 mod 的连锁。
- **反作弊友好**：所有连锁破坏均在服务端 `ServerWorld#breakBlock` 执行，客户端仅接收
  HUD 计数通知，无法伪造连锁结果。
- **性能安全**：BFS 采用迭代队列 + 硬上限（默认 64），单次破坏运算量有界。

## 构建

```bash
# 需要 JDK 21
./gradlew build          # 编译 + 打包（产出 build/libs/chainmine-1.0.0.jar）
./gradlew runClient      # 启动开发客户端
./gradlew runServer      # 启动开发服务端
./gradlew test           # 运行 BFS 单元测试
```

## 项目结构

```
src/main/java/com/chainmine/
├── ChainMine.java                       # 主入口：物品/命令/网络注册
├── ChainMineClient.java                 # 客户端入口：HUD 提示
├── config/ChainMineConfig.java          # 手写 JSON 配置 + 热重载
├── network/ChainMineNetworking.java     # S2C 网络同步（连锁计数）
├── network/ChainMineClientState.java    # 客户端 HUD 状态
├── util/VeinScanner.java                # 六向 BFS 扫描算法（无递归）
├── mixin/ServerPlayerInteractionManagerMixin.java  # 挖掘拦截（tryBreakBlock）
└── client/ChainMineModMenu.java         # Mod Menu 集成（配置按钮）
```

## License

[MIT](./LICENSE) © 2026 ChainMine Dev
