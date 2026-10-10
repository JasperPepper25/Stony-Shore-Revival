package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoastalFeaturePlacementTest {
    @Test void finiteShoreBandsAllowSeaProjectionsWhileInlandRemainsUnchanged() {
        var coast=CoastalProjectionTest.coast(42,true,true,false);
        Set<CoastalLandforms.Arch> arches=new HashSet<>();Set<CoastalLandforms.Pool> pools=new HashSet<>();
        for(int x=-64;x<=112;x+=4)for(int z=-512;z<=512;z+=4) {
            var c=coast.column(x,z);
            if(c.arch()!=null)arches.add(c.arch());if(c.pool()!=null)pools.add(c.pool());
            if(x<=-56 || x>=96)assertEquals(.46,coast.density(.46,x,90,z,.15));
        }
        assertFalse(arches.isEmpty());assertTrue(pools.stream().anyMatch(p->p.rx()<5));
        assertTrue(arches.stream().anyMatch(a->a.x()<0),"opening crosses into ocean without changing biome map");
    }
    @Test void partialCoastalInfluenceHostsPoolsWithoutArtificialEnclosingTerraces() {
        var coast=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(110,.8,32,32),
            new CoastalShape.Options(true,true,false,false,0));
        Set<CoastalLandforms.Pool> found=new HashSet<>();
        for(int x=-96;x<=96;x+=2)for(int z=-96;z<=96;z+=2) {
            var c=coast.column(x,z);if(c.pool()!=null)found.add(c.pool());
        }
        assertFalse(found.isEmpty());
        for(var p:found)for(int dx=-16;dx<=16;dx++)for(int dz=-16;dz<=16;dz++) {
            int x=p.x()+dx,z=p.z()+dz;var c=coast.column(x,z);
            if(c.pool()!=null && c.pool().equals(p) && !coast.waterCandidate(x,p.water()-1,z))
                assertTrue(c.terrainSurface()>p.water()-1.5,"dry rim remains supported");
        }
    }
    @Test void bothLowAndElevatedShelvesProduceSmallMediumAndLargeContainedPools() {
        for(double height:new double[]{66,110}) {
            var coast=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(height,.8,32,32),
                new CoastalShape.Options(true,true,false,false,0));
            Set<CoastalLandforms.Pool> found=new HashSet<>();
            for(int x=-384;x<=384;x+=4)for(int z=-384;z<=384;z+=4) {
                var pool=coast.column(x,z).pool();if(pool!=null)found.add(pool);
            }
            assertTrue(found.stream().anyMatch(p->p.rx()<=4),"small pools must remain possible");
            assertTrue(found.stream().anyMatch(p->p.rx()>4 && p.rx()<=6.5));
            assertTrue(found.stream().anyMatch(p->p.rx()>6.5));
            for(var p:found) {
                if(height>80)assertTrue(p.water()>67);
                assertTrue(coast.density(-.4,p.x(),p.water()-p.depth()-1,p.z(),.15)>0);
                for(int y=p.water()-p.depth();y<p.water();y++) {
                    assertTrue(coast.waterCandidate(p.x(),y,p.z()));
                    assertTrue(coast.density(.46,p.x(),y,p.z(),.15)<=0);
                }
                Set<Long> visited=new HashSet<>();ArrayDeque<int[]> queue=new ArrayDeque<>();queue.add(new int[]{p.x(),p.z()});
                int extent=(int)Math.ceil(Math.max(p.rx(),p.rz())*3+6);
                while(!queue.isEmpty()) {
                    int[] point=queue.remove();int x=point[0],z=point[1];
                    if(!visited.add(CoastalShape.key(x,z)) || coast.density(.46,x,p.water()-1,z,.15)>0)continue;
                    assertTrue(Math.abs(x-p.x())<extent && Math.abs(z-p.z())<extent,"pool water must be enclosed by a solid rim");
                    queue.add(new int[]{x+1,z});queue.add(new int[]{x-1,z});queue.add(new int[]{x,z+1});queue.add(new int[]{x,z-1});
                }
            }
        }
    }
    @Test void featuresRejectMissingRockSupportRatherThanLiningOpenCaves() {
        var coast=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(110,1,24),
            new CoastalShape.Options(true,true,true,false,0),(x,z)->110,(x,y,z)->false);
        for(int x=-192;x<=192;x+=8)for(int z=-192;z<=192;z+=8) {
            var c=coast.column(x,z);assertNull(c.pool());assertNull(c.arch());assertNull(c.overhang());
        }
    }
    @Test void selectedBeachesRetainMainCliffSpaceAndHaveNarrowLowFeet() {
        var coast=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(130,1,4,48),
            new CoastalShape.Options(false,false,false,true,.65));
        int sandy=0;
        for(int x=-384;x<=384;x+=4)for(int z=-384;z<=384;z+=4) {
            var c=coast.column(x,z);if(c.surface()<69 && c.sand()>.6)sandy++;
        }
        assertTrue(sandy>100,"selected beaches provide sandy cliff feet");
        var inland=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(130,1,32,48),
            new CoastalShape.Options(false,false,false,true,.65));
        for(int x=-96;x<=96;x+=8)for(int z=-96;z<=96;z+=8)assertTrue(inland.column(x,z).surface()>125);
    }
}
