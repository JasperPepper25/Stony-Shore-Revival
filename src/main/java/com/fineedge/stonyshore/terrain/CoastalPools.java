package com.fineedge.stonyshore.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Pools fit existing terraces; they do not manufacture a flat shelf around a basin. */
public final class CoastalPools {
    @FunctionalInterface public interface Height { double sample(int x,int z); }
    private final CoastalTerrainPlanner noise;
    private final int sea;
    private final CoastalShape.GroundSampler ground;
    private final Height surface;
    private final CoastalShape.Solid support;
    private final ConcurrentHashMap<String,LongAdder> counters=new ConcurrentHashMap<>();

    public CoastalPools(CoastalTerrainPlanner noise,int sea,CoastalShape.GroundSampler ground,
                        Height surface,CoastalShape.Solid support) {
        this.noise=noise;this.sea=sea;this.ground=ground;this.surface=surface;this.support=support;
    }

    private void count(String name) { counters.computeIfAbsent(name,k->new LongAdder()).increment(); }
    public Map<String,Long> stats() {
        Map<String,Long> result=new TreeMap<>();counters.forEach((k,v)->result.put(k,v.sum()));return result;
    }

    /** Independent deterministic attempts permit two basins on separate local terraces. */
    public List<CoastalLandforms.Pool> plan(int cx,int cz) {
        if(noise.value(cx,cz,1400)>.92)return List.of();
        List<CoastalLandforms.Pool> pools=new ArrayList<>(2);
        for(int attempt=0;attempt<24 && pools.size()<2;attempt++) {
            int salt=attempt*43;
            int x=cx*48+3+(int)(noise.value(cx,cz,1401+salt)*42);
            int z=cz*48+3+(int)(noise.value(cx,cz,1402+salt)*42);
            var g=ground.sample(x,z);double height=surface.sample(x,z);
            if(g.mask()<.65 || g.inlandDistance()<2 || height<sea+.7 || height>sea+96)continue;
            // Check local terrace shape rather than rejecting every shelf in a cliff region.
            double slope=Math.hypot(surface.sample(x+3,z)-surface.sample(x-3,z),
                surface.sample(x,z+3)-surface.sample(x,z-3))/6;
            if(slope>.45)continue;
            count("poolCandidates");
            double size=noise.value(cx,cz,1403+salt),rx,rz;
            if(size<.4) {rx=3+1.8*noise.value(cx,cz,1404+salt);rz=2.4+1.6*noise.value(cx,cz,1405+salt);}
            else if(size<.8) {rx=5+2.5*noise.value(cx,cz,1404+salt);rz=4+2*noise.value(cx,cz,1405+salt);}
            else {rx=8+3.5*noise.value(cx,cz,1404+salt);rz=6+3.5*noise.value(cx,cz,1405+salt);}
            int depth=1+(int)(noise.value(cx,cz,1407+salt)*(size<.4?2:3));
            int water=Math.max(sea,(int)Math.floor(height-.2));
            var p=new CoastalLandforms.Pool(x,z,rx,rz,noise.value(cx,cz,1406+salt)*Math.PI,
                water,depth,1411+salt);
            boolean overlap=false;
            for(var other:pools)if(Math.hypot(x-other.x(),z-other.z())<p.footprintRadius()+other.footprintRadius()+2)overlap=true;
            if(overlap)continue;
            if(!contained(p)) {count("poolsRejectedTerraceOrSupport");continue;}
            pools.add(p);count("poolsSupported");
            count(size<.4?"smallPoolsSupported":size<.8?"mediumPoolsSupported":"largePoolsSupported");
        }
        return List.copyOf(pools);
    }

    /** The entire wet footprint and a dry enclosing margin must already have support. */
    private boolean contained(CoastalLandforms.Pool p) {
        int extent=(int)Math.ceil(p.footprintRadius()),wet=0,core=0;
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(int dx=-extent;dx<=extent;dx++)for(int dz=-extent;dz<=extent;dz++) {
            int x=p.x()+dx,z=p.z()+dz;double radius=p.radius(x,z,noise);
            if(radius>1.18)continue;
            var g=ground.sample(x,z);double h=surface.sample(x,z);
            if(g.mask()<.55 || g.inlandDistance()<1.5 || h<p.water()-.15)return false;
            low=Math.min(low,h);high=Math.max(high,h);
            // Small natural relief is retained. A shelf with a cliff edge is not excavated flat.
            if(high-low>2.8 || h>p.water()+3.4)return false;
            if(radius<.68)core++;
            if(p.wetAt(x,z,h,noise)) {
                wet++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
            }
            if(radius<1) {
                int floor=(int)Math.floor(p.floor(x,z,h,noise));
                // All three prospective liner blocks must already be solid, preventing cave fill.
                for(int y=floor-2;y<=floor;y++)if(!support.test(x,y,z))return false;
            }
            if(radius>=.82)for(int y=p.water()-3;y<=p.water()-1;y++)if(!support.test(x,y,z))return false;
        }
        // A one-block-deep pool must have a readable wet area before interpolation.
        return wet>=12 && core>=9 && maxX-minX>=3 && maxZ-minZ>=3;
    }
}
