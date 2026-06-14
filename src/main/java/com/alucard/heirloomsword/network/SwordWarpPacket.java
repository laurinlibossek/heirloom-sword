package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.HeirloomSwordMod;
import com.alucard.heirloomsword.WarpHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: the player pressed the warp key (V) in normal mode. */
public record SwordWarpPacket() implements CustomPacketPayload {
    public static final Type<SwordWarpPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_warp"));

    public static final StreamCodec<ByteBuf, SwordWarpPacket> STREAM_CODEC =
            StreamCodec.unit(new SwordWarpPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordWarpPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarpHandler.tryWarp(player); // validates everything server-side
            }
        });
    }
}
