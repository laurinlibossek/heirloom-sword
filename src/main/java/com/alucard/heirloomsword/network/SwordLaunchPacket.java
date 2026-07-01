package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwordLaunchPacket(Vec3 direction, boolean charged) implements CustomPacketPayload {
    public static final Type<SwordLaunchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_launch"));

    public static final StreamCodec<FriendlyByteBuf, SwordLaunchPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SwordLaunchPacket decode(FriendlyByteBuf buf) {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    boolean charged = buf.readBoolean();
                    return new SwordLaunchPacket(new Vec3(x, y, z), charged);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SwordLaunchPacket packet) {
                    buf.writeDouble(packet.direction.x);
                    buf.writeDouble(packet.direction.y);
                    buf.writeDouble(packet.direction.z);
                    buf.writeBoolean(packet.charged);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordLaunchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;

            FamiliarState state = familiar.getState();
            if (state == FamiliarState.CHARGING) {
                Vec3 dir = packet.direction.normalize();
                boolean charged = familiar.isChargeReady();
                if (!charged && !ManaService.trySpend(player, ManaService.launchCost())) return;
                familiar.launch(dir, charged);
            } else if (state == FamiliarState.HOVERING) {
                // Guard against a zero-direction vector (can arrive as a stale sweep-release
                // packet when the sweep was already ended server-side by mana exhaustion).
                if (packet.direction.lengthSqr() > 1e-6) {
                    if (!ManaService.trySpend(player, ManaService.launchCost())) return;
                    Vec3 dir = packet.direction.normalize();
                    familiar.launch(dir, false);
                }
            } else if (state == FamiliarState.SWEEPING_HOLD) {
                familiar.releaseSweep();
            }
        });
    }
}
