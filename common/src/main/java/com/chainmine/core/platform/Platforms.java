package com.chainmine.core.platform;

/**
 * 平台实现持有者 —— 各加载器模块在入口类中调用
 * {@link #set(ChainMinePlatform)} 注册自己的实现。
 */
public final class Platforms {

    private static ChainMinePlatform instance;

    private Platforms() {
    }

    /** 当前生效的平台实现（加载器入口调用 set 后可用）。 */
    public static ChainMinePlatform get() {
        if (instance == null) {
            throw new IllegalStateException("ChainMinePlatform not initialized");
        }
        return instance;
    }

    /** 注册平台实现（仅加载器入口调用一次）。 */
    public static void set(ChainMinePlatform platform) {
        instance = platform;
    }
}
