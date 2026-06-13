package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: abort an in-progress charge back to HOVERING (e.g. the player paused). */
public record SwordCancelChargePacket() implements CustomPacketPayload {
    public static final Type<SwordCancelChargePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_cancel_charge"));

    public static final StreamCodec<ByteBuf, SwordCancelChargePacket> STREAM_CODEC =
            StreamCodec.unit(new SwordCancelChargePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordCancelChargePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;

            if (familiar.getState() != FamiliarState.CHARGING) return;

            familiar.cancelCharge();
        });
    }
}
