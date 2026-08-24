# ChainMine 测试指南

本指南覆盖 ChainMine 的四层测试：自动化单元测试、游戏内手动测试、服务端测试、兼容性测试。
按顺序执行，每层通过后再进入下一层。

---

## 1. 前置条件

| 依赖 | 版本 |
| --- | --- |
| JDK | 21（构建与运行必需） |
| 游戏 | Minecraft 1.21.1（客户端/服务端） |
| 加载器 | Fabric Loader 0.16.10+ |
| 可选 | Fabric API 0.116.15+1.21.1、Mod Menu 11.x |

首次构建（下载依赖）：
```bash
./gradlew build
```

---

## 2. 自动化单元测试（无需启动游戏）

### 2.1 命令

```bash
./gradlew test
# 或仅跑 BFS 测试
./gradlew test --tests "com.chainmine.util.VeinScannerTest"
```

### 2.2 断言清单（9 个用例）

| 用例 | 验证点 |
| --- | --- |
| `singleBlock_noChain` | 孤立方块只返回自身，不连锁 |
| `linearChain_threeBlocks` | 线性三连，返回顺序 = BFS 顺序 |
| `sixDirection_includesVertical` | 上下前后左右六向全扩散（含 Y 轴） |
| `maxBlocks_capsExpansion` | 10 连限制 4 → 恰好返回 4 个，不越界 |
| `differentBlockType_notChained` | 石头-泥土-石头：类型严格匹配，中间隔断不连通 |
| `bfsOrder_correct` | 第一层邻居先于第二层深层节点 |
| `maxBlocksNonPositive_returnsEmpty` | maxBlocks<=0 返回空（防御） |
| `airTarget_returnsEmpty` | 目标为空气返回空（防御） |
| `diagonal_isNotConnected` | 对角相邻（x+1,z+1）不算连通 |

### 2.3 测试报告

- 汇总页：`build/reports/tests/test/index.html`（浏览器打开）
- 原始 XML：`build/test-results/test/TEST-com.chainmine.util.VeinScannerTest.xml`

> 技术说明：单测通过 `BlockProvider` 函数式接口注入内存 Map 虚拟世界，
> 无需启动 Minecraft。初始化写法 `SharedConstants.createGameVersion() + Bootstrap.initialize()`
> 由 fabric-loader-junit 提供 access widener 支持。

---

## 3. 游戏内手动测试（runClient）

### 3.1 启动

```bash
./gradlew runClient
```

进游戏后创建**创造模式**世界（便于快速摆方块）。测试用默认配置即可。

### 3.2 功能用例矩阵

| # | 场景 | 操作 | 预期结果 |
| --- | --- | --- | --- |
| 1 | 潜行触发连锁 | 潜行挖 3×3 矿石堆 | 全部连挖，一次挥镐全碎 |
| 2 | 不潜行不触发 | 不潜行挖矿石 | 只挖 1 个，不连锁 |
| 3 | 连锁上限 | 摆 10×10 方块堆，潜行挖 | 恰好连锁 64 个（默认上限），不卡顿 |
| 4 | 工具白名单 | 徒手/剑/盾牌潜行挖矿 | 不连锁 |
| 5 | 工具白名单 | 镐挖矿石、斧挖原木、锹挖泥土、锄挖下界疣 | 各自连锁对应方块 |
| 5b | 正确工具检查 | 用斧头（非镐）潜行挖石头 | **连锁**（requireCorrectTool 默认关闭） |
| 5c | 工具保护 | 用剩 2 点耐久的镐连锁挖 64 块石头 | 连锁在耐久耗尽前停止，**镐不爆** |
| 5d | 分批破坏 | 连锁挖 64 块石头（观察服务端 TPS） | 每 tick 只破坏 8 块（blocksPerTick 默认 8），无瞬时卡顿 |
| 5e | 空手触发 | 空手潜行挖泥土/沙砾 | **连锁**（allowEmptyHand 默认开） |
| 5f | 挖掘等级门槛 | 空手/石镐潜行挖钻石矿 | **不连锁**（canHarvest 硬门槛：钻石矿需铁镐+） |
| 5g | 挖掘等级门槛 | 铁镐潜行挖钻石矿 | 连锁 |
| 5h | 规则形状 | 潜行挖 3×3×3 矿石立方体的一角 | **恰好挖出 3×3×3 规则立方体**（scanMode=CUBE 默认），不沿连通扩散 |
| 6 | 类型严格匹配 | 石头堆里夹一块泥土 | 只连锁石头，泥土留在原地 |
| 7 | 六向扩散 | 悬空方块下方也摆同类型 | 上下左右前后全连锁 |
| 8 | 对角不连 | 只有对角接触的方块 | 不连锁（非六向连通） |
| 9 | 掉落物 | 连锁挖煤矿石 | 每个方块掉落 + 经验球正常 |
| 10 | 耐久消耗 | 用未附魔铁镐连锁挖 20 块 | 耐久下降 20（含首个） |
| 11 | Unbreaking | 用 Unbreaking III 镐连锁挖 | 耐久消耗按附魔概率减免 |
| 12 | 音效粒子 | 连锁时观察 | 每个方块有碎裂粒子 + 低音量碎裂音效（0.5 音量） |
| 13 | HUD 提示 | 连锁后看屏幕左上角 | 3 秒内显示 "ChainMine: +N blocks" |
| 14 | 黑名单 | 摆 spawner（黑名单默认含 bedrock/spawner） | 连锁跳过黑名单方块 |
| 15 | 经验 | 连锁挖钻石矿 | 经验正常结算 |

