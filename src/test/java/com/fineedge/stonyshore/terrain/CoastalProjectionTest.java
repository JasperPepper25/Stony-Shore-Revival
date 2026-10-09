package com.fineedge.stonyshore.terrain;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalProjectionTest {
    static CoastalShape coast(long seed,boolean pools,boolean arches,boolean beaches) {
        return new CoastalShape(seed,63,(x,z)->{
            double mask=x>=96?0:x<0?1-CoastalShape.smooth((-x-36)/20.0):CoastalShape.smooth((96-x)/24.0);
            return new CoastalShape.Ground(x<0?45:110,mask,x,Math.max(0,96-x),110,x<0);
        },new CoastalShape.Options(true,pools,arches,beaches,.65));
    }
    static Set<CoastalLandforms.Arch> arches(CoastalShape c) {
        Set<CoastalLandforms.Arch> found=new HashSet<>();
        for(int x=-48;x<48;x+=4)for(int z=-384;z<=384;z+=4) {
            var a=c.column(x,z).arch();if(a!=null)found.add(a);
        }
        return found;
    }
    @Test void oceanNormalDirectsAttachedFinsAndCrosswiseOpenings() {
        for(long seed:new long[]{42,123,456}) {
            var c=coast(seed,false,true,false);var found=arches(c);
            assertFalse(found.isEmpty(),"eligible cliff with exterior ocean must host arches");
            for(var a:found) {
                var fin=a.fin();assertNotNull(fin);
                assertTrue(Math.cos(fin.direction())<-.95);
                assertTrue(a.x()<fin.rootX());
                int y=(int)(63+a.height()*.43);
                for(int v=-(int)Math.ceil(a.length())-3;v<=(int)Math.ceil(a.length())+3;v++) {
                    int x=(int)Math.round(a.x()-v*Math.sin(a.angle()));
                    int z=(int)Math.round(a.z()+v*Math.cos(a.angle()));
                    assertTrue(c.density((x<0?45:110)-y,x,y,z,.15)<0,"passage and both portals stay open");
                }
                int roof=(int)Math.ceil(63+a.height()+3);
                assertTrue(c.density((45-roof)*.15,a.x(),roof,a.z(),.15)>0);
                int rootY=100;
                assertTrue(c.density((110-rootY)*.15,fin.rootX(),rootY,fin.rootZ(),.15)>0,"attachment retains cliff body");
            }
        }
    }
    @Test void finDoesNotExcavateApproachPitsOutsideItsVolume() {
        var c=coast(123,false,true,true);var found=arches(c);assertFalse(found.isEmpty());
        var bare=new CoastalShape(123,63,(x,z)->{
            double mask=x>=96?0:x<0?1-CoastalShape.smooth((-x-36)/20.0):CoastalShape.smooth((96-x)/24.0);
            return new CoastalShape.Ground(x<0?45:110,mask,x,Math.max(0,96-x),110,x<0);
        },new CoastalShape.Options(false,false,false,true,.65));
        for(var a:found)for(int side:new int[]{-1,1}) {
            int x=(int)Math.round(a.x()-side*Math.sin(a.angle())*(a.length()+6));
            int z=(int)Math.round(a.z()+side*Math.cos(a.angle())*(a.length()+6));
            assertEquals(bare.column(x,z).terrainSurface(),c.column(x,z).terrainSurface(),1e-9);
        }
    }
    @Test void overhangIsAnAdditiveLipWithAnIntactRoot() {
        var c=coast(123,false,false,false);Set<CoastalLandforms.Overhang> found=new HashSet<>();
        for(int x=-32;x<48;x+=2)for(int z=-384;z<=384;z+=4) {
            var h=c.column(x,z).overhang();if(h!=null)found.add(h);
        }
        assertFalse(found.isEmpty());
        for(var h:found) {
            var p=h.ledge();assertTrue(Math.cos(p.direction())<-.95);
            int x=h.x(),z=h.z(),lip=(int)Math.floor(p.top(x,z))-1;
            assertTrue(c.density((45-lip)*.15,x,lip,z,.15)>0,"upper shelf projects over ocean");
            int below=(int)Math.floor(p.underside(x,z))-2;
            assertTrue(c.density((45-below)*.15,x,below,z,.15)<0,"space beneath the projecting shelf");
            int rootY=(int)Math.floor(p.crest()-12);
            assertTrue(c.density((110-rootY)*.15,p.rootX(),rootY,p.rootZ(),.15)>0,"no cave excavation at root");
        }
    }
    @Test void unknownOceanDirectionDoesNotManufactureBlindTunnels() {
        var c=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(110,1,24),new CoastalShape.Options(true,false,true,false,0));
        for(int x=-96;x<=96;x+=8)for(int z=-96;z<=96;z+=8) {
            assertNull(c.column(x,z).arch());assertNull(c.column(x,z).overhang());
        }
    }
    @Test void finAndLipHaveBoundedRoundedFootprintsAtAllRotations() {
        for(int i=0;i<16;i++) {
            double angle=i*Math.PI/8;
            var p=new CoastalProjection.Fin(0,0,angle,46,9.5,110);
            var h=new CoastalProjection.Ledge(0,0,angle,20,18,110);
            for(int x=-80;x<=80;x+=2)for(int z=-80;z<=80;z+=2) {
                if(p.reserve(x,z)>0) {
                    assertTrue(Math.abs(x)<52 && Math.abs(z)<52,"neighbor region lookup contains the complete fin");
                    assertTrue(Math.hypot(x-p.centerX(),z-p.centerZ())<=p.radius(),"collision bounds cover attachment");
                }
                if(Math.abs(x)>=48 || Math.abs(z)>=48)assertEquals(0,h.reserve(x,z));
            }
        }
    }
}
