package com.fineedge.stonyshore.terrain;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

public final class CoastalDensityRegistry {
    private static final DeferredRegister<Codec<? extends DensityFunction>> TYPES =
        DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE,"stonyshorerevival");
    static { TYPES.register("coastal_density",()->NativeCoastalDensity.CODEC.codec()); }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private CoastalDensityRegistry() {}
}