### 3.3 配置热重载

```text
1. 游戏内执行 /chainmine status        # 查看当前配置（需 OP/创造权限）
2. 修改 config/chainmine.json 例如 maxBlocks=8
3. 执行 /chainmine reload
4. 再挖 10×10 方块堆                   # 应只连锁 8 个
```

### 3.4 红宝石验证物品

```text
/give @s chainmine:ruby               # 物品栏出现"红宝石"（测试物品/标志物）
```

---

## 4. 服务端测试（无 GUI 环境）

无头/CI 环境无法启动客户端，改用专用服务器验证 mod 加载不崩溃：

```bash
# 首次需同意 EULA（生成 run/eula.txt 后改为 true，或预置 echo "eula=true" > run/eula.txt）
./gradlew runServer
```

**通过标准**（控制台日志）：
- `Loading Minecraft 1.21.1 with Fabric Loader 0.16.10`
- `- chainmine-core 1.1.0`
- `[ChainMine] Created default config at .\config\chainmine.json`
- `[ChainMine] initialized. Ruby registered: chainmine:ruby`
- `Done (x.xs)!` —— 完整启动，无 Exception/CRASH

> 已在本项目沙盒验证通过（Done 6.0s）。连接多人服务器后，客户端执行
> `/chainmine reload` 需 OP 权限（权限等级 2）。

---

## 5. 边界与防御测试（重要）

| 场景 | 预期 |
| --- | --- |
| 挖掘瞬间方块被替换（红石/其他玩家） | 连锁跳过类型不符的方块，不崩 |
| 连锁中途工具耐久耗尽 | 后续方块不再连锁（工具为空时 `isToolAllowed` 返回 false），不崩 |
| maxBlocks 配置为 0 / 负数 / 10000 | 自动钳制到 [1, 256] |
| 配置文件被手改坏（非法 JSON） | reload 失败并保留旧配置，游戏不崩 |
| 连锁非常深的矿脉（如 100+） | 迭代 BFS，无栈溢出，最多 256 个 |
| 与 FTB Ultimine 同时安装 | 互不干扰：ChainMine 只在自己触发条件下补刀，不劫持对方事件 |

---

## 6. 常见问题排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Not bootstrapped` 测试异常 | 单测未初始化 Minecraft 注册表 | 确认测试类 static 块：`SharedConstants.createGameVersion(); Bootstrap.initialize();` |
| `IllegalAccessError: setRegistryKey` | yarn 映射跨包访问 | 确认 `testImplementation "net.fabricmc:fabric-loader-junit:<loader版本>"` |
| 编译报 `postMine` 参数错 | yarn 顺序是 `(World, BlockState, BlockPos, PlayerEntity)` | 按此顺序调用 |
| 编译报 HUD 回调签名错 | 第二参数是 `RenderTickCounter` 非 float | `(DrawContext, RenderTickCounter)` |
| runClient 启动黑屏/闪退 | 常见于未装 Fabric API | 确认 mods 目录含 `fabric-api-0.116.15+1.21.1.jar` |
| 连锁不触发 | 未潜行 / 工具不在白名单 / 方块在黑名单 | `/chainmine status` 核对配置 |
