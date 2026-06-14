package com.alucard.heirloomsword;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Normal-mode warp-next-to-target. Server-authoritative: validates state / cooldown / mana,
 * raycasts the player's view for a living (non-player) target, finds a valid standing spot
 * beside it, and teleports the player there facing the target. No invincibility.
 */
public final class WarpHandler {
    private WarpHandler() {}

    public static final double WARP_RANGE = 20.0;       // [TUNE] eye-raycast distance (blocks)
    public static final int WARP_COOLDOWN_TICKS = 100;  // [TUNE] 5 s

    public static void tryWarp(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof HeirloomSwordItem)) return;
        if (HeirloomSwordItem.isFlying(held)) return; // normal mode only

        if (player.getData(ManaAttachments.WARP_COOLDOWN.get()) > 0) {
            SwordSounds.playDenied(player);
            return;
        }
        if (!ManaService.hasAtLeast(player, ManaService.WARP_COST)) {
            SwordSounds.playDenied(player);
            return;
        }

        ServerLevel level = player.serverLevel();
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                e -> e instanceof LivingEntity && !(e instanceof Player) && e.isAlive(), WARP_RANGE);
        if (hit.getType() != HitResult.Type.ENTITY) {
            SwordSounds.playDenied(player);
            return;
        }
        Entity target = ((EntityHitResult) hit).getEntity();

        Vec3 dest = findWarpSpot(level, player, target);
        if (dest == null) {
            SwordSounds.playDenied(player);
            return;
        }

        // Origin burst, teleport with facing, arrival burst.
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.3, 0.5, 0.3, 0.1);

        float[] rot = lookAtAngles(player, dest, target);
        player.teleportTo(level, dest.x, dest.y, dest.z, rot[0], rot[1]);

        level.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, BlockPos.containing(dest), SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS, 0.7f, 1.3f);

        ManaService.spend(player, ManaService.WARP_COST);
        player.setData(ManaAttachments.WARP_COOLDOWN.get(), WARP_COOLDOWN_TICKS);
    }

    /** First valid standing spot beside the target ("next to", with half-step-back fallbacks). */
    private static Vec3 findWarpSpot(ServerLevel level, ServerPlayer player, Entity target) {
        Vec3 pPos = player.position();
        Vec3 tPos = target.position();
        double dx = tPos.x - pPos.x;
        double dz = tPos.z - pPos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) { // player on top of target — fall back to look direction
            Vec3 look = player.getLookAngle();
            dx = look.x; dz = look.z;
            len = Math.max(1.0e-4, Math.sqrt(dx * dx + dz * dz));
        }
        double dirX = dx / len, dirZ = dz / len;       // player -> target (horizontal)
        double perpX = -dirZ, perpZ = dirX;             // perpendicular = the two sides
        double off = target.getBbWidth() / 2.0 + 0.6;   // [TUNE] stand beside, clear of the hitbox

        double[][] candidates = {
                { tPos.x + perpX * off,              tPos.z + perpZ * off },              // right
                { tPos.x - perpX * off,              tPos.z - perpZ * off },              // left
                { tPos.x + perpX * off - dirX * 0.5, tPos.z + perpZ * off - dirZ * 0.5 }, // right, half-step back
                { tPos.x - perpX * off - dirX * 0.5, tPos.z - perpZ * off - dirZ * 0.5 }, // left, half-step back
        };
        int[] yOffsets = { 0, -1, 1 };
        double baseY = target.getY();
        for (double[] c : candidates) {
            for (int yo : yOffsets) {
                double y = baseY + yo;
                if (isStandable(level, player, c[0], y, c[1])) {
                    return new Vec3(c[0], y, c[1]);
                }
            }
        }
        return null;
    }

    /** Player body fits (two air blocks of clearance) and there is solid ground underfoot. */
    private static boolean isStandable(ServerLevel level, ServerPlayer player, double x, double y, double z) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(x, y, z);
        if (!level.noCollision(player, box)) return false;
        BlockPos floor = BlockPos.containing(x, y - 0.1, z);
        return !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
    }

    /** Yaw/pitch from the destination eye position toward the target's eyes. */
    private static float[] lookAtAngles(ServerPlayer player, Vec3 dest, Entity target) {
        Vec3 eye = new Vec3(dest.x, dest.y + player.getEyeHeight(), dest.z);
        Vec3 t = target.getEyePosition();
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, horiz) * (180.0 / Math.PI))));
        return new float[] { yaw, pitch };
    }
}
