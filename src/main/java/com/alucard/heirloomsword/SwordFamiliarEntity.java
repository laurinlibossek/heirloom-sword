package com.alucard.heirloomsword;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
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
import net.minecraft.world.item.ItemStack;
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
    private static final EntityDataAccessor<Boolean> DATA_CHARGED =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_QUICKFIRE_TARGET =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);

    private static final double HOVER_RADIUS = 1.65; // [TUNE] 1.5 felt too close, 1.8 too far
    private static final double HORIZONTAL_LOCK_LIFT = 0.5; // [TUNE] extra hover height while horizontal/locked-on
    private static final double COLLISION_SPHERE_RADIUS = 0.4;
    private static final double MAX_LAG_DISTANCE = 3.0;
    private static final double MOB_AWARENESS_RADIUS = 16.0;

    private static final double SPRING_STIFFNESS = 0.8;
    private static final double SPRING_DAMPING = 0.85;
    private static final double VERTICAL_SMOOTHING = 0.05;

    private static final double LAUNCH_SPEED_NORMAL = 2.08;
    private static final double LAUNCH_SPEED_CHARGED = 4.8;
    private static final double RETURN_SPEED = 1.8;
    private static final double MAX_LAUNCH_RANGE = 48.0;
    private static final double PICKUP_RANGE = 1.5;
    private static final int STUCK_TIMEOUT_TICKS = 60; // 3 seconds
    private static final int CHARGE_THRESHOLD_TICKS = 60; // 3 seconds for charged tier
    private static final ResourceLocation CHARGE_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "charge_slowdown");

    private static final float QUICK_FIRE_DAMAGE = 12.0f;       // [TUNE]
    private static final int QUICK_FIRE_COOLDOWN_TICKS = 20;    // [TUNE] ~1s

    private static final float UNDEAD_BURN_SECONDS = 4.0f; // [TUNE] holy blade ignites undead

    private static final float LAUNCH_DAMAGE_NORMAL = 16.0f;
    private static final float LAUNCH_DAMAGE_CHARGED = 32.0f;
    private static final float RETURN_DAMAGE = 8.0f;
    private static final float BLOCK_SLASH_DAMAGE = 13.0f;  // [TUNE 12-14 per design doc]
    private static final double BLOCK_SLASH_RANGE = 3.0;    // [TUNE]

    // SWEEPING constants
    private static final double SWEEP_HOLD_DISTANCE = 1.8;
    private static final float SWEEP_CONTACT_DAMAGE = 4.0f;
    private static final float SWEEP_RELEASE_DAMAGE = 8.0f;
    private static final int SWEEP_IFRAME_TICKS = 6;
    private static final double SWEEP_MOMENTUM_SCALE = 0.08;
    private static final double SWEEP_DAMPING = 0.72;
    private static final double SWEEP_SPRING_STRENGTH = 0.35;
    private static final double SWEEP_MAX_SPEED = 1.2;
    private static final double SWEEP_RELEASE_MAX_RADIUS = 12.0;
        private static final double SWEEP_RETURN_SPEED = 1.5;
    private static final float SWEEP_KNOCKBACK_STRENGTH = 0.3f;
    private int dyingTimer = 0;
    private static final int DYING_ANIMATION_TICKS = 10;

    // ARRIVING state (sky-drop spawn)
    private static final double SKY_DROP_HEIGHT = 16.0;  // [TUNE] how high above the slot
    private static final double MIN_SKY_CLEARANCE = 6.0; // below this, fall back to materialize
    private static final double ARRIVE_SPEED = 2.5;      // [TUNE] blocks/tick descent
    private boolean skyDropSpawn = false;

    public boolean isSkyDropSpawn() {
        return skyDropSpawn;
    }

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

    // QUICK_FIRE state fields
    private int quickFireCooldown = 0;

    // RETURNING state fields
    private final Set<Integer> returnHitSet = new HashSet<>();
    private boolean recallPending = false;

    // SWEEPING state fields
    private Vec3 sweepVelocity = Vec3.ZERO;
    private boolean sweepReturning = false;
    private final Map<Integer, Integer> sweepIFrames = new HashMap<>();
    private final Set<Integer> sweepReleaseHitSet = new HashSet<>();
    private final Set<Integer> sweepReturnHitSet = new HashSet<>();

    // Sawblade spin while SWEEPING_HOLD — client-side visual only
    private float spinAngle = 0.0f;
    private float spinAngleO = 0.0f;
    private static final float SWEEP_SPIN_DEG_PER_TICK = 60.0f; // [TUNE] ~3.3 rev/s

    private int spinRampTicks = 0;
    private static final int SPIN_RAMP_TICKS = 8; // [TUNE] rev-up time

    public float getSpinAngle(float partialTick) {
        return spinAngleO + (spinAngle - spinAngleO) * partialTick;
    }

    private void tickSpinClient(float speedScale) {
        spinAngleO = spinAngle;
        spinAngle += SWEEP_SPIN_DEG_PER_TICK * speedScale;
        if (spinAngleO >= 360.0f) { // wrap both together so the partialTick lerp never jumps
            spinAngle -= 360.0f;
            spinAngleO -= 360.0f;
        }
    }

    public boolean isSweepReturning() {
        return sweepReturning;
    }

    // Client-side state-transition tracking (visual effects only)
    private FamiliarState lastClientState = FamiliarState.HOVERING;
    private int slashVisualTicks = 0;
    private static final int SLASH_VISUAL_TICKS = 14; // matches block_slash clip (0.7s)

    public boolean isSlashing() {
        return slashVisualTicks > 0;
    }

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
        Vec3 hoverPos = computeCandidatePosition(owner, 0);
        this.targetPosition = hoverPos;

        // Sky-drop entrance when there's vertical clearance; materialize otherwise
        BlockHitResult skyHit = level.clip(new ClipContext(
                hoverPos, hoverPos.add(0, SKY_DROP_HEIGHT, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double clearance = skyHit.getType() == HitResult.Type.BLOCK
                ? skyHit.getLocation().y - hoverPos.y
                : SKY_DROP_HEIGHT;
        if (clearance >= MIN_SKY_CLEARANCE) {
            this.setPos(hoverPos.add(0, clearance - 1.0, 0));
            setState(FamiliarState.ARRIVING);
        } else {
            this.setPos(hoverPos);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_STATE, FamiliarState.HOVERING.getId());
        builder.define(DATA_GUARD_COOLDOWN, 0);
        builder.define(DATA_LAUNCH_DIR, new Vector3f());
        builder.define(DATA_CHARGED, false);
        builder.define(DATA_QUICKFIRE_TARGET, 0);
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
            case ARRIVING -> tickArriving(owner);
            case QUICK_FIRE -> tickQuickFire(owner);
        }
        burnUndeadOnContact(owner);
        updateOrientation();
    }

    private void clientTick() {
        FamiliarState st = getState();
        if (st != lastClientState) {
            onClientStateChange(lastClientState, st);
            lastClientState = st;
        }
        if (slashVisualTicks > 0) slashVisualTicks--;

        if (this.tickCount == 1) {
            skyDropSpawn = getState() == FamiliarState.ARRIVING;
        }
        if (this.tickCount == 1 && getState() != FamiliarState.ARRIVING) {
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
            case ARRIVING -> tickArrivingClient();
            case QUICK_FIRE -> tickQuickFireClient();
        }
        updateOrientation();
    }

    private void onClientStateChange(FamiliarState from, FamiliarState to) {
        if (from == FamiliarState.BLOCKING && to == FamiliarState.HOVERING && hasSlashTarget()) {
            // hasSlashTarget() already exists (smart-slash hotfix): the server only fires
            // block_slash when a hostile is in reach, so mirror that gate here — otherwise
            // this window would hold the guard pose on slash-less releases.
            slashVisualTicks = SLASH_VISUAL_TICKS; // block_slash is playing — hold the guard pose
        }
        if (to == FamiliarState.SWEEPING_HOLD) {
            spinRampTicks = 0; // rev the sawblade up from rest
        }
        if (from == FamiliarState.ARRIVING && to == FamiliarState.HOVERING) {
            for (int i = 0; i < 24; i++) {
                double angle = (Math.PI * 2 * i) / 24;
                this.level().addParticle(ParticleTypes.WITCH,
                        getX() + Math.cos(angle) * 0.8, getY(), getZ() + Math.sin(angle) * 0.8,
                        Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15);
            }
        }
    }

    // === HOVERING ===

    private void tickHovering(Player owner) {
        if (quickFireCooldown > 0) quickFireCooldown--;
        if (getGuardCooldown() > 0) setGuardCooldown(getGuardCooldown() - 1);
        updateTargetPosition(owner);
        applySpringPhysics();
        updateMobAwareness(owner);
    }

    // === BLOCKING ===

        public void startBlocking() {
        setState(FamiliarState.BLOCKING);
    }

    public void cancelChargeIntoBlock() {
        removeChargeSlowdown();
        chargeTimer = 0;
        setState(FamiliarState.BLOCKING);
    }

    /** Abort a charge back to HOVERING with no launch (e.g. the player opened a screen/paused). */
    public void cancelCharge() {
        removeChargeSlowdown();
        chargeTimer = 0;
        setState(FamiliarState.HOVERING);
    }

    public void cancelSweepIntoBlock() {
        this.sweepVelocity = Vec3.ZERO;
        this.sweepIFrames.clear();
        setState(FamiliarState.BLOCKING);
    }

    public void stopBlocking() {
        // "Smart" sword: only swing if a hostile is actually within slash reach —
        // releasing guard after deflecting a distant arrow shouldn't whiff a slash.
        if (hasSlashTarget()) {
            triggerAnim("action", ANIM_PREFIX + "block_slash");
            doBlockSlashDamage();
        }
        setState(FamiliarState.HOVERING);
    }

    /** True when a living hostile is inside the frontal arc the block slash would hit. */
    public boolean hasSlashTarget() {
        Player owner = getOwner();
        if (owner == null) return false;

        Vec3 center = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
        AABB arc = new AABB(center, center).inflate(BLOCK_SLASH_RANGE, 1.2, BLOCK_SLASH_RANGE);
        Vec3 lookFlat = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z).normalize();

        return !this.level().getEntitiesOfClass(LivingEntity.class, arc, e -> {
            if (!(e instanceof Enemy) || !e.isAlive()) return false;
            Vec3 toEntity = e.position().subtract(owner.position());
            Vec3 toEntityFlat = new Vec3(toEntity.x, 0, toEntity.z).normalize();
            return toEntityFlat.dot(lookFlat) > 0.1;
        }).isEmpty();
    }

    private void doBlockSlashDamage() {
        Player owner = getOwner();
        if (owner == null || this.level().isClientSide()) return;

        Vec3 center = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
        AABB arc = new AABB(center, center).inflate(BLOCK_SLASH_RANGE, 1.2, BLOCK_SLASH_RANGE);
        Vec3 lookFlat = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z).normalize();

        DamageSource source = this.level().damageSources().playerAttack(owner);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, arc,
                e -> e.isAlive() && e != owner)) {
            Vec3 toEntity = entity.position().subtract(owner.position());
            Vec3 toEntityFlat = new Vec3(toEntity.x, 0, toEntity.z).normalize();
            if (toEntityFlat.dot(lookFlat) <= 0.1) continue; // frontal ~180° arc only
            entity.hurt(source, BLOCK_SLASH_DAMAGE);
            igniteIfUndead(entity);
            entity.knockback(0.4, owner.getX() - entity.getX(), owner.getZ() - entity.getZ());
        }

        this.level().playSound(null, owner.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public void guardBreak() {
        triggerAnim("action", ANIM_PREFIX + "guard_break");
        setState(FamiliarState.HOVERING);
        setGuardCooldown(60);
    }

    private void tickBlocking(Player owner) {
        if (!ManaService.drain(owner, ManaService.BLOCK_DRAIN_PER_TICK)) {
            // Mana exhausted while guarding — guard break (existing 3s cooldown applies).
            guardBreak();
            return;
        }
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
        // Only drain while charging up; a fully-charged blade costs no further mana.
        if (!isChargeReady() && !ManaService.drain(owner, ManaService.CHARGE_DRAIN_PER_TICK)) {
            // Mana exhausted before charge is ready — stop, no launch.
            removeChargeSlowdown();
            chargeTimer = 0;
            setState(FamiliarState.HOVERING);
            return;
        }
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
        // Telekinetic gather — converging glyphs, ramping with charge [TUNE rates]
        // Stop glyphs once fully charged to signify enough magic power present
        if (!isChargeReady()) {
            int count = 1 + Math.min(chargeTimer / 30, 2);
            for (int i = 0; i < count; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double dist = 0.8 + this.random.nextDouble() * 0.6;
                double px = getX() + Math.cos(angle) * dist;
                double py = getY() + getBbHeight() * 0.5 + (this.random.nextDouble() - 0.5) * 1.2;
                double pz = getZ() + Math.sin(angle) * dist;
                this.level().addParticle(ParticleTypes.ENCHANT, px, py, pz,
                        (getX() - px) * 0.35,
                        (getY() + getBbHeight() * 0.5 - py) * 0.35,
                        (getZ() - pz) * 0.35);
            }
        }
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
        this.entityData.set(DATA_CHARGED, charged);
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

    public boolean isChargedLaunch() {
        return this.entityData.get(DATA_CHARGED);
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
        double speed = isChargedLaunch() ? LAUNCH_SPEED_CHARGED : LAUNCH_SPEED_NORMAL;
        this.setPos(this.position().add(dir.scale(speed)));
        // Flight trail — END_ROD like the spawn streak [TUNE density]
        if (this.random.nextFloat() < (isChargedLaunch() ? 0.8f : 0.4f)) {
            this.level().addParticle(ParticleTypes.END_ROD,
                    getX() + (this.random.nextDouble() - 0.5) * 0.3,
                    getY() + getBbHeight() * 0.5 + (this.random.nextDouble() - 0.5) * 0.3,
                    getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                    0, 0, 0);
        }
    }

    // === STUCK ===

    private void enterStuck() {
        setState(FamiliarState.STUCK);
        stuckTimer = 0;
        if (!this.level().isClientSide) {
            SwordSounds.playStuckImpact(this.level(), this.getX(), this.getY(), this.getZ());
        }
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
            // Arrived — hand over to the hover spring, which glides it into the slot
            setState(FamiliarState.HOVERING);
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            this.targetPosition = computeCandidatePosition(owner, 0);
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
        if (!ManaService.drain(owner, ManaService.SWEEP_DRAIN_PER_TICK)) {
            // Mana exhausted mid-sweep — end it (transitions to SWEEPING_RELEASE / HOVERING).
            releaseSweep();
            return;
        }
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
        spinRampTicks++;
        tickSpinClient(Math.min(1.0f, spinRampTicks / (float) SPIN_RAMP_TICKS));

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
                Math.min(from.x, to.x) - SWORD_HALF_LENGTH, Math.min(from.y, to.y) - 0.5, Math.min(from.z, to.z) - SWORD_HALF_LENGTH,
                Math.max(from.x, to.x) + SWORD_HALF_LENGTH, Math.max(from.y, to.y) + 0.5, Math.max(from.z, to.z) + SWORD_HALF_LENGTH
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e.isAlive() && e != owner && !sweepIFrames.containsKey(e.getId()));

        if (entities.isEmpty()) return;

        Vec3 travelDir = this.sweepVelocity.length() > 0.01 ? this.sweepVelocity.normalize() : owner.getLookAngle();
        DamageSource source = this.level().damageSources().playerAttack(owner);

        for (LivingEntity entity : entities) {
            entity.hurt(source, SWEEP_CONTACT_DAMAGE);
            igniteIfUndead(entity);
            bloodyOwnerBlade(entity);
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
        damageEntitiesInPath(currentPos, nextPos, sweepReleaseHitSet, SWEEP_RELEASE_DAMAGE, owner, SWORD_HALF_LENGTH);
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

        // Thrown sawblade: keep spinning, decaying with flight speed
        tickSpinClient(Math.max(0.35f, (float) (this.sweepVelocity.length() / SWEEP_MAX_SPEED)));

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
            this.targetPosition = computeCandidatePosition(owner, 0);
        }
    }

    // === QUICK_FIRE ===

    public void quickFire() {
        if (quickFireCooldown > 0) return;
        Entity target = this.awarenessTarget; // server's own lock-on; never client-supplied
        if (target == null || !target.isAlive()) return;
        this.entityData.set(DATA_QUICKFIRE_TARGET, target.getId());
        this.quickFireCooldown = QUICK_FIRE_COOLDOWN_TICKS;
        setState(FamiliarState.QUICK_FIRE);
    }

    private void tickQuickFire(Player owner) {
        Entity target = this.level().getEntity(this.entityData.get(DATA_QUICKFIRE_TARGET));
        if (target == null || !target.isAlive()
                || this.position().distanceTo(owner.position()) > MAX_LAUNCH_RANGE) {
            enterReturning();
            return;
        }

        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 toTarget = targetCenter.subtract(this.position());

        // Contact: hit and immediately come home
        if (toTarget.length() <= LAUNCH_SPEED_NORMAL
                || this.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
            if (target instanceof LivingEntity living) {
                living.hurt(this.level().damageSources().playerAttack(owner), QUICK_FIRE_DAMAGE);
                igniteIfUndead(living);
                living.knockback(0.3, this.getX() - living.getX(), this.getZ() - living.getZ());
                bloodyOwnerBlade(living);
            }
            enterReturning();
            return;
        }

        // Homing: re-aim every tick
        Vec3 dir = toTarget.normalize();
        Vec3 nextPos = this.position().add(dir.scale(LAUNCH_SPEED_NORMAL));
        BlockHitResult blockHit = this.level().clip(new ClipContext(
                this.position(), nextPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            enterReturning(); // quick-fire never sticks — just comes back
            return;
        }
        this.setPos(nextPos);
    }

    private void tickQuickFireClient() {
        Entity target = this.level().getEntity(this.entityData.get(DATA_QUICKFIRE_TARGET));
        if (target == null) return;
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 toTarget = targetCenter.subtract(this.position());
        if (toTarget.length() <= LAUNCH_SPEED_NORMAL) return;
        this.setPos(this.position().add(toTarget.normalize().scale(LAUNCH_SPEED_NORMAL)));
    }

    // === COMBAT ===

    /** The blade is anathema to the undead — any contact sets them alight. */
    public static void igniteIfUndead(LivingEntity entity) {
        if (entity.getType().is(EntityTypeTags.UNDEAD)) {
            entity.igniteForSeconds(UNDEAD_BURN_SECONDS);
        }
    }

    /** Cosmetic: mark the owner's blade freshly bloodied (server-side), if the hit entity bleeds. */
    private void bloodyOwnerBlade(LivingEntity hit) {
        if (!canBleed(hit)) return;
        Player owner = getOwner();
        if (owner == null) return;
        ItemStack stack = HeirloomSwordItem.findInInventory(owner);
        if (!stack.isEmpty()) {
            HeirloomSwordItem.setBlood(stack, 1.0f);
        }
    }

    /** Returns false for entities that have no blood (mechanical, elemental, slime, bare bone). */
    private static boolean canBleed(LivingEntity entity) {
        EntityType<?> t = entity.getType();
        return !(
            // Constructed — no biology
            t == EntityType.IRON_GOLEM   ||
            t == EntityType.SNOW_GOLEM   ||
            t == EntityType.ARMOR_STAND  ||
            // Fire / energy — no liquid to spill
            t == EntityType.BLAZE        ||
            t == EntityType.MAGMA_CUBE   ||
            t == EntityType.VEX          ||
            // Slime — goo, not blood
            t == EntityType.SLIME        ||
            // Undead bone — dry, nothing left to bleed
            t == EntityType.SKELETON         ||
            t == EntityType.STRAY            ||
            t == EntityType.BOGGED           ||
            t == EntityType.WITHER_SKELETON  ||
            t == EntityType.SKELETON_HORSE
        );
    }

    private void burnUndeadOnContact(Player owner) {
        if (this.tickCount % 5 != 0) return;
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.2),
                e -> e.isAlive() && e != owner && e.getType().is(EntityTypeTags.UNDEAD))) {
            entity.igniteForSeconds(UNDEAD_BURN_SECONDS);
        }
    }

    private void damageEntitiesInPath(Vec3 from, Vec3 to, Set<Integer> hitSet, float damage, Player owner) {
        damageEntitiesInPath(from, to, hitSet, damage, owner, 0.5);
    }

    private void damageEntitiesInPath(Vec3 from, Vec3 to, Set<Integer> hitSet, float damage, Player owner, double hInflate) {
        AABB sweepBox = new AABB(
                Math.min(from.x, to.x) - hInflate, Math.min(from.y, to.y) - 0.5, Math.min(from.z, to.z) - hInflate,
                Math.max(from.x, to.x) + hInflate, Math.max(from.y, to.y) + 0.5, Math.max(from.z, to.z) + hInflate
        );

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e.isAlive() && e != owner && !hitSet.contains(e.getId()));

        DamageSource source = this.level().damageSources().playerAttack(owner);
        boolean returning = getState() == FamiliarState.RETURNING;
        for (LivingEntity entity : entities) {
            hitSet.add(entity.getId());
            entity.hurt(source, damage);
            igniteIfUndead(entity);
            // Return flight passes through mobs without re-bloodying; outbound strike already did.
            if (!returning) bloodyOwnerBlade(entity);
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
                // While horizontal (locked onto a hostile), float higher so the sword
                // "looks down" at the target. Scaled by the tilt blend for a smooth rise.
                targetPosition = candidate.add(0, HORIZONTAL_LOCK_LIFT * horizontalProgress, 0);
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
        double torsoY = owner.getY() + owner.getBbHeight() * 0.45; // [TUNE] was 0.55 — hover slightly lower

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

        List<LivingEntity> hostiles = this.level().getEntitiesOfClass(LivingEntity.class, scanBox,
                mob -> mob instanceof Enemy && mob.isAlive() && mob.distanceTo(owner) <= MOB_AWARENESS_RADIUS);

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
                    HeirloomSwordItem.setBlood(stack, 0f); // recall = sheathe: blood flies off instantly
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

    // === ARRIVING (sky-drop spawn) ===

    private void tickArriving(Player owner) {
        Vec3 hoverPos = computeCandidatePosition(owner, 0);
        Vec3 toTarget = hoverPos.subtract(this.position());
        if (toTarget.length() <= ARRIVE_SPEED) {
            this.setPos(hoverPos);
            this.targetPosition = hoverPos;
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            setState(FamiliarState.HOVERING);
            this.level().playSound(null, this.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.7f);
            // Landing impact: 4 damage + knockback in a 3-block radius (not the owner)
            for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(3.0), e -> e != owner && e.isAlive())) {
                target.hurt(this.level().damageSources().playerAttack(owner), 4.0f);
                double dx = target.getX() - this.getX();
                double dz = target.getZ() - this.getZ();
                target.knockback(0.5, -dx, -dz);
            }
            return;
        }
        this.setPos(this.position().add(toTarget.normalize().scale(ARRIVE_SPEED)));
    }

    private void tickArrivingClient() {
        // Falling streak [TUNE density]
        for (int i = 0; i < 2; i++) {
            this.level().addParticle(ParticleTypes.END_ROD,
                    getX() + (this.random.nextDouble() - 0.5) * 0.3,
                    getY() + this.random.nextDouble() * 2.5,
                    getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                    0, 0.1, 0);
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

        if (getState() == FamiliarState.SWEEPING_HOLD
                || (getState() == FamiliarState.SWEEPING_RELEASE && !sweepReturning)) {
            // Spinning sawblade: flat disc covering the blade's spin circle.
            return new AABB(
                    pos.x - SWORD_HALF_LENGTH, pos.y - SWORD_HALF_THICKNESS, pos.z - SWORD_HALF_LENGTH,
                    pos.x + SWORD_HALF_LENGTH, pos.y + SWORD_HALF_THICKNESS, pos.z + SWORD_HALF_LENGTH);
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
            case HOVERING -> awarenessTarget != null || slashVisualTicks > 0;
            case DYING -> false;
            case ARRIVING -> false;
            default -> true;
        };
        if (shouldBeHorizontal != horizontal) {
            horizontal = shouldBeHorizontal;
            refreshDimensions();
        }
        horizontalProgressO = horizontalProgress;
        float targetProgress = horizontal ? 1.0f : 0.0f;
        horizontalProgress = Mth.approach(horizontalProgress, targetProgress, 0.1f); // [TUNE] was 0.2 — softer lock-on blend

        if (currentState == FamiliarState.STUCK) {
            // Embedded in a block — keep exactly the orientation it had on impact.
            if (horizontal) {
                this.setBoundingBox(makeBoundingBox());
            }
            return;
        }

        float targetYaw = this.getYRot();
        float targetPitch = this.getXRot();

        if (currentState == FamiliarState.HOVERING && slashVisualTicks > 0) {
            // Mid block-slash: keep the guard-style facing so only the clip moves the model
            Player slashOwner = getOwner();
            if (slashOwner != null) {
                Vec3 look = slashOwner.getLookAngle();
                targetYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                targetPitch = 0;
            }
        } else if (getState() == FamiliarState.HOVERING && awarenessTarget != null) {
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
        } else if (getState() == FamiliarState.QUICK_FIRE) {
            Entity qfTarget = this.level().getEntity(this.entityData.get(DATA_QUICKFIRE_TARGET));
            if (qfTarget != null) {
                Vec3 to = qfTarget.position().add(0, qfTarget.getBbHeight() * 0.5, 0).subtract(this.position());
                targetYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                double horizDist = Math.sqrt(to.x * to.x + to.z * to.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(to.y, horizDist));
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
                } else if (getState() == FamiliarState.SWEEPING_HOLD) {
            // Sawblade spin (renderer-side) is the visual; keep synced rotation stable.
            Player owner = getOwner();
            if (owner != null) targetYaw = owner.getYRot();
            targetPitch = 0;
        } else if (getState() == FamiliarState.SWEEPING_RELEASE) {
            if (!sweepReturning) {
                // Spinning throw — renderer shows the spin; keep synced rotation stable
                Player owner = getOwner();
                if (owner != null) targetYaw = owner.getYRot();
                targetPitch = 0;
            } else {
                Vec3 dir = this.sweepVelocity; // stored reversed → hilt leads
                if (dir.lengthSqr() > 0.05) {
                    targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                    double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                    targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
                }
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

        float lerpFactor = (currentState == FamiliarState.LAUNCHING || currentState == FamiliarState.STUCK
                || currentState == FamiliarState.QUICK_FIRE) ? 1.0f : 0.25f;
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
            case SWEEPING_RELEASE -> anim = sweepReturning ? "return_hilt" : "sweep_hold";
            case BLOCKING -> anim = "block_stance";
            case QUICK_FIRE -> anim = "launch";
            case DYING -> anim = "idle";
            case ARRIVING -> anim = "idle"; // falling sword uses its hover orientation
        }

        state.getController().setAnimation(RawAnimation.begin().thenLoop(ANIM_PREFIX + anim));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
