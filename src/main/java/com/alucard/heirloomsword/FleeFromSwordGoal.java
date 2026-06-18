package com.alucard.heirloomsword;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Makes a {@link PathfinderMob} flee from any nearby {@link SwordFamiliarEntity}.
 *
 * <p>A custom goal is required because the sword familiar is a plain {@code Entity}, not a
 * {@code LivingEntity}, so vanilla {@link net.minecraft.world.entity.ai.goal.AvoidEntityGoal}
 * (whose generic is bounded to {@code LivingEntity}) cannot target it.
 *
 * <p>The goal is attached at spawn time to any mob whose type is in the
 * {@link #FLEES_FROM_SWORD} tag (endermen, endermites, creepers by default). Because the sword
 * familiar only exists while flying mode is active, this behaviour is automatically scoped to
 * "while the sword is deployed". Lore: ender-kind recoil from the corrupted ancient blade.
 */
public class FleeFromSwordGoal extends Goal {
    /** Entity types in this tag flee the deployed sword. Populated by a bundled datapack. */
    public static final TagKey<EntityType<?>> FLEES_FROM_SWORD = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "flees_from_sword"));

    private static final double SEARCH_RADIUS = 8.0;
    private static final double WALK_SPEED = 1.0;
    private static final double SPRINT_SPEED = 1.25;
    private static final double SPRINT_WITHIN = 5.0;

    private final PathfinderMob mob;
    private final PathNavigation navigation;
    @Nullable
    private SwordFamiliarEntity toAvoid;
    @Nullable
    private Path path;

    public FleeFromSwordGoal(PathfinderMob mob) {
        this.mob = mob;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        List<SwordFamiliarEntity> nearby = this.mob.level().getEntitiesOfClass(
                SwordFamiliarEntity.class,
                this.mob.getBoundingBox().inflate(SEARCH_RADIUS, 4.0, SEARCH_RADIUS),
                SwordFamiliarEntity::isAlive);
        if (nearby.isEmpty()) {
            return false;
        }

        SwordFamiliarEntity nearest = null;
        double bestSqr = Double.MAX_VALUE;
        for (SwordFamiliarEntity sword : nearby) {
            double d = this.mob.distanceToSqr(sword);
            if (d < bestSqr) {
                bestSqr = d;
                nearest = sword;
            }
        }
        this.toAvoid = nearest;

        Vec3 away = DefaultRandomPos.getPosAway(this.mob, 16, 7, this.toAvoid.position());
        if (away == null) {
            return false;
        }
        // Reject candidate positions that are no further from the sword than we already are.
        if (this.toAvoid.distanceToSqr(away.x, away.y, away.z) < this.toAvoid.distanceToSqr(this.mob)) {
            return false;
        }
        this.path = this.navigation.createPath(away.x, away.y, away.z, 0);
        return this.path != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.navigation.isDone();
    }

    @Override
    public void start() {
        this.navigation.moveTo(this.path, WALK_SPEED);
    }

    @Override
    public void stop() {
        this.toAvoid = null;
        this.path = null;
    }

    @Override
    public void tick() {
        if (this.toAvoid == null) {
            return;
        }
        // Sprint when the sword is breathing down its neck, otherwise a brisk walk.
        double speed = this.mob.distanceToSqr(this.toAvoid) < SPRINT_WITHIN * SPRINT_WITHIN
                ? SPRINT_SPEED
                : WALK_SPEED;
        this.navigation.setSpeedModifier(speed);
    }
}
