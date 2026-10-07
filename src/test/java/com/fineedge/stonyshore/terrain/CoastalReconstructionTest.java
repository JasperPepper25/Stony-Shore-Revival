package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalReconstructionTest {
    @Test void linearAndDiagonalSlopesSurviveEveryGridEdge() {
        var model=new NativeCoastalModel(42,63,256,(x,y,z)->(115+.3*x+.2*z-y)*.15,
            (x,z)->true,(x,z)->false,(x,y,z)->false,new CoastalShape.Options(false,false,false,false,0));
        for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++) {
            assertEquals(114.5+.3*x+.2*z,model.originalHeight(x,z),1e-9);
            assertEquals(.3,model.originalHeight(x+1,z)-model.originalHeight(x,z),1e-9);
            assertEquals(.2,model.originalHeight(x,z+1)-model.originalHeight(x,z),1e-9);
        }
    }
    @Test void steepNodesDoNotOvershootTheirSurfaceRange() {
        for(int i=0;i<=100;i++) {
            double value=CoastalInterpolation.cubic(30,90,110,40,i/100.0);
            assertTrue(value>=90 && value<=110);
        }
    }
    @Test void oceanApronFadesWithoutAnExactShoreGateWhileInlandStaysUnchanged() {
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(100-y)*.15,
            (x,z)->x>=0 && x<96,(x,z)->x<0,(x,y,z)->false,new CoastalShape.Options(false,false,false,false,0));
        assertEquals(0,model.ground(-24,0).mask());
        assertTrue(model.ground(-4,0).mask()>0);
        assertTrue(model.ground(0,0).mask()>.5);
        for(int x=-24;x<12;x++)assertTrue(Math.abs(model.ground(x+1,0).mask()-model.ground(x,0).mask())<.1);
        assertEquals(.2,model.cap(.2,100,90,0,.15));
        assertEquals(.2,model.cap(.2,-24,90,0,.15));
    }
    @Test void profileMedianDoesNotAcquireThirtyTwoBlockBenches() {
        var profile=new CoastalProfile((x,z)->100+.3*x+.2*z);
        for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++) {
            assertEquals(100+.3*x+.2*z,profile.sample(x,z).median(),1e-9);
            assertEquals(Math.hypot(.3,.2),profile.sample(x,z).slope(),1e-9);
        }
    }
}
