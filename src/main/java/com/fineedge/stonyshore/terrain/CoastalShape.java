package com.fineedge.stonyshore.terrain;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Pure coastal solid/air model. Plans are bounded, seeded and independent of chunk order. */
public final class CoastalShape {
    public record Ground(double height,double mask,double oceanDistance) {}
    public record Column(double original,double surface,double mask,double sand,int water,
                         CoastalLandforms.Pool pool,CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang) {}
    public record Options(boolean landforms,boolean pools,boolean arches,boolean beaches,double beachFrequency) {}
    @FunctionalInterface public interface GroundSampler { Ground sample(int x,int z); }
    private record Region(CoastalLandforms.Pool shelf,CoastalLandforms.Pool pool,
                          CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang) {}
    private final CoastalTerrainPlanner noise;
    private final GroundSampler ground;
    private final int sea;
    private final Options options;
    private final BoundedCache<Long,Region> regions=new BoundedCache<>(256);
    private final BoundedCache<Long,Column> columns=new BoundedCache<>(8192);
    private final ConcurrentHashMap<String,LongAdder> counters=new ConcurrentHashMap<>();
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options) {
        noise=new CoastalTerrainPlanner(seed);this.sea=sea;this.ground=ground;this.options=options;
    }
    private void count(String name) { counters.computeIfAbsent(name,k->new LongAdder()).increment(); }
    public Map<String,Long> stats() {
        Map<String,Long> result=new TreeMap<>();counters.forEach((k,v)->result.put(k,v.sum()));
        result.put("regionCacheHits",regions.hits());result.put("regionCacheMisses",regions.misses());
        result.put("columnCacheHits",columns.hits());result.put("columnCacheMisses",columns.misses());return result;
    }
    public Column column(int x,int z) { return columns.get(key(x,z),k->planColumn(x,z)); }
    public double beachField(int x,int z) {
        return options.beaches ? smooth((noise.noise(x,z,176,611)-(0.83-options.beachFrequency*0.5))/0.23) : 0;
    }
    private double baseSurface(int x,int z,Ground g) {
        double h=Math.max(0,g.height-sea),d=g.oceanDistance;
        // Continuous retreat, with retained headlands. No fixed Y bands or huge local cut.
        double retention=0.3+0.65*smooth((noise.noise(x,z,62,601)-0.26)/0.52);
        double profile=sea+h*(0.22+0.78*smooth(d/88))*retention;
        double target=g.height+(profile-g.height)*smooth(h/20);
        double beach=beachField(x,z)*(1-smooth((d-12)/44));
        target+=(sea+0.8+Math.min(d,28)*0.035-target)*beach;
        if(options.pools && target<sea+10) {
            double basin=smooth((noise.noise(x+12*(noise.noise(x,z,75,17)-0.5),z,39,47)-0.42)/0.27);
            double low=sea+1.4+noise.noise(x,z,57,101)-basin*4.2;
            target+=(low-target)*(1-smooth((target-sea-3)/7))*(1-beach*0.7);
        }
        return Math.max(sea-3.5,target);
    }
    private Column planColumn(int x,int z) {
        Ground g=ground.sample(x,z);
        if(g.mask<=0)return new Column(g.height,g.height,0,0,sea,null,null,null);
        double surface=baseSurface(x,z,g),sand=beachField(x,z)*(1-smooth((g.oceanDistance-12)/44));
        CoastalLandforms.Pool pool=null;CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        int water=sea;
        if(options.landforms) {
            int cx=Math.floorDiv(x,48),cz=Math.floorDiv(z,48);
            // Neighbor regions share their entire shapes; nothing is clipped at a cell edge.
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                Region r=region(cx+dx,cz+dz);
                if(r.shelf!=null) {
                    double radius=r.shelf.radius(x,z,noise),blend=1-smooth((radius-0.58)/0.42);
                    // A constructed terrace can raise low spots as well as lower high spots.
                    surface+=blend*(r.shelf.water()+1.5-surface);
                }
                if(r.pool!=null && r.pool.radius(x,z,noise)<1.2) {
                    surface=r.pool.floor(x,z,surface,noise);
                    if(r.pool.radius(x,z,noise)<1 && surface+0.5<r.pool.water()) {pool=r.pool;water=r.pool.water();}
                }
                if(r.arch!=null) {
                    var a=r.arch;double dx2=x-a.x(),dz2=z-a.z(),cs=Math.cos(a.angle()),sn=Math.sin(a.angle());
                    double u=dx2*cs+dz2*sn,v=-dx2*sn+dz2*cs;
                    double amount=a.reserve(x,z);
                    if(amount>0) {
                        surface+=amount*(Math.max(surface,sea+a.height()+6)-surface);arch=a;
                    }
                    // Open both approaches as part of the same terrain field, avoiding blind tunnels.
                    double approach=(1-smooth((Math.abs(u)-a.width())/4))
                        *smooth((Math.abs(v)-(a.length()-5))/5)
                        *(1-smooth((Math.abs(v)-(a.length()+6))/8));
                    if(approach>0)surface+=approach*(Math.min(surface,sea+a.height()*0.43-2)-surface);
                }
                if(r.overhang!=null && r.overhang.reserve(x,z)>0) {
                    overhang=r.overhang;double amount=overhang.reserve(x,z);
                    surface+=amount*(Math.max(surface,overhang.floor()+overhang.height()+3)-surface);
                }
            }
        }
        return new Column(g.height,surface,g.mask,sand,water,pool,arch,overhang);
    }
    private Region region(int cx,int cz) { return regions.get(key(cx,cz),k->planRegion(cx,cz)); }
    private Region planRegion(int cx,int cz) {
        count("regionsPlanned");int x=cx*48+24+(int)(noise.value(cx,cz,801)*12)-6;
        int z=cz*48+24+(int)(noise.value(cx,cz,831)*12)-6;
        Ground g=ground.sample(x,z);
        if(g.mask<0.92) {count("regionsRejectedBoundary");return new Region(null,null,null,null);}
        double surface=baseSurface(x,z,g);
        CoastalLandforms.Pool shelf=null,pool=null;CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        if(surface>sea+10 && noise.value(cx,cz,1000)<0.72) {
            count("shelfCandidates");double rx=11+9*noise.value(cx,cz,1003),rz=9+8*noise.value(cx,cz,1004);
            double angle=noise.value(cx,cz,1005)*Math.PI;
            int water=(int)Math.floor(surface-2-noise.value(cx,cz,1006)*2);
            boolean suitable=true;
            // Bound excavation relative to the surrounding modeled ledge, not the lowest point.
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6,c=Math.cos(angle),s=Math.sin(angle);
                int px=(int)Math.round(x+rx*Math.cos(a)*c-rz*Math.sin(a)*s);
                int pz=(int)Math.round(z+rx*Math.cos(a)*s+rz*Math.sin(a)*c);
                Ground edge=ground.sample(px,pz);double edgeSurface=baseSurface(px,pz,edge);
                if(edge.mask<0.92 || Math.abs(edgeSurface-(water+1.5))>8) {suitable=false;break;}
            }
            if(suitable) {
                shelf=new CoastalLandforms.Pool(x,z,rx,rz,angle,water,3,1007);
                if(options.pools) pool=new CoastalLandforms.Pool(x,z,rx*0.46,rz*0.46,angle,water,2+(int)(3*noise.value(cx,cz,1008)),1007);
                count("shelvesAccepted");if(pool!=null)count("poolsAccepted");
            } else count("shelvesRejectedSlopeOrBoundary");
        }
        // Arches and overhangs have independent chances; a shelf never suppresses them.
        if(options.arches && g.oceanDistance<48 && g.height>sea+24 && noise.value(cx,cz,900)<0.12) {
            arch=new CoastalLandforms.Arch(x,z,noise.value(cx,cz,901)*Math.PI,5+3*noise.value(cx,cz,902),
                10+5*noise.value(cx,cz,903),15+12*noise.value(cx,cz,904),noise.value(cx,cz,905)*2-1,sea);
            // Keep elevated pools outside the arch mass; the overhang test remains independent.
            shelf=null;pool=null;count("archesAccepted");
        }
        if(g.height>sea+24 && noise.value(cx,cz,1200)<0.38) {
            double gx=baseSurface(x+12,z,ground.sample(x+12,z))-baseSurface(x-12,z,ground.sample(x-12,z));
            double gz=baseSurface(x,z+12,ground.sample(x,z+12))-baseSurface(x,z-12,ground.sample(x,z-12));
            if(Math.hypot(gx,gz)>5) {
                // Center a recess on the descending cliff face so it opens to exterior air.
                double angle=Math.atan2(gz,gx)+Math.PI/2;
                int ox=(int)Math.round(x-10*gx/Math.hypot(gx,gz)),oz=(int)Math.round(z-10*gz/Math.hypot(gx,gz));
                Ground outer=ground.sample(ox,oz);
                int floor=(int)Math.floor(baseSurface(ox,oz,outer)-4);
                if(outer.mask>0.92 && floor>sea+2 && arch==null && shelf==null) {
                    overhang=new CoastalLandforms.Overhang(ox,oz,angle,9,16,13,floor);count("overhangsAccepted");
                } else count("overhangsRejectedSupportOrOverlap");
            } else count("overhangsRejectedSlope");
        }
        return new Region(shelf,pool,arch,overhang);
    }
    public double density(double original,int x,int y,int z,double scale) {
        Column c=column(x,z);if(c.mask<=0)return original;
        // Match both the upper surface and the cliff body. Small 3D modulation adds depth.
        double rough=(noise.noise(x+y*0.37,z-y*0.21,19,1701)-0.5)*1.1;
        if(c.pool!=null) rough=0;
        else if(c.sand>0.55) rough*=0.12;
        double sculpt=(c.surface+0.5-y+rough)*scale;
        if(c.arch!=null)sculpt=Math.min(sculpt,c.arch.opening(x,y,z)*0.4);
        if(c.overhang!=null)sculpt=Math.min(sculpt,c.overhang.opening(x,y,z)*0.4);
        double depthFade=smooth((y-(sea-24))/12.0);
        double influence=c.mask*depthFade;
        return original+influence*(sculpt-original);
    }
    public boolean waterCandidate(int x,int y,int z) {
        Column c=column(x,z);
        if(c.mask<0.999 || y>=c.water || y<c.surface+0.5 || y<sea-3) return false;
        return c.pool!=null || (y<sea && c.arch==null && c.overhang==null);
    }
    static double smooth(double v) { v=Math.max(0,Math.min(1,v));return v*v*v*(v*(v*6-15)+10); }
    static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
}
