# ChainMine Core vs VeinMiner 2.0 —— 完整技术对比

> 基于对 `vein_miner-2.0.jar`（GPL-3.0，作者 quillphen）内部结构的**直接解剖**（0 个 Java 类、纯数据包实现）
> 与 ChainMine Core v1.1.0 / v1.2.0（MIT，Java 实现）的实测验证。
> 整理日期：2026-08-25

---

## 一、一句话定位

| | VeinMiner 2.0 | ChainMine Core |
| --- | --- | --- |
| 实现形态 | **纯数据包**（0 Java 类，48 function + 14 item_modifier + 2 predicate） | **Fabric Java mod**（10 个类 + 13 个单元测试） |
| 核心卖点 | 一个 jar 通吃 **4 加载器 × 8 代际** | 潜行触发 + **规则 CUBE 形状** + 耐久保护 + 分 tick 破坏 |
| 许可证 | GPL-3.0-only | MIT |

---

## 二、架构与实现方式

| 维度 | VeinMiner 2.0（数据包） | ChainMine Core（Java） |
| --- | --- | --- |
| 代码形态 | mcfunction 递归 + 宏 + storage + scoreboard | Mixin + BFS + 事件 |
| 单 jar 通吃 | ✅ 靠"无代码"（4 份加载器元数据 + data/ 数据包） | ❌ 不可能（API 断层实测） |
| 跨版本 | pack.mcmeta `57~107`（1.21.2~26.2） | 1.21.1（v1.1.0）/ 1.21.2~1.21.11（v1.2.0）分线 |
| 跨平台 | Fabric + Quilt + Forge + NeoForge | 仅 Fabric |
| 加载器依赖 | 仅资源加载（如 fabric-resource-loader-v0） | Fabric API 多模块 |

---

## 三、核心机制逐项对比

| 能力 | VeinMiner 2.0 | ChainMine Core | 判定 |
| --- | --- | --- | --- |
| **触发** | tick 轮询检测挖掘状态（0 advancement） | `tryBreakBlock` Mixin 精准事件 | ChainMine 更精准、零轮询开销 |
| **潜行触发** | ❌ 无法检测（默认常开） | ✅ `requireSneak`（默认开） | **ChainMine 独占** |
| **连锁形状** | 递归扩散（不规则，矿石 6 向 / 树 26 向） | CUBE 规则立方体（默认 3×3×3）+ CONNECTED BFS | **ChainMine 独占**（挖石头更整齐） |
| **方块匹配** | 宏 `$execute if block $(ns):$(id)` 精确匹配 | `Block` 类型严格匹配 | 等价 |
| **破坏+掉落** | `loot ... mine` 命令 + `setblock air` | `ServerWorld.breakBlock`（原版通道） | ChainMine 更正统（统计/方块实体/事件链完整） |
| **附魔/掉落** | ✅ loot 命令保掉落 | ✅ 原版 loot 表 + 经验 | 等价 |
| **数量上限** | scoreboard `vm.blocks >= max_blocks` | config `maxBlocks`（1~256 钳制） | 等价 |
| **耐久扣减** | ✅ item_modifier 逐工具扣 1 点（12 种工具各一） | ✅ `ItemStack.postMine`（含 Unbreaking 概率免伤） | **ChainMine 更准**（数据包无 Unbreaking 处理） |
| **耐久保护** | ✅ damage 满值检测 → 销毁工具 + 音效 | ✅ 剩余耐久 ≤1 停止连锁（不销毁） | ChainMine 更友好（工具保留） |
| **冷却** | ✅ scoreboard 递减 | ❌ 无 | **VeinMiner 有** |
| **挖掘等级门槛** | ❌ 无（空手也能连锁） | ✅ `PlayerEntity.canHarvest`（钻石矿需铁镐+） | **ChainMine 独占** |
| **工具分类** | 仅 pickaxe（矿石）/ tree（树木）两类 | 镐/斧/锹/锄白名单 + 空手模式 | ChainMine 更细 |
| **配置** | storage + scoreboard（无 GUI，改值繁琐） | JSON 文件 + `/chainmine-core reload` 热重载 + Mod Menu | **ChainMine 独占** |
| **HUD 计数** | ❌ 无（仅聊天欢迎/帮助） | ✅ S2C payload + 屏幕左上角 3 秒计数 | **ChainMine 独占** |
| **服务端权威** | ✅ 天然服务端 | ✅ 全服务端 `breakBlock` | 等价 |
| **性能** | 函数递归（每方块多条命令，大矿脉可能卡） | BFS 迭代 + 分 tick 破坏（默认 8/tick） | **ChainMine 更稳** |
| **反作弊友好** | 中等（tick 轮询 + loot 命令可被观察） | 高（全服务端原版通道） | ChainMine 更优 |

