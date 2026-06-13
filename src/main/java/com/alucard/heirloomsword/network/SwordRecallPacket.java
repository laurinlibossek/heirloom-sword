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

public record SwordRecallPacket() implements CustomPacketPayload {
    public static final Type<SwordRecallPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_recall"));

    public static final StreamCodec<ByteBuf, SwordRecallPacket> STREAM_CODEC =
            StreamCodec.unit(new SwordRecallPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordRecallPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            if (ManaService.isLockedOut(player)) return; // inputs locked during depletion punishment

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;

            // R only works from LAUNCHING or STUCK
            FamiliarState state = familiar.getState();
            if (state != FamiliarState.LAUNCHING && state != FamiliarState.STUCK) return;

            familiar.recall();
        });
    }
}
