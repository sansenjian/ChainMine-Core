package com.chainmine.core.platform;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.lang.reflect.Method;

/**
 * 版本感知适配层 —— 让同一份字节码在多个 MC 子版本上运行。
 *
 * <p><b>为什么需要反射：</b>Fabric 运行时所有类名/方法名都是 <i>intermediary</i> 名
 * （yarn 名只在编译期存在）。某些 API 在不同子版本间被 Mojang 重命名或移除：
 * <ul>
 *   <li>1.21.2~1.21.8：{@code Entity.getWorld()}（intermediary {@code method_37908}）</li>
 *   <li>1.21.9+：{@code Entity.getEntityWorld()}（intermediary {@code method_51469}）</li>
 *   <li>旧权限：{@code ServerCommandSource.hasPermissionLevel(int)}（{@code method_9259}）</li>
 *   <li>新权限：{@code getPermissions().hasPermission(new Permission.Level(...))}
 *       （intermediary {@code method_75037} / {@code class_12087} 等）</li>
 * </ul>
 *
 * <p><b>策略：</b>运行时按<b>方法存在性</b>探测（而非硬编码版本号）——
 * 先试新名，找不到再退回旧名。intermediary 名在各自区间内跨版本稳定，
 * 因此同一字节码可在 1.21.2~1.21.11 全系运行。
 */
public final class VersionCompat {

    // ---- intermediary 名（跨版本稳定，勿改） ----
    private static final String ENTITY_GET_ENTITY_WORLD = "method_51469";      // 1.21.9+
    private static final String ENTITY_GET_WORLD = "method_37908";             // 1.21.2~1.21.8
    private static final String HAS_PERMISSION_LEVEL = "method_9259";          // 旧权限
    private static final String GET_PERMISSIONS = "method_75037";              // 新权限 getter
    private static final String HAS_PERMISSION = "hasPermission";              // PermissionPredicate
    private static final String PERMISSION_LEVEL_ENUM = "net/minecraft/class_12094";            // PermissionLevel (intermediary)
    private static final String PERMISSION_LEVEL_GAMEMASTERS = "field_63198";                  // PermissionLevel.GAMEMASTERS
    private static final String PERMISSION_CLASS = "net/minecraft/class_12087";                // Permission (intermediary)
    private static final String PERMISSION_LEVEL_CLASS = "net/minecraft/class_12087$class_12089"; // Permission$Level (intermediary)

    // ---- 名字搜索顺序：intermediary（发布环境）→ yarn（loom 开发环境） ----
    // loom 的 runServer/runGametest 用 yarn 映射的 MC jar（getEntityWorld/getWorld），
    // 发布 jar 在玩家环境是 intermediary 名（method_xxx）——两组名都要试。
    private static final String[] ENTITY_WORLD_GETTERS = {
            ENTITY_GET_ENTITY_WORLD,   // intermediary: getEntityWorld (1.21.9+)
            "getEntityWorld",          // yarn 名（开发环境）
            ENTITY_GET_WORLD,          // intermediary: getWorld (≤1.21.8)
            "getWorld"                 // yarn 名（开发环境）
    };
    private static final String[] PERMISSION_LEVEL_NAMES = {
            HAS_PERMISSION_LEVEL,      // intermediary: hasPermissionLevel(int)
            "hasPermissionLevel"       // yarn 名（开发环境）
    };

    private VersionCompat() {
    }

    /** 获取玩家所在服务端世界（兼容 getWorld / getEntityWorld 两代命名 + 双环境）。 */
    public static ServerWorld getServerWorld(ServerPlayerEntity player) {
        // 沿名字列表 × 父类链逐级搜索（父类链兜底兼容 GameTest 的 mock player 匿名子类）
        for (String name : ENTITY_WORLD_GETTERS) {
            for (Class<?> c = player.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    return (ServerWorld) c.getMethod(name).invoke(player);
                } catch (NoSuchMethodException ignore) {
                    // 该类没有，继续向父类找
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot invoke Entity world getter", e);
                }
            }
        }
        throw new IllegalStateException("Cannot resolve Entity world getter");
    }

    /**
     * 检查命令源是否拥有 OP 等级 2（兼容新旧两代权限系统）。
     *
     * @param source 命令源（ServerCommandSource）
     * @return true = 拥有等级 2 及以上权限
     */
    public static boolean hasOpLevel2(ServerCommandSource source) {
        // 旧权限（intermediary + yarn 双名）
        for (String name : PERMISSION_LEVEL_NAMES) {
            try {
                Method m = source.getClass().getMethod(name, int.class);
                return (Boolean) m.invoke(source, 2);
            } catch (NoSuchMethodException ignore) {
                // 试下一个名字
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        // 新权限（1.21.9+）：getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS))
        return checkNewPermission(source);
    }

    private static boolean checkNewPermission(ServerCommandSource source) {
        try {
            // GET_PERMISSIONS 双名（intermediary + yarn）
            Object permissions = null;
            for (String name : new String[]{GET_PERMISSIONS, "getPermissions"}) {
                try {
                    permissions = source.getClass().getMethod(name).invoke(source);
                    break;
                } catch (NoSuchMethodException ignore) {
                    // 试下一个名字
                }
            }
            if (permissions == null) {
                return true;
            }
            // 类名双解析：intermediary（发布环境）→ yarn（loom 开发环境）
            Class<?> permissionLevelEnum = loadClass(
                    PERMISSION_LEVEL_ENUM, "net.minecraft.command.permission.PermissionLevel");
            Class<?> permissionLevel = loadClass(
                    PERMISSION_LEVEL_CLASS, "net.minecraft.command.permission.Permission$Level");
            Class<?> permissionCls = loadClass(
                    PERMISSION_CLASS, "net.minecraft.command.permission.Permission");
            if (permissionLevelEnum == null || permissionLevel == null || permissionCls == null) {
                return true;
            }
            Object gamemasters = permissionLevelEnum.getField(PERMISSION_LEVEL_GAMEMASTERS).get(null);
            Object permission = permissionLevel.getConstructor(permissionLevelEnum).newInstance(gamemasters);
            Method hasPermission = permissions.getClass()
                    .getMethod(HAS_PERMISSION, permissionCls);
            return (Boolean) hasPermission.invoke(permissions, permission);
        } catch (Exception e) {
            // 权限探测失败时保守放行（与 vanilla 命令默认行为一致：无 requires 限制）
            return true;
        }
    }

    /** 按 intermediary → yarn 顺序解析类名，都失败返回 null。 */
    private static Class<?> loadClass(String intermediaryName, String yarnName) {
        try {
            return Class.forName(intermediaryName);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(yarnName);
            } catch (ClassNotFoundException e2) {
                return null;
            }
        }
    }
}