---

## 四、覆盖范围对比

| 覆盖维度 | VeinMiner 2.0 | ChainMine Core |
| --- | --- | --- |
| MC 版本 | 1.21.2 ~ 26.2（同 jar） | 1.21.1 + 1.21.2~1.21.11（两线） |
| 加载器 | Fabric / Quilt / Forge / NeoForge | Fabric |
| 发布形态 | 1 个 jar 全平台 | 每版本线 1 个 jar |

---

## 五、差异化卖点分析（ChainMine 独有的四个能力）

VeinMiner 2.0 数据包版**没有**的四个能力，恰好是 ChainMine 的全部差异化：

1. **潜行触发**（`requireSneak`）—— 数据包检测不到潜行态，无法实现
2. **规则 CUBE 形状**（`scanMode: CUBE`）—— 数据包只有递归扩散，形状不可控
3. **GUI 配置 + 热重载** —— 数据包只能改 storage，无 UI
4. **HUD 计数** —— 数据包无 UI 接口

> 结论：VeinMiner 2.0 用"放弃体验"换"覆盖广度"；ChainMine 用"限定平台"换"体验深度"。
> 两者是互补路线，不是替代关系。

---

## 六、对 ChainMine 的启示

1. **数据包能力比预想强**：耐久扣减/保护、冷却、上限、工具分类都能用 item_modifier + scoreboard + storage 实现。
2. **但数据包永远做不了**：潜行触发、HUD、规则形状、GUI 配置——这四项是 Java mod 的护城河。
3. **"单 jar 通吃"的前提是"没有代码"**：ChainMine 作为 Java mod 追求跨代际单 jar 没有意义（实测 API 断层），正确姿势是"版本线分 jar + Modrinth 版本条目合并显示"（已在 v1.1.0 + v1.2.0 落地）。
4. **未来若想要 NeoForge 用户群**：走 MultiLoader 重构（common + fabric + neoforge），而非数据包路线——保留全部卖点，付出 2-3 天工程成本。

---

## 附：VeinMiner 2.0 内部结构速查（解剖记录）

```
vein_miner-2.0.jar
├── fabric.mod.json          # Fabric 入口（仅依赖 fabric-resource-loader-v0，无 minecraft 版本声明）
├── quilt.mod.json           # Quilt 入口
├── META-INF/mods.toml       # Forge 入口
├── META-INF/neoforge.mods.toml  # NeoForge 入口
├── pack.mcmeta              # 数据包格式 57~107（1.21.2~26.2）
├── data/minecraft/tags/function/{load,tick}.json   # 常驻 tick 驱动
└── data/vm/
    ├── function/            # 48 个：execute_mine / mine_vein / try_mine / damage_tool / break_tool ...
    ├── item_modifier/       # 14 个：damage/{wooden,stone,iron,golden,diamond,netherite}_{pickaxe,axe}
    └── predicate/           # 2 个
```

核心链：`tick → check_tools → check_pickaxe/check_tree → (检测到挖掘) → execute_mine → loot 挖块 + setblock air → scoreboard 计数 → mine_vein 六向/26 向递归 → try_mine(宏匹配) → execute_mine...`
