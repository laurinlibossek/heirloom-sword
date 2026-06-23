package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.BackSheathClientState;
import com.alucard.heirloomsword.HeirloomSwordMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server → client: a player's back-sheath display state changed (or an initial snapshot on login).
 * {@code blood} is the blade's blood level quantized to 0..20 (5% steps); clients divide by 20.
 */
public record BackSheathSyncPacket(UUID playerId, boolean wearing, byte blood) implements CustomPacketPayload {
    public static final Type<BackSheathSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "back_sheath_sync"));

    public static final StreamCodec<ByteBuf, BackSheathSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, BackSheathSyncPacket::playerId,
                    ByteBufCodecs.BOOL, BackSheathSyncPacket::wearing,
                    ByteBufCodecs.BYTE, BackSheathSyncPacket::blood,
                    BackSheathSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BackSheathSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                BackSheathClientState.set(packet.playerId(), packet.wearing(), packet.blood() / 20f));
    }
}
