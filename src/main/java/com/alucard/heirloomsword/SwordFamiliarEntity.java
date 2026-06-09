package com.alucard.heirloomsword;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.*;

public class SwordFamiliarEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);

    private static final double HOVER_RADIUS = 1.5;
    private static final double COLLISION_SPHERE_RADIUS = 0.4;
    private static final double MAX_LAG_DISTANCE = 3.0;
    private static final double MOB_AWARENESS_RADIUS = 16.0;

    private static final double SPRING_STIFFNESS = 0.8;
    private static final double SPRING_DAMPING = 0.85;
    private static final double VERTICAL_SMOOTHING = 0.05;

    private static final double LAUNCH_SPEED_NORMAL = 1.6;
    private static final double LAUNCH_SPEED_CHARGED = 3.2;
    private static final double RETURN_SPEED = 1.8;
    private static final double MAX_LAUNCH_RANGE = 48.0;
    private static final double PICKUP_RANGE = 1.5;
    private static final int STUCK_TIMEOUT_TICKS = 60; // 3 seconds

    private static final float LAUNCH_DAMAGE_NORMAL = 16.0f;
    private static final float LAUNCH_DAMAGE_CHARGED = 32.0f;
    private static final float RETURN_DAMAGE = 8.0f;

    private Vec3 velocity = Vec3.ZERO;
    private Vec3 targetPosition = Vec3.ZERO;
    private double smoothedAnchorY = Double.NaN;
    private int currentCandidateIndex = 0;

    // LAUNCHING state fields
    private Vec3 launchDirection = Vec3.ZERO;
    private Vec3 launchOrigin = Vec3.ZERO;
    private boolean chargedLaunch = false;
    private final Set<Integer> outboundHitSet = new HashSet<>();

    // STUCK state fields
    private int stuckTimer = 0;

    // RETURNING state fields
    private final Set<Integer> returnHitSet = new HashSet<>();

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
        builder.define(DATA_STATE, FamiliarState.HOVERING.getId());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(DATA_OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        if (tag.contains("FamiliarState")) {
            setState(FamiliarState.fromId(tag.getInt("FamiliarState")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getOwnerUUID().ifPresent(uuid -> tag.putUUID("OwnerUUID", uuid));
        tag.putInt("FamiliarState", getState().getId());
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

    public FamiliarState getState() {
        return FamiliarState.fromId(this.entityData.get(DATA_STATE));
    }

    public void setState(FamiliarState state) {
        this.entityData.set(DATA_STATE, state.getId());
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

        switch (getState()) {
            case HOVERING -> tickHovering(owner);
            case LAUNCHING -> tickLaunching(owner);
            case STUCK -> tickStuck(owner);
            case RETURNING -> tickReturning(owner);
        }
    }

    private void clientTick() {
        Player owner = getOwner();
        if (owner == null) return;

        switch (getState()) {
            case HOVERING -> tickHovering(owner);
            case LAUNCHING -> tickLaunchingClient();
            case STUCK -> {} // No client tick needed for stuck
            case RETURNING -> tickReturningClient(owner);
        }
    }

    // === HOVERING ===

    private void tickHovering(Player owner) {
        updateTargetPosition(owner);
        applySpringPhysics();
        updateMobAwareness(owner);
    }

    // === LAUNCHING ===

    public void launch(Vec3 direction, boolean charged) {
        this.launchDirection = direction.normalize();
        this.launchOrigin = this.position();
        this.chargedLaunch = charged;
        this.outboundHitSet.clear();
        setState(FamiliarState.LAUNCHING);
    }

    private void tickLaunching(Player owner) {
        double speed = chargedLaunch ? LAUNCH_SPEED_CHARGED : LAUNCH_SPEED_NORMAL;
        Vec3 movement = launchDirection.scale(speed);
        Vec3 currentPos = this.position();
        Vec3 nextPos = currentPos.add(movement);

        // Check block collision along the path
        BlockHitResult blockHit = this.level().clip(new ClipContext(
                currentPos, nextPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            // Embed in block face
            this.setPos(blockHit.getLocation().subtract(launchDirection.scale(0.1)));
            enterStuck();
            return;
        }

        // Move the sword
        this.setPos(nextPos);

        // Check max range
        if (currentPos.distanceTo(launchOrigin) >= MAX_LAUNCH_RANGE) {
            enterReturning();
            return;
        }

        // Damage entities along the path
        damageEntitiesInPath(currentPos, nextPos, outboundHitSet,
                chargedLaunch ? LAUNCH_DAMAGE_CHARGED : LAUNCH_DAMAGE_NORMAL, owner);
    }

    private void tickLaunchingClient() {
        double speed = chargedLaunch ? LAUNCH_SPEED_CHARGED : LAUNCH_SPEED_NORMAL;
        Vec3 movement = launchDirection.scale(speed);
        this.setPos(this.position().add(movement));
    }

    // === STUCK ===

    private void enterStuck() {
        setState(FamiliarState.STUCK);
        stuckTimer = 0;
    }

    private void tickStuck(Player owner) {
        stuckTimer++;
        if (stuckTimer >= STUCK_TIMEOUT_TICKS) {
            enterReturning();
        }
    }

    // === RETURNING ===

    private void enterReturning() {
        returnHitSet.clear();
        setState(FamiliarState.RETURNING);
    }

    public void recall() {
        if (getState() == FamiliarState.LAUNCHING || getState() == FamiliarState.STUCK) {
            enterReturning();
        }
    }

    private void tickReturning(Player owner) {
        Vec3 currentPos = this.position();
        Vec3 ownerPos = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        Vec3 toOwner = ownerPos.subtract(currentPos);
        double distance = toOwner.length();

        if (distance <= PICKUP_RANGE) {
            // Arrived at player — return to hovering
            setState(FamiliarState.HOVERING);
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            Vec3 hoverPos = computeCandidatePosition(owner, 0);
            this.setPos(hoverPos);
            this.targetPosition = hoverPos;
            return;
        }

        Vec3 direction = toOwner.normalize();
        Vec3 movement = direction.scale(RETURN_SPEED);
        Vec3 nextPos = currentPos.add(movement);

        // Phases through blocks during return
        this.setPos(nextPos);

        // Damage entities on return path
        damageEntitiesInPath(currentPos, nextPos, returnHitSet, RETURN_DAMAGE, owner);
    }

    private void tickReturningClient(Player owner) {
        Vec3 currentPos = this.position();
        Vec3 ownerPos = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        Vec3 toOwner = ownerPos.subtract(currentPos);
        double distance = toOwner.length();

        if (distance <= PICKUP_RANGE) {
            return;
        }

        Vec3 direction = toOwner.normalize();
        Vec3 movement = direction.scale(RETURN_SPEED);
        this.setPos(this.position().add(movement));
    }

    // === COMBAT ===

    private void damageEntitiesInPath(Vec3 from, Vec3 to, Set<Integer> hitSet, float damage, Player owner) {
        AABB sweepBox = new AABB(
                Math.min(from.x, to.x) - 0.5, Math.min(from.y, to.y) - 0.5, Math.min(from.z, to.z) - 0.5,
                Math.max(from.x, to.x) + 0.5, Math.max(from.y, to.y) + 0.5, Math.max(from.z, to.z) + 0.5
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e.isAlive() && e != owner && !hitSet.contains(e.getId()));

        DamageSource source = this.level().damageSources().playerAttack(owner);
        for (LivingEntity entity : entities) {
            hitSet.add(entity.getId());
            entity.hurt(source, damage);
        }
    }

    // === HOVERING MECHANICS (existing) ===

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
            case 0 -> anchor.add(rightX * HOVER_RADIUS, 0, rightZ * HOVER_RADIUS);
            case 1 -> anchor.add(-rightX * HOVER_RADIUS, 0, -rightZ * HOVER_RADIUS);
            case 2 -> anchor.add(-forwardX * HOVER_RADIUS, 0, -forwardZ * HOVER_RADIUS);
            case 3 -> anchor.add(0, HOVER_RADIUS, 0);
            case 4 -> anchor.add(forwardX * HOVER_RADIUS, 0, forwardZ * HOVER_RADIUS);
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

        double lerpFactor = SPRING_STIFFNESS;
        if (distance > MAX_LAG_DISTANCE) {
            lerpFactor = Math.min(1.0, SPRING_STIFFNESS + (distance - MAX_LAG_DISTANCE) * 0.3);
        }

        velocity = displacement.scale(lerpFactor);
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

    @Nullable
    public static SwordFamiliarEntity findForOwner(ServerLevel level, UUID ownerUUID) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SwordFamiliarEntity familiar
                    && familiar.getOwnerUUID().map(ownerUUID::equals).orElse(false)) {
                return familiar;
            }
        }
        return null;
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
