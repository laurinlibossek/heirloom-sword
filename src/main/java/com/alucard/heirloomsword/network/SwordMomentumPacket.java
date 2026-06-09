package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwordMomentumPacket(float yawDelta, float pitchDelta) implements CustomPacketPayload {
    public static final Type<SwordMomentumPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_momentum"));

    public static final StreamCodec<FriendlyByteBuf, SwordMomentumPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SwordMomentumPacket decode(FriendlyByteBuf buf) {
                    float yawDelta = buf.readFloat();
                    float pitchDelta = buf.readFloat();
                    return new SwordMomentumPacket(yawDelta, pitchDelta);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SwordMomentumPacket packet) {
                    buf.writeFloat(packet.yawDelta);
                    buf.writeFloat(packet.pitchDelta);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordMomentumPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;

            if (familiar.getState() != FamiliarState.SWEEPING_HOLD) return;

            familiar.applySweepMomentum(packet.yawDelta, packet.pitchDelta);
        });
    }
}
