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
    private static final String PERMISSION_LEVEL_ENUM = "net/minecraft/class_12094";            // PermissionLevel
    private static final String PERMISSION_LEVEL_GAMEMASTERS = "field_63198";                  // PermissionLevel.GAMEMASTERS
    private static final String PERMISSION_CLASS = "net/minecraft/class_12087";                // Permission
    private static final String PERMISSION_LEVEL_CLASS = "net/minecraft/class_12087$class_12089"; // Permission$Level

    private VersionCompat() {
    }

    /** 获取玩家所在服务端世界（兼容 getWorld / getEntityWorld 两代命名）。 */
    public static ServerWorld getServerWorld(ServerPlayerEntity player) {
        try {
            return (ServerWorld) player.getClass().getMethod(ENTITY_GET_ENTITY_WORLD).invoke(player);
        } catch (NoSuchMethodException e) {
            try {
                return (ServerWorld) player.getClass().getMethod(ENTITY_GET_WORLD).invoke(player);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException("Cannot resolve Entity world getter", e2);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot resolve Entity world getter", e);
        }
    }

    /**
     * 检查命令源是否拥有 OP 等级 2（兼容新旧两代权限系统）。
     *
     * @param source 命令源（ServerCommandSource）
     * @return true = 拥有等级 2 及以上权限
     */
    public static boolean hasOpLevel2(ServerCommandSource source) {
        try {
            // 旧权限（1.21.2~1.21.8 等）：hasPermissionLevel(int)
            Method m = source.getClass().getMethod(HAS_PERMISSION_LEVEL, int.class);
            return (Boolean) m.invoke(source, 2);
        } catch (NoSuchMethodException e) {
            // 新权限（1.21.9+）：getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS))
            return checkNewPermission(source);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean checkNewPermission(ServerCommandSource source) {
        try {
            Object permissions = source.getClass().getMethod(GET_PERMISSIONS).invoke(source);
            Class<?> permissionLevelEnum = Class.forName(PERMISSION_LEVEL_ENUM);
            Object gamemasters = permissionLevelEnum.getField(PERMISSION_LEVEL_GAMEMASTERS).get(null);
            Class<?> permissionLevel = Class.forName(PERMISSION_LEVEL_CLASS);
            Object permission = permissionLevel.getConstructor(permissionLevelEnum).newInstance(gamemasters);
            Method hasPermission = permissions.getClass()
                    .getMethod(HAS_PERMISSION, Class.forName(PERMISSION_CLASS));
            return (Boolean) hasPermission.invoke(permissions, permission);
        } catch (Exception e) {
            // 权限探测失败时保守放行（与 vanilla 命令默认行为一致：无 requires 限制）
            return true;
        }
    }
}
