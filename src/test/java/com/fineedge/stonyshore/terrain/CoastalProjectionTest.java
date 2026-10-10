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
                if(h.reserve(x,z)>0)assertTrue(Math.hypot(x-h.centerX(),z-h.centerZ())<=h.radius(),
                    "collision bounds include the complete lip and its attachment");
                if(Math.abs(x)>=48 || Math.abs(z)>=48)assertEquals(0,h.reserve(x,z));
            }
        }
    }
    @Test void terminalPierClosesInThreeDimensionsAndRetainsASubstantialFoot() {
        var fin=new CoastalProjection.Fin(0,0,0,44,9,112,35);
        // The full-height outer pier stands beyond the passage, before the terminal cap.
        assertTrue(fin.solid(30,40,0)>0);
        assertTrue(fin.solid(30,105,0)>0);
        double previous=fin.top(33,0);
        for(int x=34;x<=44;x++) {
            assertTrue(fin.top(x,0)<=previous+.01,"crown rounds down toward the end");
            previous=fin.top(x,0);
        }
        assertTrue(fin.top(44,0)<fin.crest()-25,"the endpoint cannot retain a high vertical blade");
        assertTrue(fin.bottom(43,0)>fin.floor()+15,"the lower terminal corner rounds up as well");
        assertTrue(fin.solid(43,106,0)<0);
        assertTrue(fin.solid(43,40,0)<0);
        for(int y=20;y<=130;y++)assertTrue(fin.solid(45,y,0)<0,"body terminates completely beyond its end");
        assertTrue(fin.top(42,3)<fin.top(42,0),"end cap also rounds across its width");
    }
    @Test void roundedTerminalJoinsTheMainPierWithoutAShoulderStep() {
        var fin=new CoastalProjection.Fin(0,0,0,40,9,112,35);
        // The join falls between these adjacent columns. Its shoulders must not
        // suddenly lose a large part of the full-height pier above or below.
        for(int z=-8;z<=8;z+=2) {
            assertTrue(fin.edge(29,z)>0 && fin.edge(30,z)>0);
            assertTrue(Math.abs(fin.top(30,z)-fin.top(29,z))<2,
                "rounded crown joins the pier without a tall vertical shoulder");
            assertTrue(Math.abs(fin.bottom(30,z)-fin.bottom(29,z))<2,
                "rounded foot joins the pier without an abrupt undercut");
        }
    }
    @Test void completeArchesAndLipsRemainVisibleAcrossAnOceanMaskBoundary() {
        CoastalShape.GroundSampler normal=(x,z)->new CoastalShape.Ground(x<0?45:110,
            x>=96?0:x<0?1-CoastalShape.smooth((-x-36)/20.0):CoastalShape.smooth((96-x)/24.0),
            x,Math.max(0,96-x),110,x<0);
        CoastalShape.GroundSampler clipped=(x,z)->{
            var g=normal.sample(x,z);
            return new CoastalShape.Ground(g.height(),x< -8?0:g.mask(),g.oceanDistance(),g.inlandDistance(),g.shoreHeight(),g.ocean());
        };
        var options=new CoastalShape.Options(true,false,true,false,0);
        var full=new CoastalShape(123,63,normal,options);
        var edge=new CoastalShape(123,63,clipped,options);
        var found=arches(full);assertFalse(found.isEmpty());int pastBoundary=0;
        for(var a:found) {
            int x=a.x(),z=a.z(),y=(int)Math.ceil(63+a.height()+3);
            if(x>=-8)continue;
            pastBoundary++;
            assertEquals(a,edge.column(x,z).arch(),"region owner carries the complete plan into adjacent ocean");
            assertEquals(0,edge.column(x,z).mask());
            assertTrue(edge.column(x,z).featureInfluence()>0);
            assertEquals(full.density((45-y)*.15,x,y,z,.15),edge.density((45-y)*.15,x,y,z,.15),1e-9);
            assertTrue(edge.density((45-y)*.15,x,y,z,.15)>0);
        }
        assertTrue(pastBoundary>0);
        var lipOptions=new CoastalShape.Options(true,false,false,false,0);
        var fullLips=new CoastalShape(123,63,normal,lipOptions);
        var edgeLips=new CoastalShape(123,63,clipped,lipOptions);
        Set<CoastalLandforms.Overhang> lips=new HashSet<>();
        for(int x=-32;x<32;x+=2)for(int z=-240;z<=240;z+=4) {
            var lip=fullLips.column(x,z).overhang();if(lip!=null)lips.add(lip);
        }
        int exposedLips=0;
        for(var lip:lips)if(lip.x()< -8) {
            exposedLips++;
            int y=(int)Math.floor(lip.ledge().top(lip.x(),lip.z()))-1;
            assertEquals(lip,edgeLips.column(lip.x(),lip.z()).overhang());
            assertTrue(edgeLips.density((45-y)*.15,lip.x(),y,lip.z(),.15)>0);
        }
        assertTrue(exposedLips>0,"a complete visible lip also crosses the local terrain-mask boundary");
        for(int z=-400;z<=400;z+=16)assertEquals(-.2,edge.density(-.2,-128,85,z,.15),1e-9);
    }
    @Test void boundedPlanLookupIsIndependentOfChunkTraversalDirection() {
        var forward=coast(456,false,true,false);var reverse=coast(456,false,true,false);
        for(int z=240;z>=-240;z-=4)for(int x=64;x>=-64;x-=4)reverse.column(x,z);
        for(int z=-240;z<=240;z+=4)for(int x=-64;x<=64;x+=4) {
            var a=forward.column(x,z);var b=reverse.column(x,z);
            assertEquals(a.arch(),b.arch());assertEquals(a.overhang(),b.overhang());
            for(int y:new int[]{64,82,102})assertEquals(forward.density((x<0?45:110)-y,x,y,z,.15),
                reverse.density((x<0?45:110)-y,x,y,z,.15),1e-9);
        }
    }
    @Test void aCliffLipDoesNotRequireACaveAndAGentleShoreCannotCreateOne() {
        var steep=coast(123,false,false,false);
        var gentle=new CoastalShape(123,63,(x,z)->new CoastalShape.Ground(63+x*.18,1,x,96,90,x<0),
            new CoastalShape.Options(true,false,false,false,0));
        int steepLips=0;
        for(int x=-32;x<=48;x+=4)for(int z=-240;z<=240;z+=4) {
            var lip=steep.column(x,z).overhang();if(lip!=null)steepLips++;
            assertNull(gentle.column(x,z).overhang(),"a small slope is not a cliff drop");
        }
        assertTrue(steepLips>0,"intact high cliff supports visible shelf lips");
    }
}
