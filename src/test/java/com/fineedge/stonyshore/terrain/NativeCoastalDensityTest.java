package com.fineedge.stonyshore.terrain;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class NativeCoastalDensityTest {
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static NativeCoastalModel model(long seed,double height) {
        return new NativeCoastalModel(seed,63,192,(x,y,z)->((x>=0?height:45)-y)*.15,(x,z)->x>=0,(x,z)->x<0,
            (x,y,z)->x>=0,new CoastalShape.Options(false,true,false,false,0));
    }
    @Test void codecRoundTripsWithoutCapturingRuntimeContext() {
        var node=new NativeCoastalDensity(DensityFunctions.constant(.2),.15);
        var json=NativeCoastalDensity.CODEC.codec().encodeStart(JsonOps.INSTANCE,node).result().orElseThrow();
        var decoded=NativeCoastalDensity.CODEC.codec().parse(JsonOps.INSTANCE,json).result().orElseThrow();
        assertEquals(.15,decoded.scale());assertEquals(.2,decoded.compute(new DensityFunction.SinglePointContext(0,70,0)));
    }
    @Test void seededCopiesAreIndependentAndChunkCopiesShareBoundContext() {
        var template=new NativeCoastalDensity(DensityFunctions.constant(.2),.15);
        var a=(NativeCoastalDensity)template.mapAll(df->df);var b=(NativeCoastalDensity)template.mapAll(df->df);
        var ma=model(42,150);var mb=model(42,90);
        assertTrue(ma.ground(24,24).mask()>.5,"high fixture has an active physical coastal corridor");
        assertTrue(mb.ground(24,24).mask()>.5,"low fixture has an active physical coastal corridor");
        a.bind(ma,new AtomicBoolean());b.bind(mb,new AtomicBoolean());
        var point=new DensityFunction.SinglePointContext(24,90,24);
        assertEquals(ma.cap(.2,24,90,24,.15),a.compute(point),1e-9);
        assertEquals(mb.cap(.2,24,90,24,.15),b.compute(point),1e-9);
        assertNotEquals(a.compute(point),b.compute(point));
        assertEquals(.2,template.compute(point));
        var chunkCopy=(NativeCoastalDensity)a.mapAll(df->df);assertEquals(a.compute(point),chunkCopy.compute(point));
    }
    @Test void samplerFailuresReturnOriginalDensityForTheEntireContext() {
        var broken=new NativeCoastalModel(42,63,192,(x,y,z)->Double.NaN,(x,z)->true,(x,z)->false,
            (x,y,z)->true,new CoastalShape.Options(false,true,false,false,0));
        var failed=new AtomicBoolean();var node=new NativeCoastalDensity(DensityFunctions.constant(.2),.15);node.bind(broken,failed);
        assertEquals(.2,node.compute(new DensityFunction.SinglePointContext(0,70,0)));assertTrue(failed.get());
    }
}
