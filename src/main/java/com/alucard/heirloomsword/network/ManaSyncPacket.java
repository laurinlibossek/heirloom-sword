package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.ClientManaState;
import com.alucard.heirloomsword.HeirloomSwordMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: updates the local player's cached mana value and lockout timer. */
public record ManaSyncPacket(float amount, int lockout) implements CustomPacketPayload {
    public static final Type<ManaSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "mana_sync"));

    public static final StreamCodec<ByteBuf, ManaSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, ManaSyncPacket::amount,
                    ByteBufCodecs.VAR_INT, ManaSyncPacket::lockout,
                    ManaSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ManaSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientManaState.current = packet.amount();
            ClientManaState.lockoutTicks = packet.lockout();
        });
    }
}
