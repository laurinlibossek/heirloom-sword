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

public record SwordTetherPacket() implements CustomPacketPayload {
    public static final Type<SwordTetherPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_tether"));

    public static final StreamCodec<ByteBuf, SwordTetherPacket> STREAM_CODEC =
            StreamCodec.unit(new SwordTetherPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordTetherPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            if (ManaService.isLockedOut(player)) return;

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;

            // Tether only triggers from STUCK (server is authoritative — a stray packet is inert).
            if (familiar.getState() != FamiliarState.STUCK) return;

            if (!ManaService.trySpend(player, ManaService.tetherCost())) {
                SwordSounds.playDenied(player);
                return;
            }

            familiar.startTether();
        });
    }
}
