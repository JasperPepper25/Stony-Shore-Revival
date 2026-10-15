package com.fineedge.stonyshore.terrain;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions for the detached skins exposed by the V10 loaded-block audit. */
class CoastalDensityCompositionTest {
    @Test void aSmoothedHeightEstimateCannotMakeAStoneSkinOverExteriorAir() {
        // The audit measured this condition at (-7212,-12911): the smoothed
        // estimate was 82.82, while the actual upstream cliff-side field was air.
        var shape=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(82.82,1,8),
            new CoastalShape.Options(false,false,false,false,0));
        for(int y=64;y<=85;y++) {
            double exterior=-.15*(y-45);
            assertTrue(shape.density(exterior,20,y,20,.15)<0,
                "ordinary shaping must never fill a detached band at the estimated surface");
        }
    }
    private static CoastalShape cliff(boolean arches) {
        return new CoastalShape(123,63,(x,z)->new CoastalShape.Ground(x<0?45:110,
            x>=96?0:x<0?1-CoastalShape.smooth((-x-36)/20.0):CoastalShape.smooth((96-x)/24.0),
            x,Math.max(0,96-x),110,x<0),
            new CoastalShape.Options(true,false,arches,false,0));
    }
    @Test void anAcceptedFinHasACompleteInteriorBelowTheEstimatedSurface() {
        var shape=cliff(true);Set<CoastalLandforms.Arch> found=new HashSet<>();
        for(int x=-48;x<48;x+=4)for(int z=-384;z<=384;z+=4) {
            var arch=shape.column(x,z).arch();if(arch!=null)found.add(arch);
        }
        assertFalse(found.isEmpty());int checked=0;
        for(var arch:found) {
            var fin=arch.fin();int x=fin.rootX(),z=fin.rootZ(),y=90;
            if(fin.solid(x,y,z)<=0 || arch.opening(x,y,z)<0)continue;
            assertEquals(arch,shape.column(x,z).arch());
            assertTrue(y<shape.column(x,z).original()-3);
            assertTrue(shape.density(-.2,x,y,z,.15)>0,
                "an explicitly accepted fin cannot be sliced by the estimated original height");
            checked++;
        }
        assertTrue(checked>0);
    }
    @Test void anAcceptedLipHasACompleteInteriorBelowTheEstimatedSurface() {
        var shape=cliff(false);Set<CoastalLandforms.Overhang> found=new HashSet<>();
        for(int x=-32;x<48;x+=2)for(int z=-384;z<=384;z+=4) {
            var lip=shape.column(x,z).overhang();if(lip!=null)found.add(lip);
        }
        assertFalse(found.isEmpty());int checked=0;
        for(var lip:found) {
            var ledge=lip.ledge();int x=ledge.rootX(),z=ledge.rootZ();
            int y=(int)Math.ceil(ledge.underside(x,z))+2;
            if(ledge.solid(x,y,z)<=0)continue;
            assertEquals(lip,shape.column(x,z).overhang());
            assertTrue(y<shape.column(x,z).original()-3);
            assertTrue(shape.density(-.2,x,y,z,.15)>0,
                "an explicitly accepted lip cannot be sliced by the estimated original height");
            checked++;
        }
        assertTrue(checked>0);
    }
    @Test void ordinaryShapingPreservesShallowAndDeepCaves() {
        var shape=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(110,1,24),
            new CoastalShape.Options(false,false,false,false,0));
        for(int y:new int[]{39,64,90,108})
            assertEquals(-.2,shape.density(-.2,20,y,20,.15),1e-9);
    }
    @Test void aRaisedSandyApronFillsContinuouslyFromASolidBoundedFoundation() {
        var shape=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(45,1,-12,96,110,true),
            new CoastalShape.Options(false,false,false,true,1));
        int x=0,z=0;CoastalShape.Column chosen=null;
        outer:for(int px=-48;px<=48;px+=4)for(int pz=-96;pz<=96;pz+=4) {
            var c=shape.column(px,pz);
            if(c.sand()>.5 && c.terrainSurface()>45 && c.terrainSurface()<=69) {
                chosen=c;x=px;z=pz;break outer;
            }
        }
        assertNotNull(chosen);
        for(int y=31;y<=Math.floor(chosen.terrainSurface());y++)
            assertTrue(shape.density((45-y)*.15,x,y,z,.15)>0,
                "a raised shallow apron must connect to its verified solid foundation");
        assertEquals(-.2,shape.density(-.2,x,29,z,.15),1e-9,
            "the apron cannot fill caves below its finite sediment depth");
    }
    @Test void aRaisedSandyApronCannotFloatOverAnUnsupportedFoundation() {
        var shape=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(45,1,-12,96,110,true),
            new CoastalShape.Options(false,false,false,true,1),(x,z)->45,(x,y,z)->false);
        int checked=0;
        for(int x=-48;x<=48;x+=4)for(int z=-96;z<=96;z+=4) {
            var c=shape.column(x,z);
            if(c.sand()<=.5 || c.terrainSurface()<=45 || c.terrainSurface()>69)continue;
            assertTrue(shape.density(-.2,x,50,z,.15)<0,
                "an estimated seabed without a solid foundation is not permission to build a skin");
            checked++;
        }
        assertTrue(checked>0);
    }
}
