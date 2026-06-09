package com.alucard.heirloomsword;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HeirloomSwordMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SwordFamiliarEntity>> SWORD_FAMILIAR =
            ENTITY_TYPES.register("sword_familiar", () -> EntityType.Builder.<SwordFamiliarEntity>of(
                            SwordFamiliarEntity::new, MobCategory.MISC)
                    .sized(0.4f, 3.0f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .build(HeirloomSwordMod.MODID + ":sword_familiar"));
}
