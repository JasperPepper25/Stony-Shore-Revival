package com.fineedge.stonyshore.audit;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditDensitySerializationTest {
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void sharedRegistryReferencesRemainCompactAndDecode() {
        var registry=new net.minecraft.core.MappedRegistry<DensityFunction>(net.minecraft.core.registries.Registries.DENSITY_FUNCTION,com.mojang.serialization.Lifecycle.stable());
        var id=new net.minecraft.resources.ResourceLocation("test","shared");
        var shared=registry.register(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DENSITY_FUNCTION,id),DensityFunctions.constant(.4),com.mojang.serialization.Lifecycle.stable());
        var graph=DensityFunctions.add(new DensityFunctions.HolderHolder(shared),new DensityFunctions.HolderHolder(shared));
        var access=new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(java.util.List.of(registry));
        var ops=net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,access);
        var exported=AuditDensitySerialization.expand(new DensityFunctions.HolderHolder(Holder.direct(graph)));
        var encoded=DensityFunction.DIRECT_CODEC.encodeStart(ops,exported).result().orElseThrow();
        assertTrue(encoded.toString().contains("test:shared"));assertTrue(encoded.toString().length()<300);
        var decoded=DensityFunction.DIRECT_CODEC.parse(ops,encoded).result().orElseThrow();
        assertEquals(.8,decoded.compute(new DensityFunction.SinglePointContext(0,70,0)),1e-9);
    }
    @Test void runtimeHolderWrappersExportWithoutChangingLiveGraph() {
        var original=new DensityFunctions.HolderHolder(Holder.direct(new DensityFunctions.HolderHolder(Holder.direct(DensityFunctions.constant(.4)))));
        var exported=AuditDensitySerialization.expand(original);
        var encoded=DensityFunction.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE,exported).result().orElseThrow();
        var decoded=DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE,encoded).result().orElseThrow();
        var point=new DensityFunction.SinglePointContext(0,70,0);
        assertEquals(original.compute(point),decoded.compute(point));
        assertInstanceOf(DensityFunctions.HolderHolder.class,original);
    }
}
