package com.alucard.heirloomsword;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.core.particles.ParticleTypes;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

public class SwordFamiliarEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GUARD_COOLDOWN =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_LAUNCH_DIR =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.VECTOR3);

    private static final double HOVER_RADIUS = 1.8; // [TUNE] was 1.5 — sword felt too close
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
    private static final int CHARGE_THRESHOLD_TICKS = 60; // 3 seconds for charged tier
    private static final ResourceLocation CHARGE_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "charge_slowdown");

    private static final float LAUNCH_DAMAGE_NORMAL = 16.0f;
    private static final float LAUNCH_DAMAGE_CHARGED = 32.0f;
    private static final float RETURN_DAMAGE = 8.0f;

    // SWEEPING constants
    private static final double SWEEP_HOLD_DISTANCE = 1.8;
    private static final float SWEEP_CONTACT_DAMAGE = 4.0f;
    private static final float SWEEP_RELEASE_DAMAGE = 8.0f;
    private static final int SWEEP_IFRAME_TICKS = 10;
    private static final double SWEEP_MOMENTUM_SCALE = 0.08;
    private static final double SWEEP_DAMPING = 0.72;
    private static final double SWEEP_SPRING_STRENGTH = 0.35;
    private static final double SWEEP_MAX_SPEED = 1.2;
    private static final double SWEEP_RELEASE_MAX_RADIUS = 12.0;
        private static final double SWEEP_RETURN_SPEED = 1.5;
    private static final float SWEEP_KNOCKBACK_STRENGTH = 0.6f;
    private int dyingTimer = 0;
    private static final int DYING_ANIMATION_TICKS = 10;

    private Vec3 velocity = Vec3.ZERO;
    private Vec3 targetPosition = Vec3.ZERO;
    private double smoothedAnchorY = Double.NaN;
    private int currentCandidateIndex = 0;
    private int preferredFreeTicks = 0;
    private static final int PREFERRED_RETURN_DELAY_TICKS = 10; // [TUNE]

    // LAUNCHING state fields
    private Vec3 launchDirection = Vec3.ZERO;
    private Vec3 launchOrigin = Vec3.ZERO;
    private boolean chargedLaunch = false;
    private final Set<Integer> outboundHitSet = new HashSet<>();

    // CHARGING state fields
    private int chargeTimer = 0;

    // STUCK state fields
    private int stuckTimer = 0;

    // RETURNING state fields
    private final Set<Integer> returnHitSet = new HashSet<>();
    private boolean recallPending = false;

    // SWEEPING state fields
    private Vec3 sweepVelocity = Vec3.ZERO;
    private boolean sweepReturning = false;
    private final Map<Integer, Integer> sweepIFrames = new HashMap<>();
    private final Set<Integer> sweepReleaseHitSet = new HashSet<>();
    private final Set<Integer> sweepReturnHitSet = new HashSet<>();

    @Nullable
    private Entity awarenessTarget = null;
    private boolean horizontal = false;
    private float horizontalProgress = 0.0f;
    private float horizontalProgressO = 0.0f;

    private static final EntityDimensions DIMENSIONS_VERTICAL = EntityDimensions.fixed(0.4f, 3.0f);
    // Horizontal: width/height passed to EntityDimensions are overridden by makeBoundingBox()
    private static final EntityDimensions DIMENSIONS_HORIZONTAL = EntityDimensions.fixed(3.0f, 0.4f);

    private static final double SWORD_HALF_LENGTH = 1.5;
    private static final double SWORD_HALF_THICKNESS = 0.2;

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
        builder.define(DATA_GUARD_COOLDOWN, 0);
        builder.define(DATA_LAUNCH_DIR, new Vector3f());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(DATA_OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        if (tag.contains("FamiliarState")) {
            setState(FamiliarState.fromId(tag.getInt("FamiliarState")));
        }
        if (tag.contains("guardCooldown")) {
            setGuardCooldown(tag.getInt("guardCooldown"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getOwnerUUID().ifPresent(uuid -> tag.putUUID("OwnerUUID", uuid));
        tag.putInt("FamiliarState", getState().getId());
        tag.putInt("guardCooldown", getGuardCooldown());
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

    public int getGuardCooldown() {
        return this.entityData.get(DATA_GUARD_COOLDOWN);
    }

    private void setGuardCooldown(int ticks) {
        this.entityData.set(DATA_GUARD_COOLDOWN, ticks);
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
            removeChargeSlowdown();
            this.discard();
            return;
        }

                switch (getState()) {
            case HOVERING -> tickHovering(owner);
            case CHARGING -> tickCharging(owner);
            case LAUNCHING -> tickLaunching(owner);
            case STUCK -> tickStuck(owner);
            case RETURNING -> tickReturning(owner);
            case SWEEPING_HOLD -> tickSweepingHold(owner);
            case SWEEPING_RELEASE -> tickSweepingRelease(owner);
            case BLOCKING -> tickBlocking(owner);
            case DYING -> tickDying(owner);
        }
        updateOrientation();
    }

    private void clientTick() {
        if (this.tickCount == 1) {
            for (int i = 0; i < 15; i++) {
                double dx = (this.random.nextDouble() - 0.5) * 1.5;
                double dy = (this.random.nextDouble() - 0.5) * 1.5;
                double dz = (this.random.nextDouble() - 0.5) * 1.5;
                this.level().addParticle(ParticleTypes.WITCH, 
                        this.getX() + dx, this.getY() + this.getBbHeight() / 2.0 + dy, this.getZ() + dz, 
                        0, 0, 0);
            }
        }

        Player owner = getOwner();
        if (owner == null) return;

                switch (getState()) {
            case HOVERING -> tickHovering(owner);
            case CHARGING -> tickChargingClient(owner);
            case LAUNCHING -> tickLaunchingClient();
            case STUCK -> {}
            case RETURNING -> tickReturningClient(owner);
            case SWEEPING_HOLD -> tickSweepingHoldClient(owner);
            case SWEEPING_RELEASE -> tickSweepingReleaseClient(owner);
            case BLOCKING -> tickBlockingClient(owner);
            case DYING -> {}
        }
        updateOrientation();
    }

    // === HOVERING ===

    private void tickHovering(Player owner) {
        if (getGuardCooldown() > 0) setGuardCooldown(getGuardCooldown() - 1);
        updateTargetPosition(owner);
        applySpringPhysics();
        updateMobAwareness(owner);
    }

    // === BLOCKING ===

        public void startBlocking() {
        setState(FamiliarState.BLOCKING);
    }

    public void stopBlocking() {
        triggerAnim("action", ANIM_PREFIX + "block_slash");
        setState(FamiliarState.HOVERING);
    }

    public void guardBreak() {
        triggerAnim("action", ANIM_PREFIX + "guard_break");
        setState(FamiliarState.HOVERING);
        setGuardCooldown(60);
    }

    private void tickBlocking(Player owner) {
        Vec3 target = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
        targetPosition = target;
        applySpringPhysics();
    }

    private void tickBlockingClient(Player owner) {
        Vec3 target = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
        targetPosition = target;
        applySpringPhysics();
    }

    // === CHARGING ===

    public boolean startCharging() {
        Player owner = getOwner();
        if (owner == null) return false;

        Vec3 rightSide = computeCandidatePosition(owner, 0);
        Vec3 leftSide = computeCandidatePosition(owner, 1);
        if (isPositionObstructed(rightSide) && isPositionObstructed(leftSide)) {
            return false;
        }

        this.chargeTimer = 0;
        setState(FamiliarState.CHARGING);
        return true;
    }

    public boolean isChargeReady() {
        return chargeTimer >= CHARGE_THRESHOLD_TICKS;
    }

    public int getChargeTimer() {
        return chargeTimer;
    }

    private void tickCharging(Player owner) {
        chargeTimer++;

        // Apply movement slowdown via attribute modifier (like bow draw)
        AttributeInstance speedAttr = owner.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(CHARGE_SLOW_ID) == null) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    CHARGE_SLOW_ID, -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        // Keep sword at the side position (reuse candidate system — prefer right/left)
        updateChargingPosition(owner);
        applySpringPhysics();
    }

    private void tickChargingClient(Player owner) {
        chargeTimer++;
        updateChargingPosition(owner);
        applySpringPhysics();
    }

        private void updateChargingPosition(Player owner) {
            // Prefer right side (candidate 0), fall back to left (candidate 1).
            // +1.0 raises the charge pose to roughly head height; the renderer now puts the
            // model exactly on the hitbox, so this offset is the real visual height.
            Vec3 rightSide = computeCandidatePosition(owner, 0).add(0, 1.0, 0);
            if (!isPositionObstructed(rightSide)) {
                targetPosition = rightSide;
            } else {
                Vec3 leftSide = computeCandidatePosition(owner, 1).add(0, 1.0, 0);
                targetPosition = leftSide;
            }
        }

    // === LAUNCHING ===

    public void launch(Vec3 direction, boolean charged) {
        removeChargeSlowdown();
        this.launchDirection = direction.normalize();
        this.entityData.set(DATA_LAUNCH_DIR, this.launchDirection.toVector3f());
        this.launchOrigin = this.position();
        this.chargedLaunch = charged;
        this.outboundHitSet.clear();
        // Snap orientation now so the first rotation the client receives already matches
        this.setYRot((float) Math.toDegrees(Math.atan2(-launchDirection.x, launchDirection.z)));
        double horizDist = Math.sqrt(launchDirection.x * launchDirection.x + launchDirection.z * launchDirection.z);
        this.setXRot((float) -Math.toDegrees(Math.atan2(launchDirection.y, horizDist)));
        setState(FamiliarState.LAUNCHING);
    }

    public Vec3 getLaunchDirection() {
        Vector3f v = this.entityData.get(DATA_LAUNCH_DIR);
        return new Vec3(v.x(), v.y(), v.z());
    }

    private void removeChargeSlowdown() {
        Player owner = getOwner();
        if (owner == null) return;
        AttributeInstance speedAttr = owner.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(CHARGE_SLOW_ID);
        }
    }

    private void tickLaunching(Player owner) {
        if (recallPending) {
            recallPending = false;
            enterReturning();
            return;
        }

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
        Vec3 dir = getLaunchDirection();
        if (dir.lengthSqr() < 1.0e-4) return; // data not synced yet this tick
        double speed = chargedLaunch ? LAUNCH_SPEED_CHARGED : LAUNCH_SPEED_NORMAL;
        this.setPos(this.position().add(dir.scale(speed)));
    }

    // === STUCK ===

    private void enterStuck() {
        setState(FamiliarState.STUCK);
        stuckTimer = 0;
    }

    private void tickStuck(Player owner) {
        if (recallPending) {
            recallPending = false;
            enterReturning();
            return;
        }

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
            this.recallPending = true;
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

    // === SWEEPING_HOLD ===

    public void startSweeping() {
        this.sweepVelocity = Vec3.ZERO;
        this.sweepIFrames.clear();
        setState(FamiliarState.SWEEPING_HOLD);
    }

    public void applySweepMomentum(float yawDelta, float pitchDelta) {
        double totalDelta = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
        if (totalDelta < 3.0) return; // Minimum threshold: ~3 degrees per tick

        Player owner = getOwner();
        if (owner == null) return;

        float yaw = owner.getYRot() + yawDelta;
        float pitch = owner.getXRot() + pitchDelta;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        Vec3 swingDir = new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
        );

        double force = Math.min(totalDelta * SWEEP_MOMENTUM_SCALE, SWEEP_MAX_SPEED * 0.5);
        this.sweepVelocity = this.sweepVelocity.add(swingDir.scale(force));
    }

    private void tickSweepingHold(Player owner) {
        Vec3 lookDir = owner.getLookAngle();
        Vec3 eyePos = owner.getEyePosition();
        Vec3 holdTarget = eyePos.add(lookDir.scale(SWEEP_HOLD_DISTANCE));

        Vec3 currentPos = this.position();
        Vec3 toTarget = holdTarget.subtract(currentPos);
        Vec3 springForce = toTarget.scale(SWEEP_SPRING_STRENGTH);

        this.sweepVelocity = this.sweepVelocity.add(springForce);
        this.sweepVelocity = this.sweepVelocity.scale(SWEEP_DAMPING);

        if (this.sweepVelocity.length() > SWEEP_MAX_SPEED) {
            this.sweepVelocity = this.sweepVelocity.normalize().scale(SWEEP_MAX_SPEED);
        }

        Vec3 prevPos = currentPos;
        Vec3 nextPos = currentPos.add(this.sweepVelocity);
        this.setPos(nextPos);

        // Tick down i-frames
        sweepIFrames.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            return entry.getValue() <= 0;
        });

        // Contact damage with directional knockback
        sweepDamageEntities(prevPos, nextPos, owner);
    }

    private void tickSweepingHoldClient(Player owner) {
        Vec3 lookDir = owner.getLookAngle();
        Vec3 eyePos = owner.getEyePosition();
        Vec3 holdTarget = eyePos.add(lookDir.scale(SWEEP_HOLD_DISTANCE));

        Vec3 currentPos = this.position();
        Vec3 toTarget = holdTarget.subtract(currentPos);
        Vec3 springForce = toTarget.scale(SWEEP_SPRING_STRENGTH);

        this.sweepVelocity = this.sweepVelocity.add(springForce);
        this.sweepVelocity = this.sweepVelocity.scale(SWEEP_DAMPING);

        if (this.sweepVelocity.length() > SWEEP_MAX_SPEED) {
            this.sweepVelocity = this.sweepVelocity.normalize().scale(SWEEP_MAX_SPEED);
        }

        this.setPos(currentPos.add(this.sweepVelocity));
    }

    private void sweepDamageEntities(Vec3 from, Vec3 to, Player owner) {
        AABB sweepBox = new AABB(
                Math.min(from.x, to.x) - 0.5, Math.min(from.y, to.y) - 0.5, Math.min(from.z, to.z) - 0.5,
                Math.max(from.x, to.x) + 0.5, Math.max(from.y, to.y) + 0.5, Math.max(from.z, to.z) + 0.5
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e.isAlive() && e != owner && !sweepIFrames.containsKey(e.getId()));

        if (entities.isEmpty()) return;

        Vec3 travelDir = this.sweepVelocity.length() > 0.01 ? this.sweepVelocity.normalize() : owner.getLookAngle();
        DamageSource source = this.level().damageSources().playerAttack(owner);

        for (LivingEntity entity : entities) {
            entity.hurt(source, SWEEP_CONTACT_DAMAGE);
            // Directional knockback in sword travel direction
            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    travelDir.x * SWEEP_KNOCKBACK_STRENGTH,
                    0.1 + travelDir.y * SWEEP_KNOCKBACK_STRENGTH * 0.5,
                    travelDir.z * SWEEP_KNOCKBACK_STRENGTH
            ));
            entity.hurtMarked = true;
            sweepIFrames.put(entity.getId(), SWEEP_IFRAME_TICKS);
        }
    }

    // === SWEEPING_RELEASE ===

    public void releaseSweep() {
        if (this.sweepVelocity.length() < 0.15) {
            enterHoveringFromSweep();
            return;
        }
        this.sweepReturning = false;
        this.sweepReleaseHitSet.clear();
        this.sweepReturnHitSet.clear();
        setState(FamiliarState.SWEEPING_RELEASE);
    }

    private void tickSweepingRelease(Player owner) {
        Vec3 currentPos = this.position();
        Vec3 ownerPos = owner.position().add(0, owner.getBbHeight() * 0.5, 0);

        if (sweepReturning) {
            // Hilt-first return — phases through blocks
            double distance = currentPos.distanceTo(ownerPos);
            if (distance <= PICKUP_RANGE) {
                enterHoveringFromSweep();
                return;
            }
            Vec3 toOwner = ownerPos.subtract(currentPos).normalize();
            // Store reversed velocity so updateOrientation() points hilt-first (backwards)
            this.sweepVelocity = toOwner.scale(-SWEEP_RETURN_SPEED);
            Vec3 nextPos = currentPos.add(toOwner.scale(SWEEP_RETURN_SPEED));
            damageEntitiesInPath(currentPos, nextPos, sweepReturnHitSet, SWEEP_RELEASE_DAMAGE, owner);
            this.setPos(nextPos);
            return;
        }

        // Outbound phase: check if should start returning
        double distFromPlayer = currentPos.distanceTo(ownerPos);
        boolean pastMaxRadius = distFromPlayer >= SWEEP_RELEASE_MAX_RADIUS;
        boolean velocityDepleted = this.sweepVelocity.length() < 0.05;

        // TODO: future STUCK mechanic — block collision could embed sword here instead of phasing
        // BlockHitResult blockHit = this.level().clip(new ClipContext(
        //         currentPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        // if (blockHit.getType() == HitResult.Type.BLOCK) { enterStuck(); return; }

        if (pastMaxRadius || velocityDepleted) {
            this.sweepReturnHitSet.clear();
            this.sweepReturning = true;
            return;
        }

        // Apply drag to gradually slow down
        this.sweepVelocity = this.sweepVelocity.scale(0.96);

        Vec3 nextPos = currentPos.add(this.sweepVelocity);
        // Phases through blocks on outbound
        this.setPos(nextPos);

        // Damage entities in path (8 damage, each entity hit once)
        damageEntitiesInPath(currentPos, nextPos, sweepReleaseHitSet, SWEEP_RELEASE_DAMAGE, owner);
    }

    private void tickSweepingReleaseClient(Player owner) {
        Vec3 currentPos = this.position();
        Vec3 ownerPos = owner.position().add(0, owner.getBbHeight() * 0.5, 0);

        if (sweepReturning) {
            double distance = currentPos.distanceTo(ownerPos);
            if (distance <= PICKUP_RANGE) return;
            Vec3 toOwner = ownerPos.subtract(currentPos).normalize();
            this.setPos(currentPos.add(toOwner.scale(SWEEP_RETURN_SPEED)));
            return;
        }

        double distFromPlayer = currentPos.distanceTo(ownerPos);
        boolean pastMaxRadius = distFromPlayer >= SWEEP_RELEASE_MAX_RADIUS;
        boolean velocityDepleted = this.sweepVelocity.length() < 0.05;

        if (pastMaxRadius || velocityDepleted) {
            this.sweepReturning = true;
            return;
        }

        this.sweepVelocity = this.sweepVelocity.scale(0.96);
        this.setPos(currentPos.add(this.sweepVelocity));
    }

    private void enterHoveringFromSweep() {
        Player owner = getOwner();
        setState(FamiliarState.HOVERING);
        this.sweepVelocity = Vec3.ZERO;
        this.sweepReturning = false;
        this.velocity = Vec3.ZERO;
        this.smoothedAnchorY = Double.NaN;
        if (owner != null) {
            Vec3 hoverPos = computeCandidatePosition(owner, 0);
            this.setPos(hoverPos);
            this.targetPosition = hoverPos;
        }
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
        // Recurring check: drift back to the most-preferred free candidate once it has
        // stayed unobstructed for a short while (hysteresis avoids flip-flopping).
        if (currentCandidateIndex != 0) {
            int best = -1;
            for (int i = 0; i < currentCandidateIndex; i++) {
                if (!isPositionObstructed(computeCandidatePosition(owner, i))) {
                    best = i;
                    break;
                }
            }
            if (best >= 0) {
                preferredFreeTicks++;
                if (preferredFreeTicks >= PREFERRED_RETURN_DELAY_TICKS) {
                    currentCandidateIndex = best;
                    preferredFreeTicks = 0;
                }
            } else {
                preferredFreeTicks = 0;
            }
        }

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
            if (getState() == FamiliarState.DYING) return;
        
            for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
                var stack = owner.getInventory().getItem(i);
                if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                    HeirloomSwordItem.setMode(stack, SwordMode.NORMAL);
                    stack.remove(ModDataComponents.FAMILIAR_UUID.get());
                    break;
                }
            }
            owner.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("msg.heirloomswordmod.sword_returns"), true);
            triggerAnim("action", ANIM_PREFIX + "death_fall");
            setState(FamiliarState.DYING);
            dyingTimer = 0;
        }

        private void tickDying(Player owner) {
            dyingTimer++;
            if (dyingTimer >= DYING_ANIMATION_TICKS) {
                this.discard();
            }
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
        return true; // Re-enabled for testing
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return horizontal ? DIMENSIONS_HORIZONTAL : DIMENSIONS_VERTICAL;
    }

    @Override
    protected AABB makeBoundingBox() {
        if (!horizontal) return super.makeBoundingBox();

        double yawRad = Math.toRadians(this.getYRot());
        double sinYaw = Math.abs(Math.sin(yawRad));
        double cosYaw = Math.abs(Math.cos(yawRad));
        Vec3 pos = this.position();

        if (getState() == FamiliarState.BLOCKING) {
            // Blade is held upright and rolled 45 deg across the view (block_stance):
            // wide across the look direction and tall, thin along the look axis.
            double halfAcross = 1.1;
            double halfX = cosYaw * halfAcross + sinYaw * SWORD_HALF_THICKNESS;
            double halfZ = sinYaw * halfAcross + cosYaw * SWORD_HALF_THICKNESS;
            return new AABB(
                    pos.x - halfX, pos.y - halfAcross, pos.z - halfZ,
                    pos.x + halfX, pos.y + halfAcross, pos.z + halfZ
            );
        }

        // Tight AABB around the rotated blade: 3 long, 0.4 thick, oriented along getYRot().
        double halfX = sinYaw * SWORD_HALF_LENGTH + cosYaw * SWORD_HALF_THICKNESS;
        double halfZ = cosYaw * SWORD_HALF_LENGTH + sinYaw * SWORD_HALF_THICKNESS;
        return new AABB(
                pos.x - halfX, pos.y - SWORD_HALF_THICKNESS, pos.z - halfZ,
                pos.x + halfX, pos.y + SWORD_HALF_THICKNESS, pos.z + halfZ
        );
    }

    public boolean isHorizontal() {
        return horizontal;
    }

    public float getHorizontalProgress(float partialTick) {
        return Mth.lerp(partialTick, horizontalProgressO, horizontalProgress);
    }

    private void updateOrientation() {
        FamiliarState currentState = getState();
        boolean shouldBeHorizontal = switch (currentState) {
            case HOVERING -> awarenessTarget != null;
            case DYING -> false;
            default -> true;
        };
        if (shouldBeHorizontal != horizontal) {
            horizontal = shouldBeHorizontal;
            refreshDimensions();
        }
        horizontalProgressO = horizontalProgress;
        float targetProgress = horizontal ? 1.0f : 0.0f;
        horizontalProgress = Mth.approach(horizontalProgress, targetProgress, 0.2f);

        if (currentState == FamiliarState.STUCK) {
            // Embedded in a block — keep exactly the orientation it had on impact.
            if (horizontal) {
                this.setBoundingBox(makeBoundingBox());
            }
            return;
        }

        float targetYaw = this.getYRot();
        float targetPitch = this.getXRot();

        if (getState() == FamiliarState.HOVERING && awarenessTarget != null) {
            Vec3 toTarget = awarenessTarget.position()
                    .add(0, awarenessTarget.getBbHeight() * 0.5, 0)
                    .subtract(this.position());
            targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            double horizDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            targetPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizDist));
        } else if (getState() == FamiliarState.LAUNCHING) {
            Vec3 dir = getLaunchDirection();
            if (dir.lengthSqr() > 1.0e-4) {
                targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
            }
                } else if (getState() == FamiliarState.RETURNING) {
            Player owner = getOwner();
            if (owner != null) {
                // Invert direction so hilt points at player
                Vec3 toOwner = this.position().subtract(owner.position().add(0, owner.getBbHeight() * 0.5, 0));
                targetYaw = (float) Math.toDegrees(Math.atan2(-toOwner.x, toOwner.z));
                double horizDist = Math.sqrt(toOwner.x * toOwner.x + toOwner.z * toOwner.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(toOwner.y, horizDist));
            }
                } else if (getState() == FamiliarState.SWEEPING_HOLD || getState() == FamiliarState.SWEEPING_RELEASE) {
            Vec3 dir = this.sweepVelocity.lengthSqr() > 0.05 ? this.sweepVelocity : null;
            if (dir == null) {
                Player owner = getOwner();
                if (owner != null) dir = owner.getLookAngle();
            }
            if (dir != null) {
                // Tip points along the travel direction. During the release return phase
                // sweepVelocity is stored reversed, so the hilt leads automatically.
                targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
            }
        } else if (getState() == FamiliarState.BLOCKING) {
            Player owner = getOwner();
            if (owner != null) {
                Vec3 look = owner.getLookAngle();
                targetYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                targetPitch = 0;
            }
        } else if (currentState == FamiliarState.CHARGING) {
            Player owner = getOwner();
            if (owner != null) {
                targetYaw = owner.getYRot();
                targetPitch = owner.getXRot();
            }
        } else {
            Player owner = getOwner();
            if (owner != null) {
                targetYaw = owner.getYRot();
            }
            targetPitch = 0;
        }

        float lerpFactor = (currentState == FamiliarState.LAUNCHING || currentState == FamiliarState.STUCK) ? 1.0f : 0.25f;
        float smoothedYaw = Mth.rotLerp(lerpFactor, this.getYRot(), targetYaw);
        float smoothedPitch = Mth.rotLerp(lerpFactor, this.getXRot(), targetPitch);
        this.setYRot(smoothedYaw);
        this.setXRot(smoothedPitch);

        if (horizontal) {
            this.setBoundingBox(makeBoundingBox());
        }
    }

    private static final String ANIM_PREFIX = "animation.alucard_sword.";

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animationPredicate)
                .setAnimationSpeedHandler(state -> {
                    if (getState() == FamiliarState.CHARGING) {
                        float t = Math.min(chargeTimer / 15.0f, 1.0f);
                        return (double) t;
                    }
                    return 1.0;
                }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim(ANIM_PREFIX + "block_slash", RawAnimation.begin().thenPlay(ANIM_PREFIX + "block_slash"))
                .triggerableAnim(ANIM_PREFIX + "guard_break", RawAnimation.begin().thenPlay(ANIM_PREFIX + "guard_break"))
                .triggerableAnim(ANIM_PREFIX + "death_fall", RawAnimation.begin().thenPlay(ANIM_PREFIX + "death_fall")));
    }

    private PlayState animationPredicate(AnimationState<SwordFamiliarEntity> state) {
        FamiliarState familiarState = getState();
        String anim = "idle";

        switch (familiarState) {
            case HOVERING -> anim = awarenessTarget != null ? "alert" : "idle";
            case CHARGING -> anim = "charge_spin";
            case LAUNCHING -> anim = "launch";
            case STUCK -> anim = "stuck";
            case RETURNING -> anim = "return";
            case SWEEPING_HOLD -> anim = "sweep_hold";
            case SWEEPING_RELEASE -> anim = sweepReturning ? "return_hilt" : "launch";
            case BLOCKING -> anim = "block_stance";
            case DYING -> anim = "idle";
        }

        state.getController().setAnimation(RawAnimation.begin().thenLoop(ANIM_PREFIX + anim));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
