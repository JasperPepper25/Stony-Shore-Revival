package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoastalFeaturePlacementTest {
    @Test void finiteShoreBandsStillFitArchesAndSmallPoolsWithoutEditingInland() {
        var coast=new CoastalShape(42,63,(x,z)-> {
            double mask=x<0 || x>=80?0:CoastalShape.smooth(Math.min(x,80-x)/24.0);
            return new CoastalShape.Ground(110,mask,Math.max(0,x),Math.max(0,80-x));
        },new CoastalShape.Options(true,true,true,false,0));
        Set<CoastalLandforms.Arch> arches=new HashSet<>();Set<CoastalLandforms.Pool> pools=new HashSet<>();
        for(int x=-16;x<=96;x+=4)for(int z=-512;z<=512;z+=4) {
            var c=coast.column(x,z);
            if(c.arch()!=null)arches.add(c.arch());if(c.pool()!=null)pools.add(c.pool());
            if(x<0 || x>=80)assertEquals(.46,coast.density(.46,x,90,z,.15));
        }
        assertFalse(arches.isEmpty());assertTrue(pools.stream().anyMatch(p->p.rx()<=4));
    }
    @Test void partialCoastalInfluenceCanHostSupportedOpenArches() {
        for(long seed:new long[]{42,123,456}) {
            var coast=new CoastalShape(seed,63,(x,z)->new CoastalShape.Ground(115,.8,24,30),
                new CoastalShape.Options(true,false,true,false,0));
            Set<CoastalLandforms.Arch> found=new HashSet<>();
            for(int x=-192;x<=192;x+=8)for(int z=-192;z<=192;z+=8) {
                var a=coast.column(x,z).arch();if(a!=null)found.add(a);
            }
            assertFalse(found.isEmpty(),"supported partial influence must not reject every arch");
            for(var a:found) {
                int y=(int)(63+a.height()*.43);
                for(int v=-(int)a.length()-3;v<=(int)a.length()+3;v++) {
                    int x=(int)Math.round(a.x()-v*Math.sin(a.angle()));
                    int z=(int)Math.round(a.z()+v*Math.cos(a.angle()));
                    assertTrue(coast.density(.46,x,y,z,.15)<0,"arch must open continuously through both approaches");
                }
                assertTrue(coast.density(.46,a.x(),(int)Math.ceil(63+a.height()+3),a.z(),.15)>0);
            }
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
    @Test void selectedBeachesLowerTallCliffFeetButLeaveDistantCliffsTall() {
        var coast=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(130,1,24,48),
            new CoastalShape.Options(false,false,false,true,.65));
        int broad=0;
        for(int x=-384;x<=384;x+=4)for(int z=-384;z<=384;z+=4) {
            var c=coast.column(x,z);if(c.surface()<69 && c.sand()>.6)broad++;
        }
        assertTrue(broad>200,"beach selection must form low sand benches beneath high cliffs");
        var inland=new CoastalShape(42,63,(x,z)->new CoastalShape.Ground(130,1,64,48),
            new CoastalShape.Options(false,false,false,true,.65));
        for(int x=-96;x<=96;x+=8)for(int z=-96;z<=96;z+=8)assertTrue(inland.column(x,z).surface()>105);
    }
}
