package com.chainmine.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 服务端 → 客户端的网络同步。
 *
 * <p>说明：连锁破坏时，原版 {@code World#breakBlock} 内部会广播
 * {@code WorldEvents.BLOCK_BROKEN (2001)} 世界事件 —— 客户端自动播放方块碎裂粒子
 * 与 0.5 音量的碎裂音效，因此本类不需要再为粒子/音效单独发包；
 * 它只负责把"本次连锁数量 + 触发点"同步给客户端，用于 HUD 提示。
 */
public final class ChainMineNetworking {

    /** 连锁计数 payload 类型 ID（play → S2C） */
    public static final Identifier CHAIN_COUNT_ID = Identifier.of("chainmine", "chain_count");

    private ChainMineNetworking() {
    }

    /** 双端共同注册 payload 编解码（必须在服务端与客户端各执行一次）。 */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(ChainCountPayload.ID, ChainCountPayload.CODEC);
    }

    /** 服务端向指定玩家发送连锁计数。 */
    public static void sendChainCount(ServerPlayerEntity player, int count, BlockPos origin) {
        ServerPlayNetworking.send(player, new ChainCountPayload(count, origin));
    }

    /** 客户端注册接收器（仅在客户端入口调用）。 */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ChainCountPayload.ID, (payload, context) ->
                context.client().execute(() -> ChainMineClientState.onChain(payload.count(), payload.origin())));
    }

    // ------------------------------------------------------------------
    // Payload 定义
    // ------------------------------------------------------------------

    public record ChainCountPayload(int count, BlockPos origin) implements CustomPayload {
        public static final Id<ChainCountPayload> ID = new Id<>(CHAIN_COUNT_ID);

        public static final PacketCodec<RegistryByteBuf, ChainCountPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, ChainCountPayload::count,
                BlockPos.PACKET_CODEC, ChainCountPayload::origin,
                ChainCountPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
