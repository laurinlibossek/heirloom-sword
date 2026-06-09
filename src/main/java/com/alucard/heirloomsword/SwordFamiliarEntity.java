package com.alucard.heirloomsword;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SwordFamiliarEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final double HOVER_RADIUS = 1.5;
    private static final double COLLISION_SPHERE_RADIUS = 0.4;
    private static final double MAX_LAG_DISTANCE = 3.0;
    private static final double MOB_AWARENESS_RADIUS = 16.0;

    private static final double SPRING_STIFFNESS = 0.8;
    private static final double SPRING_DAMPING = 0.85;
    private static final double VERTICAL_SMOOTHING = 0.05;

    private Vec3 velocity = Vec3.ZERO;
    private Vec3 targetPosition = Vec3.ZERO;
    private double smoothedAnchorY = Double.NaN;
    private int currentCandidateIndex = 0;

    @Nullable
    private Entity awarenessTarget = null;

    public SwordFamiliarEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public SwordFamiliarEntity(Level level, Player owner) {
        this(ModEntities.SWORD_FAMILIAR.get(), level);
        this.entityData.set(DATA_OWNER_UUID, Optional.of(owner.getUUID()));
        Vec3 spawnPos = computeCandidatePosition(owner, 0);
        this.setPos(spawnPos);
        this.targetPosition = spawnPos;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_UUID, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(DATA_OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getOwnerUUID().ifPresent(uuid -> tag.putUUID("OwnerUUID", uuid));
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(DATA_OWNER_UUID);
    }

    @Nullable
    public Player getOwner() {
        return getOwnerUUID()
                .map(uuid -> this.level().getPlayerByUUID(uuid))
                .orElse(null);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            clientTick();
            return;
        }

        serverTick();
    }

    private void serverTick() {
        Player owner = getOwner();
        if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            this.discard();
            return;
        }

        updateTargetPosition(owner);
        applySpringPhysics();
        updateMobAwareness(owner);
    }

    private void clientTick() {
        Player owner = getOwner();
        if (owner == null) return;

        updateTargetPosition(owner);
        applySpringPhysics();
        updateMobAwareness(owner);
    }

    private void updateTargetPosition(Player owner) {
        for (int i = 0; i < 5; i++) {
            int candidateIdx = (currentCandidateIndex + i) % 5;
            Vec3 candidate = computeCandidatePosition(owner, candidateIdx);
            if (!isPositionObstructed(candidate)) {
                if (candidateIdx != currentCandidateIndex) {
                    currentCandidateIndex = candidateIdx;
                }
                targetPosition = candidate;
                return;
            }
        }

        // All positions obstructed — exit flying mode
        if (!this.level().isClientSide()) {
            exitFlyingMode(owner);
        }
    }

    private Vec3 computeCandidatePosition(Player owner, int candidateIndex) {
        Vec3 anchor = getAnchorPoint(owner);
        float yaw = owner.getYRot();
        double rad = Math.toRadians(yaw);

        double rightX = -Math.cos(rad);
        double rightZ = -Math.sin(rad);
        double forwardX = -Math.sin(rad);
        double forwardZ = Math.cos(rad);

        return switch (candidateIndex) {
            case 0 -> anchor.add(rightX * HOVER_RADIUS, 0, rightZ * HOVER_RADIUS); // Right
            case 1 -> anchor.add(-rightX * HOVER_RADIUS, 0, -rightZ * HOVER_RADIUS); // Left
            case 2 -> anchor.add(-forwardX * HOVER_RADIUS, 0, -forwardZ * HOVER_RADIUS); // Behind
            case 3 -> anchor.add(0, HOVER_RADIUS, 0); // Above
            case 4 -> anchor.add(forwardX * HOVER_RADIUS, 0, forwardZ * HOVER_RADIUS); // Front
            default -> anchor;
        };
    }

    private Vec3 getAnchorPoint(Player owner) {
        double torsoY = owner.getY() + owner.getBbHeight() * 0.55;

        if (Double.isNaN(smoothedAnchorY)) {
            smoothedAnchorY = torsoY;
        } else {
            smoothedAnchorY += (torsoY - smoothedAnchorY) * VERTICAL_SMOOTHING;
        }

        return new Vec3(owner.getX(), smoothedAnchorY, owner.getZ());
    }

    private boolean isPositionObstructed(Vec3 pos) {
        AABB testBox = new AABB(
                pos.x - COLLISION_SPHERE_RADIUS, pos.y - COLLISION_SPHERE_RADIUS, pos.z - COLLISION_SPHERE_RADIUS,
                pos.x + COLLISION_SPHERE_RADIUS, pos.y + COLLISION_SPHERE_RADIUS, pos.z + COLLISION_SPHERE_RADIUS
        );

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int minX = (int) Math.floor(testBox.minX);
        int minY = (int) Math.floor(testBox.minY);
        int minZ = (int) Math.floor(testBox.minZ);
        int maxX = (int) Math.floor(testBox.maxX);
        int maxY = (int) Math.floor(testBox.maxY);
        int maxZ = (int) Math.floor(testBox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    VoxelShape shape = this.level().getBlockState(mutable).getCollisionShape(this.level(), mutable);
                    if (!shape.isEmpty()) {
                        AABB blockBounds = shape.bounds().move(mutable);
                        if (testBox.intersects(blockBounds)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void applySpringPhysics() {
        Vec3 currentPos = this.position();
        Vec3 displacement = targetPosition.subtract(currentPos);
        double distance = displacement.length();

        // Lerp-style follow: heavily damped, no oscillation
        double lerpFactor = SPRING_STIFFNESS;
        if (distance > MAX_LAG_DISTANCE) {
            // Urgently catch up when too far
            lerpFactor = Math.min(1.0, SPRING_STIFFNESS + (distance - MAX_LAG_DISTANCE) * 0.3);
        }

        velocity = displacement.scale(lerpFactor);

        // Apply damping to kill any residual wobble
        velocity = velocity.scale(1.0 - SPRING_DAMPING);

        double maxSpeed = 0.6;
        if (velocity.length() > maxSpeed) {
            velocity = velocity.normalize().scale(maxSpeed);
        }

        this.setPos(currentPos.add(velocity));
    }

    private void updateMobAwareness(Player owner) {
        if (this.tickCount % 5 != 0) return;

        AABB scanBox = new AABB(
                owner.getX() - MOB_AWARENESS_RADIUS,
                owner.getY() - MOB_AWARENESS_RADIUS,
                owner.getZ() - MOB_AWARENESS_RADIUS,
                owner.getX() + MOB_AWARENESS_RADIUS,
                owner.getY() + MOB_AWARENESS_RADIUS,
                owner.getZ() + MOB_AWARENESS_RADIUS
        );

        List<Monster> hostiles = this.level().getEntitiesOfClass(Monster.class, scanBox,
                mob -> mob.isAlive() && mob.distanceTo(owner) <= MOB_AWARENESS_RADIUS);

        if (hostiles.isEmpty()) {
            awarenessTarget = null;
        } else {
            hostiles.sort((a, b) -> Double.compare(a.distanceTo(owner), b.distanceTo(owner)));
            awarenessTarget = hostiles.get(0);
        }
    }

    @Nullable
    public Entity getAwarenessTarget() {
        return awarenessTarget;
    }

    private void exitFlyingMode(Player owner) {
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            var stack = owner.getInventory().getItem(i);
            if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                HeirloomSwordItem.setMode(stack, SwordMode.NORMAL);
                break;
            }
        }
        owner.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("msg.heirloomswordmod.sword_returns"), true);
        this.discard();
    }

    public static void despawnForOwner(ServerLevel level, UUID ownerUUID) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SwordFamiliarEntity familiar
                    && familiar.getOwnerUUID().map(ownerUUID::equals).orElse(false)) {
                familiar.discard();
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
}
