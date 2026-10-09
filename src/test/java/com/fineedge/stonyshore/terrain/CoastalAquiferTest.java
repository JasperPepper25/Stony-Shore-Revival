package com.fineedge.stonyshore.terrain;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class CoastalAquiferTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static CoastalColumnSampler columns() {
        return new CoastalColumnSampler(42,63,(x,y,z)->(67.5-y)*0.15,(x,z)->true,false);
    }
    private static DensityFunction.SinglePointContext wet(CoastalColumnSampler columns) {
        for(int x=-128;x<128;x+=4) for(int z=-128;z<128;z+=4)
            if(columns.waterCandidate(x,62,z)) return new DensityFunction.SinglePointContext(x,62,z);
        throw new AssertionError("No wet test column");
    }
    private static Aquifer delegate(BlockState state) {
        return new Aquifer() {
            public BlockState computeSubstance(DensityFunction.FunctionContext p, double density) { return state; }
            public boolean shouldScheduleFluidUpdate() { return true; }
        };
    }
    private static CoastalAquifer wrap(BlockState state, CoastalColumnSampler columns, AtomicBoolean failed) {
        return new CoastalAquifer(delegate(state),(x,y,z)->new Aquifer.FluidStatus(63,Blocks.WATER.defaultBlockState()),columns,failed);
    }
    @Test void dryAquiferAndPressureBarrierReceiveWaterOnlyInNewCarving() {
        var columns=columns(); var position=wet(columns);
        for (BlockState state : new BlockState[]{Blocks.AIR.defaultBlockState(), null}) {
            var aquifer=wrap(state,columns,new AtomicBoolean());
            assertTrue(aquifer.computeSubstance(position,-0.2).is(Blocks.WATER));
            assertFalse(aquifer.shouldScheduleFluidUpdate());
            assertSame(state,aquifer.computeSubstance(position,0.2)); // structure beard wins
            assertTrue(aquifer.shouldScheduleFluidUpdate());
            assertSame(state,aquifer.computeSubstance(new DensityFunction.SinglePointContext(position.blockX(),63,position.blockZ()),-1));
        }
        assertEquals(2,columns.waterDecisions());
    }
    @Test void existingFluidsGlobalPickerAndFailureArePreserved() {
        var columns=columns(); var position=wet(columns);
        for (BlockState state : new BlockState[]{Blocks.LAVA.defaultBlockState(),Blocks.WATER.defaultBlockState()}) {
            var aquifer=wrap(state,columns,new AtomicBoolean());
            assertSame(state,aquifer.computeSubstance(position,-1));
            assertTrue(aquifer.shouldScheduleFluidUpdate());
        }
        var air=Blocks.AIR.defaultBlockState();
        assertSame(air,wrap(air,columns,new AtomicBoolean(true)).computeSubstance(position,-1));
        var dryPicker=new CoastalAquifer(delegate(air),(x,y,z)->new Aquifer.FluidStatus(50,Blocks.WATER.defaultBlockState()),columns,new AtomicBoolean());
        assertSame(air,dryPicker.computeSubstance(position,-1));
        assertEquals(0,columns.waterDecisions());
    }
    @Test void elevatedBasinUsesItsOwnWaterPlane() {
        var columns=new CoastalColumnSampler(42,63,(x,y,z)->(110.5-y)*.15,(x,z)->true,false,(x,z)->false,true,false,192);
        DensityFunction.SinglePointContext p=null;
        for(int x=0;x<192 && p==null;x+=4) for(int z=0;z<192;z+=4)
            {int y=columns.column(x,z).waterLevel()-1;
                if(y>63 && columns.waterCandidate(x,y,z)) {p=new DensityFunction.SinglePointContext(x,y,z);break;}}
        assertNotNull(p);
        var aquifer=wrap(Blocks.AIR.defaultBlockState(),columns,new AtomicBoolean());
        assertTrue(aquifer.computeSubstance(p,-1).is(Blocks.WATER));
        assertTrue(aquifer.computeSubstance(new DensityFunction.SinglePointContext(p.blockX(),columns.column(p.blockX(),p.blockZ()).waterLevel(),p.blockZ()),-1).isAir());
        assertTrue(aquifer.computeSubstance(p,1).isAir());
    }
    @Test void pinnedNoiseChunkHasOneAquiferFieldAndMatchingConstructorHook() throws Exception {
        assertEquals(1,Arrays.stream(NoiseChunk.class.getDeclaredFields()).filter(f->f.getType()==Aquifer.class).count());
        var mixin=Class.forName("com.fineedge.stonyshore.mixin.NoiseChunkMixin");
        var hook=Arrays.stream(mixin.getDeclaredMethods()).filter(m->m.getName().equals("stonyshore$attachWater")).findFirst().orElseThrow();
        var parameters=hook.getParameterTypes();
        assertNotNull(NoiseChunk.class.getConstructor(Arrays.copyOf(parameters,parameters.length-1)));
    }
    @Test void nativeShallowPoolsHaveNegativeWaterDensityAndUseTheirElevatedPlane() {
        var columns=new NativeCoastalModel(42,63,192,(x,y,z)->(110-y)*.15,(x,z)->true,(x,z)->false,
            (x,y,z)->true,new CoastalShape.Options(true,true,false,false,0));
        DensityFunction.SinglePointContext point=null;
        outer:for(int x=-160;x<=160;x+=2)for(int z=-160;z<=160;z+=2) {
            var c=columns.detail(x,z);
            if(c.pool()!=null && c.pool().depth()==1 && columns.waterCandidate(x,c.water()-1,z)) {
                point=new DensityFunction.SinglePointContext(x,c.water()-1,z);break outer;
            }
        }
        assertNotNull(point,"native one-block pools retain a readable wet footprint");
        int x=point.blockX(),y=point.blockY(),z=point.blockZ();
        double density=columns.cap((110-y)*.15,x,y,z,.15);
        assertTrue(density<0,"shallow water has a negative density margin rather than a zero crossing");
        var aquifer=wrap(Blocks.AIR.defaultBlockState(),columns,new AtomicBoolean());
        assertTrue(aquifer.computeSubstance(point,density).is(Blocks.WATER));
        assertFalse(aquifer.shouldScheduleFluidUpdate());
        assertTrue(columns.cap((110-(y-1))*.15,x,y-1,z,.15)>0,"solid immediately below the shallow water");
        assertTrue(aquifer.computeSubstance(new DensityFunction.SinglePointContext(x,y+1,z),-1).isAir());
    }
}
