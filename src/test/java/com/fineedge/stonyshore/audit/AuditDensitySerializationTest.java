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
