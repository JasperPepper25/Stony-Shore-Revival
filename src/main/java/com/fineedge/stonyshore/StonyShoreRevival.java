package com.fineedge.stonyshore;

import com.fineedge.stonyshore.audit.ShoreAuditCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(StonyShoreRevival.ID)
public final class StonyShoreRevival {
    public static final String ID = "stonyshorerevival";
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, ID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SHORE_DETAIL = FEATURES.register(
        "shore_detail", () -> new ShoreDetailFeature(NoneFeatureConfiguration.CODEC));

    public StonyShoreRevival() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        FEATURES.register(bus);
        MinecraftForge.EVENT_BUS.addListener(ShoreAuditCommand::register);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ShoreConfig.SPEC);
    }
}
