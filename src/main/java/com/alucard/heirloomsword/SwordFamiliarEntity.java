package com.alucard.heirloomsword;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
    private static final EntityDataAccessor<Integer> DATA_IDLE_ANIM =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_CURIOSITY_POS =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_AWARENESS_TARGET =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKENING =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double HOVER_RADIUS = 1.5; // [TUNE] 1.5 feels right
    private static final double HORIZONTAL_LOCK_LIFT = 0.5; // [TUNE] extra hover height while horizontal/locked-on
    private static final double COLLISION_SPHERE_RADIUS = 0.4;
    private static final double MAX_LAG_DISTANCE = 3.0;
    private static final double MOB_AWARENESS_RADIUS = 16.0;

    private static final double SPRING_STIFFNESS = 0.8;
    private static final double SPRING_DAMPING = 0.85;
    private static final double VERTICAL_SMOOTHING = 0.05;

    private static final double LAUNCH_SPEED_NORMAL = 3;
    private static final double LAUNCH_SPEED_CHARGED = 6;
    private static final double RETURN_SPEED = 2.4;
    private static final double MAX_LAUNCH_RANGE = 64.0;
    private static final double PICKUP_RANGE = 1.5;
    private static final int STUCK_TIMEOUT_TICKS = 60; // 3 seconds
    // TETHERING constants — single ballistic "force pull": one launch toward the midpoint, then the
    // player arcs the rest of the way under gravity (no per-tick reel) [all TUNE → Phase 13 config]
    private static final double TETHER_PULL_SPEED = 3.2;         // blocks/tick the launch closes the gap (snappiness)
    private static final int TETHER_MIN_FLIGHT_TICKS = 4;        // floor on flight time so close pulls arc, not teleport
    private static final int TETHER_MAX_FLIGHT_TICKS = 16;       // cap so far pulls stay snappy, not floaty
    private static final double TETHER_GRAVITY = 0.08;           // MC player gravity; the vy solve uses it to land on target
    private static final double TETHER_DRAG_COMP = 1.5;          // horizontal boost to offset air drag so far pulls reach
    private static final double TETHER_ARRIVAL_RANGE = 2.0;      // within this of the midpoint → done
    private static final int TETHER_TIMEOUT_TICKS = 40;          // 2 seconds
    private static final int TETHER_GEOMETRY_BLOCK_TICKS = 10;   // ticks of near-zero travel (blocked / landed) → done
    private static final double TETHER_GEOMETRY_MOVE_SQR = 0.01; // (~0.1 block/tick)^2 movement floor
    private static final double TETHER_SLAM_DETECT_INFLATE = 1.2; // tight box around the player to detect the slam
    private static final double TETHER_SLAM_RADIUS = 3.5;         // [TUNE] AoE radius of the slam
    private static final float  TETHER_SLAM_KNOCKBACK = 1.2f;     // [TUNE] outward knockback strength
    private static float tetherSlamDamage() { return (float) Config.TETHER_SLAM_DAMAGE.getAsDouble(); }
    private static final TagKey<Block> PIERCEABLE_BLOCKS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "pierceable"));
    private static final TagKey<Block> SWEPT_AWAY_BLOCKS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "swept_away"));
    private static final int CHARGE_THRESHOLD_TICKS = 60; // 3 seconds for charged tier
    private static final ResourceLocation CHARGE_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "charge_slowdown");

    private static final double BLOCK_SLASH_RANGE = 3.0;    // [TUNE]

    // Combat values are config-backed (design §25.1). Read per-use so a /reload-style config
    // edit on world reload applies without a restart. Names mirror the deleted constants.
    private float launchDamageNormal()             { return (float) Config.LAUNCH_DAMAGE_NORMAL.getAsDouble()  * bloodlustMultiplier(); }
    private float launchDamageCharged()            { return (float) Config.LAUNCH_DAMAGE_CHARGED.getAsDouble() * bloodlustMultiplier(); }
    private static float returnDamage()            { return (float) Config.RETURN_DAMAGE.getAsDouble(); }
    private float quickFireDamage()                { return (float) Config.QUICK_FIRE_DAMAGE.getAsDouble()     * bloodlustMultiplier(); }
    private float sweepContactDamage()             { return (float) Config.SWEEP_CONTACT_DAMAGE.getAsDouble()  * bloodlustMultiplier(); }
    private float sweepReleaseDamage()             { return (float) Config.SWEEP_RELEASE_DAMAGE.getAsDouble()  * bloodlustMultiplier(); }
    private float blockSlashDamage()               { return (float) Config.BLOCK_SLASH_DAMAGE.getAsDouble()    * bloodlustMultiplier(); }
    private static float landingImpactDamage()     { return (float) Config.LANDING_IMPACT_DAMAGE.getAsDouble(); }
    private static int   quickFireCooldownTicks()  { return Config.QUICK_FIRE_COOLDOWN_TICKS.getAsInt(); }
    private static int   guardBreakCooldownTicks() { return Config.GUARD_BREAK_COOLDOWN_TICKS.getAsInt(); }
    private static float undeadBurnSeconds()       { return (float) Config.UNDEAD_IGNITE_SECONDS.getAsDouble(); }

    /** True when the owner's blade currently carries blood — drives the Bloodlust passive. */
    private boolean isOwnerBladeBloody() {
        Player owner = getOwner();
        if (owner == null) return false;
        return HeirloomSwordItem.getBlood(HeirloomSwordItem.findInInventory(owner)) > 0f;
    }

    /** 1.0 normally; the configured Bloodlust multiplier while the blade is bloodied. */
    private float bloodlustMultiplier() {
        return isOwnerBladeBloody() ? (float) Config.BLOODLUST_DAMAGE_MULT.getAsDouble() : 1.0f;
    }

    /**
     * Whether the sword may damage this target. Non-players: always. Players: only when
     * {@code integration.allowPvpDamage} is on, the server permits PvP, and vanilla team
     * friendly-fire rules allow it. Mirrors §25.5.
     */
    private boolean canDamage(Player owner, LivingEntity target) {
        // Never damage the owner's own pets
        if (target instanceof OwnableEntity owned && owner.getUUID().equals(owned.getOwnerUUID())) return false;
        // Respect scoreboard team friendly-fire for any entity (not just players)
        var ownerTeam = owner.getTeam();
        if (ownerTeam != null && ownerTeam.equals(target.getTeam()) && !ownerTeam.isAllowFriendlyFire()) return false;
        if (!(target instanceof Player victim)) return true;
        if (!Config.ALLOW_PVP_DAMAGE.getAsBoolean()) return false;
        var server = this.level().getServer();
        if (server == null || !server.isPvpAllowed()) return false;
        return owner.canHarmPlayer(victim);
    }

    private boolean isValidAwarenessTarget(Player owner, LivingEntity target) {
        if (target == null || !target.isAlive() || target.getUUID().equals(owner.getUUID())) {
            return false;
        }
        if (!canDamage(owner, target)) {
            return false;
        }
        if (target instanceof Enemy) {
            return true;
        }
        if (target instanceof Player) {
            return true;
        }
        
        UUID ownerId = owner.getUUID();
        if (target instanceof NeutralMob neutral && ownerId.equals(neutral.getPersistentAngerTarget())) {
            return true;
        }
        if (target instanceof Mob mob) {
            if (mob.getTarget() != null && ownerId.equals(mob.getTarget().getUUID())) {
                return true;
            }
            if (mob.getLastHurtByMob() != null && ownerId.equals(mob.getLastHurtByMob().getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Emits a vibration game-event at the sword's position, attributed to the owner, so sculk
     * sensors / shriekers / the Warden respond as if a real projectile was fired or landed.
     * No-op when {@code integration.sculkResonance=false} or on the client.
     */
    private void emitVibration(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event) {
        if (!Config.SCULK_RESONANCE.getAsBoolean()) return;
        if (this.level().isClientSide) return;
        Player owner = getOwner();
        // Context = the owner so detectors attribute the vibration to the player (like a real projectile).
        this.level().gameEvent(owner, event, this.position());
    }

    // SWEEPING constants
    private static final double SWEEP_HOLD_DISTANCE = 2.7; // [TUNE] was 1.8, +50% per §25 feedback
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

    private static final double AWAKENING_DESCENT_SPEED = 0.32; // [TUNE] ~2.5s over a ~16-block drop

    public void setAwakening(boolean value) { this.entityData.set(DATA_AWAKENING, value); }
    public boolean isAwakening() { return this.entityData.get(DATA_AWAKENING); }

    public boolean isSkyDropSpawn() {
        return skyDropSpawn;
    }

    private Vec3 velocity = Vec3.ZERO;
    private Vec3 targetPosition = Vec3.ZERO;
    private final IdlePersonality idle = new IdlePersonality(this);
    private double smoothedAnchorY = Double.NaN;
    private int currentCandidateIndex = 0;
    private int preferredFreeTicks = 0;
    // [TUNE] Once a more-preferred slot frees up, stay in the relocated slot this long
    // before drifting back — longer dwell so the sword commits to a position. 1500ms.
    private static final int PREFERRED_RETURN_DELAY_TICKS = 30;

    // LAUNCHING state fields
    private Vec3 launchDirection = Vec3.ZERO;
    private Vec3 launchOrigin = Vec3.ZERO;
    private boolean chargedLaunch = false;
    private final Set<Integer> outboundHitSet = new HashSet<>();

    // CHARGING state fields
    private int chargeTimer = 0;

    // STUCK state fields
    private int stuckTimer = 0;

    // TETHERING state fields
    private boolean tetherPending = false;
    private Vec3 tetherMidpoint = Vec3.ZERO;
    private int tetherTimer = 0;
    private int tetherGeometryTicks = 0;
    private Vec3 tetherLastPos = Vec3.ZERO;

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

    // Render-only personality wobble (figure-eight / curiosity drift). Eased client-side toward
    // IdlePersonality.visualOffset and applied via the renderer's getRenderOffset. The networked
    // position is NEVER moved for cosmetics — that flickers between the two ticking sides.
    private Vec3 idleVisualOffset = Vec3.ZERO;
    private Vec3 idleVisualOffsetO = Vec3.ZERO;
    private static final double IDLE_VISUAL_EASE = 0.25; // [TUNE] smoothing toward the target offset

    // Client-side smooth interpolation toward the server position, used ONLY in HOVERING.
    // Hover is near-stationary, so the old client-side spring prediction shimmered: every server
    // packet hard-snapped it back to a stale spot, then the next tick sprang it forward again.
    // Instead the client now eases toward the last server position over lerpHoverSteps ticks (the
    // base Entity.lerpTo hard-snaps, which is why this is needed). The server still runs the spring
    // (lazy-lag feel preserved and authoritative); the client merely follows it smoothly.
    private double lerpX, lerpY, lerpZ;
    private int lerpHoverSteps;

    public boolean isSlashing() {
        return slashVisualTicks > 0;
    }

    @Nullable
    private Entity serverAwarenessTarget = null;
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
        builder.define(DATA_IDLE_ANIM, 0);
        builder.define(DATA_CURIOSITY_POS, Optional.empty());
        builder.define(DATA_AWARENESS_TARGET, 0);
        builder.define(DATA_AWAKENING, false);
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

        // If the sword item left the owner's inventory (moved to chest, traded, etc.) while
        // the familiar was active, orphan-discard so the entity doesn't keep running forever.
        // onPlayerTick in SwordEventHandler will reset the mode flag when the item is next seen.
        boolean hasFlyingSword = false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            ItemStack s = owner.getInventory().getItem(i);
            if (s.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(s)) {
                hasFlyingSword = true;
                break;
            }
        }
        if (!hasFlyingSword) {
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
            case TETHERING -> tickTethering(owner);
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
            case HOVERING -> tickHoveringClient();
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
            case TETHERING -> {}
        }

        // Ease the render-only personality wobble toward its synced target. Never moves the
        // networked position; the renderer reads getIdleVisualOffset() and applies it.
        idleVisualOffsetO = idleVisualOffset;
        Vec3 idleTarget = getState() == FamiliarState.HOVERING
                ? IdlePersonality.visualOffset(this, 0f) : Vec3.ZERO;
        idleVisualOffset = idleVisualOffset.add(idleTarget.subtract(idleVisualOffset).scale(IDLE_VISUAL_EASE));

        updateOrientation();
    }

    private void onClientStateChange(FamiliarState from, FamiliarState to) {
        if (from == FamiliarState.BLOCKING && to == FamiliarState.HOVERING && hasSlashTarget()) {
            // hasSlashTarget() already exists (smart-slash hotfix): the server only fires
            // block_slash when a hostile is in reach, so mirror that gate here — otherwise
            // this window would hold the guard pose on slash-less releases.
            slashVisualTicks = SLASH_VISUAL_TICKS; // block_slash is playing — hold the guard pose
        }
        if (to == FamiliarState.CHARGING) {
            // Client predicts chargeTimer locally for the gather glyphs; the server's reset in
            // startCharging() isn't synced, so reset here on every entry or the timer stays
            // >= threshold after the first charge and the particles never reappear.
            chargeTimer = 0;
        }
        if (to == FamiliarState.SWEEPING_HOLD) {
            spinRampTicks = 0; // rev the sawblade up from rest
        }
    }

    // === HOVERING ===

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // HOVERING is near-stationary, so a hard snap to each (stale) server packet shimmers
        // against the client's own motion. Store the target and ease toward it in tickHoveringClient
        // instead. Rotation stays with updateOrientation() (deterministic from owner facing), so we
        // ignore yRot/xRot here. Every other state keeps the vanilla hard-snap — they either move
        // deterministically (launching/returning) or are fully server-driven, so snapping is invisible.
        if (this.level().isClientSide() && getState() == FamiliarState.HOVERING) {
            this.lerpX = x;
            this.lerpY = y;
            this.lerpZ = z;
            this.lerpHoverSteps = Math.max(steps, 1);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    /** Client HOVERING: ease toward the last server position (no spring prediction → no shimmer). */
    private void tickHoveringClient() {
        if (lerpHoverSteps <= 0) return;
        double t = 1.0 / lerpHoverSteps;
        setPos(
                Mth.lerp(t, getX(), lerpX),
                Mth.lerp(t, getY(), lerpY),
                Mth.lerp(t, getZ(), lerpZ));
        lerpHoverSteps--;
    }

    private void tickHovering(Player owner) {
        if (quickFireCooldown > 0) quickFireCooldown--;
        if (getGuardCooldown() > 0) setGuardCooldown(getGuardCooldown() - 1);
        updateTargetPosition(owner);
        idle.tick(owner);          // adds idle offset to targetPosition (no-op when not idle)
        applySpringPhysics();
        updateMobAwareness(owner);
        if (this.tickCount % 300 == 0) {
            SwordSounds.playHoverAmbient(this.level(), getX(), getY(), getZ());
        }
    }

    // === BLOCKING ===

        public void startBlocking() {
        setState(FamiliarState.BLOCKING);
        if (getOwner() instanceof ServerPlayer sp) {
            SwordSounds.playGuardRaised(sp);
        }
    }

    public void cancelChargeIntoBlock() {
        removeChargeSlowdown();
        chargeTimer = 0;
        setState(FamiliarState.BLOCKING);
        if (getOwner() instanceof ServerPlayer sp) {
            SwordSounds.playGuardRaised(sp);
        }
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
        if (getOwner() instanceof ServerPlayer sp) {
            SwordSounds.playGuardRaised(sp);
        }
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
            if (!isValidAwarenessTarget(owner, e)) return false;
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
            if (!canDamage(owner, entity)) continue;
            entity.hurt(source, blockSlashDamage());
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
        setGuardCooldown(guardBreakCooldownTicks());
        if (!this.level().isClientSide) {
            SwordSounds.playGuardBreak(this.level(), getX(), getY(), getZ());
        }
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
        if (!isChargeReady() && chargeTimer % 10 == 0) {
            SwordSounds.playChargeLoop(this.level(), getX(), getY(), getZ());
        }

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
        emitVibration(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_SHOOT);
        if (!this.level().isClientSide) {
            SwordSounds.playLaunch(this.level(), getX(), getY(), getZ(), charged);
        }
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
            BlockState hitState = this.level().getBlockState(blockHit.getBlockPos());
            if (!hitState.is(PIERCEABLE_BLOCKS)) {
                // Damage entities along the path travelled up to the wall BEFORE embedding,
                // so an enemy standing right in front of the wall is still struck this tick.
                damageEntitiesInPath(currentPos, blockHit.getLocation(), outboundHitSet,
                        chargedLaunch ? launchDamageCharged() : launchDamageNormal(), owner);
                // Embed in block face
                this.setPos(blockHit.getLocation().subtract(launchDirection.scale(0.1)));
                enterStuck();
                return;
            }
            // Pierceable block — phase through, keep moving
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
                chargedLaunch ? launchDamageCharged() : launchDamageNormal(), owner);
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
            ((net.minecraft.server.level.ServerLevel) this.level()).sendParticles(ParticleTypes.POOF,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 12, 0.2, 0.3, 0.2, 0.02);
            emitVibration(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_LAND);
        }
    }

    private void tickStuck(Player owner) {
        if (recallPending) {
            recallPending = false;
            tetherPending = false;
            enterReturning();
            return;
        }

        if (tetherPending) {
            tetherPending = false;
            enterTether(owner);
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

    // === TETHERING ===

    private void enterTether(Player owner) {
        Vec3 a = owner.position();               // player feet
        Vec3 b = this.position();                // embedded sword
        tetherMidpoint = a.add(b).scale(0.5);
        tetherTimer = 0;
        tetherGeometryTicks = 0;
        tetherLastPos = a;
        setState(FamiliarState.TETHERING);

        // Force-pull: ONE ballistic launch toward the midpoint, applied this tick so it feels
        // instant; gravity then arcs the player the rest of the way (no per-tick reel). Flight time
        // scales with distance (clamped) so horizontal speed feels the same near or far, and vy is
        // SOLVED from that time + gravity so the player lands on the target — which also lifts them
        // off the ground immediately, escaping the ground friction that made a flat pull skid out.
        // hurtMarked syncs the server-set velocity to the client (vanilla knockback path). Fall
        // damage is NOT reset — the player takes it on landing (design L629).
        Vec3 delta = tetherMidpoint.subtract(a);
        double dist = delta.length();
        if (dist > 1.0E-4) {
            double t = Math.min(Math.max(dist / TETHER_PULL_SPEED, TETHER_MIN_FLIGHT_TICKS), TETHER_MAX_FLIGHT_TICKS);
            double vy = delta.y / t + 0.5 * TETHER_GRAVITY * t;
            owner.setDeltaMovement(delta.x / t * TETHER_DRAG_COMP, vy, delta.z / t * TETHER_DRAG_COMP);
            owner.hurtMarked = true;
        }

        if (!this.level().isClientSide) {
            SwordSounds.playTetherStart(this.level(), getX(), getY(), getZ());
            SwordSounds.playTetherStart(this.level(), owner.getX(), owner.getY(), owner.getZ());
        }
    }

    private void tickTethering(Player owner) {
        // Tether Slam: dragging the player into a valid enemy detonates an AoE and aborts the pull.
        if (!this.level().isClientSide) {
            AABB detect = owner.getBoundingBox().inflate(
                    TETHER_SLAM_DETECT_INFLATE, TETHER_SLAM_DETECT_INFLATE, TETHER_SLAM_DETECT_INFLATE);
            boolean contact = !this.level().getEntitiesOfClass(LivingEntity.class, detect,
                    e -> e.isAlive() && e != owner && canDamage(owner, e)).isEmpty();
            if (contact) {
                tetherSlam(owner);
                return; // slam consumed the pull
            }
        }

        tetherTimer++;

        // Pure ballistic monitor — the launch in enterTether did the work; gravity carries the arc.
        // No velocity is set here, so the flight stays smooth instead of stuttering against a
        // per-tick re-aim.

        // Throttled reel-in loop (disabled)

        // Arrival: player within range of the snapshot midpoint.
        if (owner.position().distanceToSqr(tetherMidpoint) <= TETHER_ARRIVAL_RANGE * TETHER_ARRIVAL_RANGE) {
            endTether();
            return;
        }

        // Timeout.
        if (tetherTimer >= TETHER_TIMEOUT_TICKS) {
            endTether();
            return;
        }

        // Geometry block / landed: the player's position has stalled in every axis (slammed into
        // terrain, or the arc has come to rest short of the target). Full-3D delta so a still-rising
        // or still-falling arc isn't cut off mid-flight.
        Vec3 nowPos = owner.position();
        double dx = nowPos.x - tetherLastPos.x;
        double dy = nowPos.y - tetherLastPos.y;
        double dz = nowPos.z - tetherLastPos.z;
        if (dx * dx + dy * dy + dz * dz < TETHER_GEOMETRY_MOVE_SQR) {
            tetherGeometryTicks++;
            if (tetherGeometryTicks >= TETHER_GEOMETRY_BLOCK_TICKS) {
                endTether();
                return;
            }
        } else {
            tetherGeometryTicks = 0;
        }
        tetherLastPos = nowPos;
    }

    /** Single AoE detonation centred on the player; damages, ignites, bloodies, and knocks back. */
    private void tetherSlam(Player owner) {
        Vec3 center = owner.position();
        DamageSource source = this.level().damageSources().playerAttack(owner);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(TETHER_SLAM_RADIUS),
                e -> e.isAlive() && e != owner && canDamage(owner, e))) {
            target.hurt(source, tetherSlamDamage());
            igniteIfUndead(target);
            bloodyOwnerBlade(target);
            // Push away from the player (mirrors doBlockSlashDamage's knockback convention).
            target.knockback(TETHER_SLAM_KNOCKBACK,
                    owner.getX() - target.getX(), owner.getZ() - target.getZ());
        }
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 1.0, center.z, 6, 1.5, 0.4, 1.5, 0.0);
        }
        SwordSounds.playTetherSlam(this.level(), center.x, center.y, center.z);
        endTether();
    }

    private void endTether() {
        if (!this.level().isClientSide) {
            ((net.minecraft.server.level.ServerLevel) this.level()).sendParticles(ParticleTypes.POOF,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 16, 0.25, 0.3, 0.25, 0.03);
        }
        enterReturning();
    }

    public void recall() {
        if (getState() == FamiliarState.LAUNCHING || getState() == FamiliarState.STUCK) {
            this.recallPending = true;
        }
    }

    public void startTether() {
        if (getState() == FamiliarState.STUCK) {
            this.tetherPending = true;
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
            if (owner instanceof ServerPlayer sp) {
                SwordSounds.playReturnArrival(sp);
            }
            return;
        }

        Vec3 direction = toOwner.normalize();
        Vec3 movement = direction.scale(RETURN_SPEED);
        Vec3 nextPos = currentPos.add(movement);

        // Phases through blocks during return
        this.setPos(nextPos);

        // Damage entities on return path
        damageEntitiesInPath(currentPos, nextPos, returnHitSet, returnDamage(), owner);
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

        // Mow qualifying foliage the blade swept through (drops items).
        if (Config.SWEEP_MOWS_PLANTS.get()) {
            mowPlants(prevPos, nextPos, owner);
        }
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
                e -> e.isAlive() && e != owner && e != owner.getVehicle() && !sweepIFrames.containsKey(e.getId()));

        if (entities.isEmpty()) return;

        Vec3 travelDir = this.sweepVelocity.length() > 0.01 ? this.sweepVelocity.normalize() : owner.getLookAngle();
        DamageSource source = this.level().damageSources().playerAttack(owner);

        for (LivingEntity entity : entities) {
            if (!canDamage(owner, entity)) continue;
            entity.hurt(source, sweepContactDamage());
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
        if (!this.level().isClientSide) {
            SwordSounds.playSweepContact(this.level(), getX(), getY(), getZ());
        }
    }

    /** Destroy and drop qualifying foliage in the blade's swept volume (server-side). */
    private void mowPlants(Vec3 from, Vec3 to, Player owner) {
        if (this.level().isClientSide) return;
        AABB box = new AABB(
                Math.min(from.x, to.x) - SWORD_HALF_LENGTH, Math.min(from.y, to.y) - SWORD_HALF_THICKNESS, Math.min(from.z, to.z) - SWORD_HALF_LENGTH,
                Math.max(from.x, to.x) + SWORD_HALF_LENGTH, Math.max(from.y, to.y) + SWORD_HALF_THICKNESS, Math.max(from.z, to.z) + SWORD_HALF_LENGTH);
        Level level = this.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(SWEPT_AWAY_BLOCKS)) {
                level.destroyBlock(pos, true, owner);
            }
        }
    }

    // === SWEEPING_RELEASE ===

    public void releaseSweep() {
        if (this.sweepVelocity.length() < 0.15) {
            enterHoveringFromSweep();
            return;
        }
        if (!this.level().isClientSide) {
            SwordSounds.playSweepRelease(this.level(), this.getX(), this.getY(), this.getZ());
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
            damageEntitiesInPath(currentPos, nextPos, sweepReturnHitSet, sweepReleaseDamage(), owner);
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
        damageEntitiesInPath(currentPos, nextPos, sweepReleaseHitSet, sweepReleaseDamage(), owner, SWORD_HALF_LENGTH);

        // Mow qualifying foliage the flung blade slices through (outbound sawblade only).
        if (Config.SWEEP_MOWS_PLANTS.get()) {
            mowPlants(currentPos, nextPos, owner);
        }
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
        Entity target = this.serverAwarenessTarget; // server's own lock-on; never client-supplied
        if (target == null || !target.isAlive()) return;
        this.entityData.set(DATA_QUICKFIRE_TARGET, target.getId());
        this.quickFireCooldown = quickFireCooldownTicks();
        setState(FamiliarState.QUICK_FIRE);
        emitVibration(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_SHOOT);
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
            if (target == owner.getVehicle()) {
                enterReturning();
                return;
            }
            if (target instanceof LivingEntity living) {
                if (canDamage(owner, living)) {
                    living.hurt(this.level().damageSources().playerAttack(owner), quickFireDamage());
                    igniteIfUndead(living);
                    living.knockback(0.3, this.getX() - living.getX(), this.getZ() - living.getZ());
                }
                bloodyOwnerBlade(living);
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    SwordSounds.playImpact(this.level(), getX(), getY(), getZ());
                    sl.sendParticles(ParticleTypes.CRIT,
                            getX(), getY() + getBbHeight() * 0.5, getZ(), 8, 0.2, 0.2, 0.2, 0.0);
                }
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
            entity.igniteForSeconds(undeadBurnSeconds());
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
    public static boolean canBleed(LivingEntity entity) {
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
            entity.igniteForSeconds(undeadBurnSeconds());
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
                e -> e.isAlive() && e != owner && e != owner.getVehicle() && !hitSet.contains(e.getId()));

        DamageSource source = this.level().damageSources().playerAttack(owner);
        boolean returning = getState() == FamiliarState.RETURNING;
        for (LivingEntity entity : entities) {
            if (!canDamage(owner, entity)) continue;
            hitSet.add(entity.getId());
            entity.hurt(source, damage);
            igniteIfUndead(entity);
            // Return flight passes through mobs without re-bloodying; outbound strike already did.
            if (!returning) bloodyOwnerBlade(entity);
        }
        // End Crystals are plain Entity, not LivingEntity — second pass so the sword can destroy them.
        List<net.minecraft.world.entity.boss.enderdragon.EndCrystal> crystals =
                this.level().getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,
                        sweepBox, e -> e.isAlive() && !hitSet.contains(e.getId()));
        for (var crystal : crystals) {
            hitSet.add(crystal.getId());
            crystal.hurt(source, damage);
        }

        // Boats and Minecarts are Entity, not LivingEntity — third pass so the sword can destroy them like arrows do.
        List<Entity> vehicles = this.level().getEntitiesOfClass(Entity.class, sweepBox,
                e -> (e instanceof net.minecraft.world.entity.vehicle.Boat || e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart)
                        && e.isAlive() && e != owner.getVehicle() && !hitSet.contains(e.getId()));
        DamageSource projectileSource = this.level().damageSources().thrown(this, owner);
        for (Entity vehicle : vehicles) {
            hitSet.add(vehicle.getId());
            vehicle.hurt(projectileSource, 100.0f);
        }

        boolean anyHit = !entities.isEmpty() || !crystals.isEmpty() || !vehicles.isEmpty();
        if (anyHit && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            SwordSounds.playImpact(this.level(), getX(), getY(), getZ());
            sl.sendParticles(ParticleTypes.CRIT,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 8, 0.2, 0.2, 0.2, 0.0);
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

        // Speed cap grows with distance so the sword "tries harder" to catch up. [TUNE]
        // 0.6 at ≤ MAX_LAG_DISTANCE, ramps to 2.5 at ~12 blocks out.
        double maxSpeed = distance > MAX_LAG_DISTANCE
                ? Math.min(0.6 + (distance - MAX_LAG_DISTANCE) * 0.28, 2.5)
                : 0.6;
        if (velocity.length() > maxSpeed) {
            velocity = velocity.normalize().scale(maxSpeed);
        }

        this.setPos(currentPos.add(velocity));
    }

    private void updateMobAwareness(Player owner) {
        // Server-authoritative only. isValidAwarenessTarget() inspects mob.getTarget() /
        // getLastHurtByMob(), which are NOT synced to clients, so a client running this would
        // wrongly find no target for angered neutrals/PvP and clobber the server-synced
        // DATA_AWARENESS_TARGET back to 0 — the "aims for a frame, then drops the lock" bug.
        // The client only ever reads the lock via getAwarenessTarget().
        if (this.level().isClientSide) return;
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
                mob -> isValidAwarenessTarget(owner, mob) && mob.distanceTo(owner) <= MOB_AWARENESS_RADIUS);

        if (hostiles.isEmpty()) {
            this.serverAwarenessTarget = null;
            this.entityData.set(DATA_AWARENESS_TARGET, 0);
        } else {
            hostiles.sort((a, b) -> Double.compare(a.distanceTo(owner), b.distanceTo(owner)));
            this.serverAwarenessTarget = hostiles.get(0);
            this.entityData.set(DATA_AWARENESS_TARGET, this.serverAwarenessTarget.getId());
        }
    }

    @Nullable
    public Entity getAwarenessTarget() {
        if (this.level().isClientSide()) {
            int id = this.entityData.get(DATA_AWARENESS_TARGET);
            return id == 0 ? null : this.level().getEntity(id);
        }
        return this.serverAwarenessTarget;
    }

    // === Idle personality hooks (used by IdlePersonality) ===

    public int getIdleAnim() {
        return this.entityData.get(DATA_IDLE_ANIM);
    }

    public void setIdleAnim(int id) {
        this.entityData.set(DATA_IDLE_ANIM, id);
    }

    public Optional<BlockPos> getCuriosityPos() {
        return this.entityData.get(DATA_CURIOSITY_POS);
    }

    public void setCuriosityPos(Optional<BlockPos> pos) {
        this.entityData.set(DATA_CURIOSITY_POS, pos);
    }

    /** Render-only personality wobble for the current frame (see {@link #idleVisualOffset}). */
    public Vec3 getIdleVisualOffset(float partialTick) {
        return idleVisualOffsetO.add(idleVisualOffset.subtract(idleVisualOffsetO).scale(partialTick));
    }

    /** Fire a one-shot idle reaction clip on the shared "action" controller. */
    public void triggerIdleAnim(String clip) {
        triggerAnim("action", ANIM_PREFIX + clip);
    }

        private void exitFlyingMode(Player owner) {
            if (getState() == FamiliarState.DYING) return;
        
            for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
                var stack = owner.getInventory().getItem(i);
                if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                    HeirloomSwordItem.setMode(stack, SwordMode.NORMAL);
                    // Blood persists across the recall — it is one unified countdown that decays on a
                    // single timeline no matter the mode, so the held/sheathed blade keeps its splatter.
                    stack.remove(ModDataComponents.FAMILIAR_UUID.get());
                    break;
                }
            }
            owner.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("msg.heirloomswordmod.sword_returns"), true);
            triggerAnim("action", ANIM_PREFIX + "death_fall");
            setState(FamiliarState.DYING);
            dyingTimer = 0;
            SwordSounds.playDeathFall(this.level(), getX(), getY(), getZ());
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

        if (isAwakening()) {
            // Ceremonial slow descent.
            Vec3 toTarget = hoverPos.subtract(this.position());
            if (toTarget.length() > AWAKENING_DESCENT_SPEED) {
                this.setPos(this.position().add(toTarget.normalize().scale(AWAKENING_DESCENT_SPEED)));
                return;
            }
            // Settle immediately upon landing. No landing impact.
            this.setPos(hoverPos);
            this.targetPosition = hoverPos;
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            setAwakening(false);
            setState(FamiliarState.HOVERING);
            SwordSounds.playLandingTouchdown(this.level(), this.getX(), this.getY(), this.getZ());
            return;
        }

        // Default fast sky-drop.
        Vec3 toTarget = hoverPos.subtract(this.position());
        if (toTarget.length() <= ARRIVE_SPEED) {
            this.setPos(hoverPos);
            this.targetPosition = hoverPos;
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            setState(FamiliarState.HOVERING);
            SwordSounds.playLandingTouchdown(this.level(), this.getX(), this.getY(), this.getZ());
            // Landing impact: 4 damage + knockback in a 3-block radius (not the owner)
            for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(3.0), e -> e != owner && e.isAlive())) {
                if (!canDamage(owner, target)) continue;
                target.hurt(this.level().damageSources().playerAttack(owner), landingImpactDamage());
                double dx = target.getX() - this.getX();
                double dz = target.getZ() - this.getZ();
                target.knockback(0.5, -dx, -dz);
            }
            return;
        }
        this.setPos(this.position().add(toTarget.normalize().scale(ARRIVE_SPEED)));
    }

    private void tickArrivingClient() {
        if (isAwakening()) {
            tickAwakeningClient();
            return;
        }
        // Falling streak [TUNE density]
        for (int i = 0; i < 2; i++) {
            this.level().addParticle(ParticleTypes.END_ROD,
                    getX() + (this.random.nextDouble() - 0.5) * 0.3,
                    getY() + this.random.nextDouble() * 2.5,
                    getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                    0, 0.1, 0);
        }
    }

    /** Client-only awakening descent VFX: swirling glow + electric crackle + expanding ground rings. */
    private void tickAwakeningClient() {
        // Energy build-up swirling around the descending blade.
        for (int i = 0; i < 3; i++) { // [TUNE]
            double a = this.random.nextDouble() * Math.PI * 2;
            double r = 0.6 + this.random.nextDouble() * 0.4;
            double px = getX() + Math.cos(a) * r;
            double py = getY() + getBbHeight() * 0.5 + (this.random.nextDouble() - 0.5) * 1.5;
            double pz = getZ() + Math.sin(a) * r;
            this.level().addParticle(ParticleTypes.GLOW, px, py, pz, 0, 0.02, 0);
            if (this.random.nextFloat() < 0.4f) {
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz,
                        (getX() - px) * 0.2, 0, (getZ() - pz) * 0.2);
            }
        }
        // Concentric expanding rings on the ground beneath the descent.
        double groundY = findGroundY();
        for (int ring = 0; ring < 2; ring++) { // [TUNE]
            double phase = ((this.tickCount + ring * 7) % 20) / 20.0;
            double radius = 0.5 + phase * 2.5;
            int pts = 24;
            for (int p = 0; p < pts; p++) {
                double ang = (p / (double) pts) * Math.PI * 2;
                this.level().addParticle(p % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.ENCHANT,
                        getX() + Math.cos(ang) * radius, groundY + 0.1, getZ() + Math.sin(ang) * radius,
                        0, 0, 0);
            }
        }
    }

    /** First solid block surface below the entity (client-side, for the ground ring effect). */
    private double findGroundY() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        m.set(this.blockPosition());
        for (int i = 0; i < 24; i++) {
            m.move(net.minecraft.core.Direction.DOWN);
            if (!this.level().getBlockState(m).getCollisionShape(this.level(), m).isEmpty()) {
                return m.getY() + 1.0;
            }
        }
        return getY();
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
        return true;
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
        Player _orientOwner = getOwner();
        boolean _elytra = currentState == FamiliarState.HOVERING && _orientOwner != null && _orientOwner.isFallFlying();
        boolean shouldBeHorizontal = switch (currentState) {
            case HOVERING -> getAwarenessTarget() != null || slashVisualTicks > 0 || _elytra;
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
        } else if (getState() == FamiliarState.HOVERING && getAwarenessTarget() != null) {
            Entity awarenessTarget = getAwarenessTarget();
            Vec3 toTarget = awarenessTarget.position()
                    .add(0, awarenessTarget.getBbHeight() * 0.5, 0)
                    .subtract(this.position());
            targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            double horizDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            targetPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizDist));
        } else if (getState() == FamiliarState.HOVERING) {
            // During elytra flight the sword aims at the player (blade points toward them).
            // In all other hovering situations it mirrors the player's facing.
            Player owner = getOwner();
            if (owner != null && owner.isFallFlying()) {
                Vec3 toOwner = owner.position().add(0, owner.getBbHeight() * 0.5, 0).subtract(this.position());
                if (toOwner.lengthSqr() > 0.01) {
                    targetYaw = (float) Math.toDegrees(Math.atan2(-toOwner.x, toOwner.z));
                    double hd = Math.sqrt(toOwner.x * toOwner.x + toOwner.z * toOwner.z);
                    targetPitch = (float) -Math.toDegrees(Math.atan2(toOwner.y, hd));
                }
            } else if (owner != null) {
                targetYaw = owner.getYRot();
                targetPitch = 0;
            }
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
                        // Spin ramps with charge, but never to 0 — at speed 0 GeckoLib can't
                        // advance the idle->charge_spin transition, so the very first charge
                        // (chargeTimer just reset to 0) renders frozen on idle. Floor it.
                        float t = Math.min(chargeTimer / 15.0f, 1.0f);
                        return (double) Math.max(0.35f, t);
                    }
                    return 1.0;
                }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim(ANIM_PREFIX + "block_slash", RawAnimation.begin().thenPlay(ANIM_PREFIX + "block_slash"))
                .triggerableAnim(ANIM_PREFIX + "guard_break", RawAnimation.begin().thenPlay(ANIM_PREFIX + "guard_break"))
                .triggerableAnim(ANIM_PREFIX + "death_fall", RawAnimation.begin().thenPlay(ANIM_PREFIX + "death_fall"))
                .triggerableAnim(ANIM_PREFIX + "idle_recoil", RawAnimation.begin().thenPlay(ANIM_PREFIX + "idle_recoil"))
                .triggerableAnim(ANIM_PREFIX + "idle_perk", RawAnimation.begin().thenPlay(ANIM_PREFIX + "idle_perk")));
    }

    private PlayState animationPredicate(AnimationState<SwordFamiliarEntity> state) {
        FamiliarState familiarState = getState();
        String anim = "idle";

        switch (familiarState) {
            case HOVERING -> {
                if (getAwarenessTarget() != null) {
                    anim = "alert";
                } else {
                    anim = switch (getIdleAnim()) {
                        case 1 -> "idle_curious";
                        case 2 -> "idle_figure_eight";
                        case 3 -> "block_stance"; // trapped-chest guard beat
                        default -> "idle";
                    };
                }
            }
            case CHARGING -> anim = "charge_spin";
            case LAUNCHING -> anim = "launch";
            case STUCK -> anim = "stuck";
            case RETURNING -> anim = "return";
            case SWEEPING_HOLD -> anim = "sweep_hold";
            case SWEEPING_RELEASE -> anim = sweepReturning ? "return_hilt" : "sweep_hold";
            case BLOCKING -> anim = "block_stance";
            case QUICK_FIRE -> anim = "launch";
            case TETHERING -> anim = "stuck"; // PLACEHOLDER: real `tether_pull` clip + glow is an art/GeckoLib-pass task
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
