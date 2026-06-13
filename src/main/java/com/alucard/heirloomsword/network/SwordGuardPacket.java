package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwordGuardPacket(boolean held) implements CustomPacketPayload {
    public static final Type<SwordGuardPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_guard"));

    public static final StreamCodec<ByteBuf, SwordGuardPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SwordGuardPacket::held,
                    SwordGuardPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordGuardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;

            if (packet.held()) {
                if (familiar.getGuardCooldown() > 0) return;
                if (!ManaService.hasAtLeast(player, ManaService.MIN_BLOCK)) {
                    SwordSounds.playDenied(player);
                    return;
                }
                switch (familiar.getState()) {
                    case HOVERING -> familiar.startBlocking();
                    case CHARGING -> familiar.cancelChargeIntoBlock();      // cancels charge, no launch
                    case SWEEPING_HOLD -> familiar.cancelSweepIntoBlock();  // arrests sweep momentum
                    default -> { } // invalid source state — silently discard (design doc §22)
                }
            } else {
                if (familiar.getState() != FamiliarState.BLOCKING) return;
                familiar.stopBlocking();
            }
        });
    }
}
