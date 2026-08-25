package com.chainmine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.chainmine.ChainMine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ChainMine 配置（手写 JSON，无需 Cloth Config 依赖）。
 *
 * <p>配置文件位于 {@code config/chainmine.json}，支持 {@code /chainmine reload} 热重载。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code requireSneak} —— 是否必须潜行才触发连锁（默认 true）</li>
 *   <li>{@code maxBlocks} —— 单次连锁最大方块数（默认 64，范围 1~256，超界自动钳制）</li>
 *   <li>{@code toolWhitelist} —— 触发工具白名单（pickaxe/axe/shovel/hoe）</li>
 *   <li>{@code blockBlacklist} —— 禁止连锁的方块 ID 黑名单（默认 bedrock、spawner）</li>
 * </ul>
 */
public final class ChainMineConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_BLOCKS_LIMIT = 256;
    private static final int MIN_BLOCKS_LIMIT = 1;

    private static ChainMineConfig INSTANCE = new ChainMineConfig();

    // ---- 可配置字段（字段名即 JSON key） ----
    public boolean requireSneak = true;
    public int maxBlocks = 64;
    public List<String> toolWhitelist = new ArrayList<>(List.of("pickaxe", "axe", "shovel", "hoe"));
    public List<String> blockBlacklist = new ArrayList<>(
            List.of("minecraft:bedrock", "minecraft:spawner"));

    /** 空手也能触发连锁（默认开，挖泥土/沙砾无需工具） */
    public boolean allowEmptyHand = true;
    /** 是否要求工具对目标方块"合适"（如石头必须用镐）。默认关闭：斧头也能连锁挖石头。
     *  注意：无论此开关如何，"挖掘等级硬门槛"始终生效（钻石矿仍需对应等级工具才会掉落）。 */
    public boolean requireCorrectTool = false;
    /** 工具保护：连锁途中剩余耐久 ≤ 1 时立即停止，绝不磨爆工具 */
    public boolean durabilityProtection = true;
    /** 每 tick 最多破坏的方块数（0 = 单 tick 全破；8 参考 SgtVeinminer 默认） */
    public int blocksPerTick = 8;
    /** 扫描模式：CUBE=规则立方体（默认，3×3×3 形状，挖石头更整齐）；CONNECTED=六向连通 BFS */
    public String scanMode = "CUBE";
    /** 规则立方体边长（仅 scanMode=CUBE 时生效；3/5/7/9，自动取奇数并钳制到 [3,9]） */
    public int scanSize = 3;

    // ---- 运行时缓存（由 reload 重建，不落盘） ----
    private transient Set<Identifier> blacklistCache = new HashSet<>();

    private ChainMineConfig() {
    }

    public static ChainMineConfig get() {
        return INSTANCE;
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("chainmine-core.json");
    }

    /** 首次加载：文件不存在则写出默认配置；存在则读取。 */
    public static void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            save();
            ChainMine.LOGGER.info("[ChainMine] Created default config at {}", path);
        } else {
            reload();
        }
    }

    /** 热重载：重新读取磁盘文件并重建缓存。任何字段缺失/非法都会回退到默认值。 */
    public static void reload() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            load();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            ChainMineConfig loaded = GSON.fromJson(reader, ChainMineConfig.class);
            if (loaded == null) {
                throw new IOException("empty config file");
            }
            loaded.sanitize();
            INSTANCE = loaded;
            ChainMine.LOGGER.info("[ChainMine] Config reloaded from {}", path);
        } catch (Exception e) {
            ChainMine.LOGGER.error("[ChainMine] Failed to reload config, keeping current values", e);
        }
    }

    private static void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            ChainMine.LOGGER.error("[ChainMine] Failed to save default config", e);
        }
    }

    /** 字段合法性检查：钳制范围、过滤非法条目。 */
    private void sanitize() {
        this.maxBlocks = Math.max(MIN_BLOCKS_LIMIT, Math.min(MAX_BLOCKS_LIMIT, this.maxBlocks));
        // blocksPerTick: 0 = 单 tick 全破；1~64 之间钳制
        this.blocksPerTick = Math.max(0, Math.min(MAX_BLOCKS_LIMIT, this.blocksPerTick));
        // scanMode: 仅允许 CUBE / CONNECTED，非法回退 CUBE
        if (this.scanMode == null
                || !(this.scanMode.equalsIgnoreCase("CUBE") || this.scanMode.equalsIgnoreCase("CONNECTED"))) {
            this.scanMode = "CUBE";
        }
        // scanSize: 钳制 [3,9] 并强制取奇数
        this.scanSize = Math.max(3, Math.min(9, this.scanSize));
        this.scanSize |= 1;

        if (this.toolWhitelist == null) {
            this.toolWhitelist = new ArrayList<>(List.of("pickaxe", "axe", "shovel", "hoe"));
        }
        if (this.blockBlacklist == null) {
            this.blockBlacklist = new ArrayList<>();
        }

        this.blacklistCache = new HashSet<>();
        for (String id : this.blockBlacklist) {
            try {
                Identifier parsed = Identifier.of(id);
                // 提前验证方块存在性，无效条目直接丢弃并告警
                if (Registries.BLOCK.containsId(parsed)) {
                    this.blacklistCache.add(parsed);
                } else {
                    ChainMine.LOGGER.warn("[ChainMine] Unknown block in blacklist: {}", id);
                }
            } catch (Exception e) {
                ChainMine.LOGGER.warn("[ChainMine] Invalid block id in blacklist: {}", id);
            }
        }
    }

    /** 工具是否在白名单内（按物品类别判断）。
     *  1.21.2+ 移除了 PickaxeItem 等具体工具类，改用 ItemTags（minecraft:pickaxes 等）。 */
    public boolean isToolAllowed(Item item) {
        if (item == null || toolWhitelist == null) {
            return false;
        }
        for (String type : toolWhitelist) {
            TagKey<Item> tag = TOOL_TAGS.get(type);
            if (tag != null && item.getRegistryEntry().isIn(tag)) {
                return true;
            }
        }
        return false;
    }

    /** 工具类型 → 物品 tag（1.21.2+ 工具系统统一为 tag 判定）。 */
    private static final Map<String, TagKey<Item>> TOOL_TAGS = Map.of(
            "pickaxe", ItemTags.PICKAXES,
            "axe", ItemTags.AXES,
            "shovel", ItemTags.SHOVELS,
            "hoe", ItemTags.HOES
    );

    /** 方块是否在黑名单内（禁止连锁）。 */
    public boolean isBlockBlacklisted(Block block) {
        if (block == null) {
            return true;
        }
        return blacklistCache.contains(Registries.BLOCK.getId(block));
    }

    /** 状态摘要（/chainmine status 输出用）。 */
    public String describe() {
        return "requireSneak=" + requireSneak
                + ", maxBlocks=" + maxBlocks
                + ", tools=" + toolWhitelist
                + ", blacklist=" + blacklistCache;
    }
}
