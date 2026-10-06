package com.fineedge.stonyshore.terrain;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Pure coastal solid/air model. Plans are bounded, seeded and independent of chunk order. */
public final class CoastalShape {
    public record Ground(double height,double mask,double oceanDistance,double inlandDistance) {
        public Ground(double height,double mask,double oceanDistance) { this(height,mask,oceanDistance,96); }
    }
    public record Column(double original,double surface,double mask,double sand,int water,
                         CoastalLandforms.Pool pool,CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang) {}
    public record Options(boolean landforms,boolean pools,boolean arches,boolean beaches,double beachFrequency) {}
    @FunctionalInterface public interface GroundSampler { Ground sample(int x,int z); }
    private record Region(CoastalLandforms.Pool shelf,CoastalLandforms.Pool pool,
                          CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang) {}
    private final CoastalTerrainPlanner noise;
    private final GroundSampler ground;
    private final CoastalProfile profiles;
    private final int sea;
    private final Options options;
    private final BoundedCache<Long,Region> regions=new BoundedCache<>(256);
    private final BoundedCache<Long,Column> columns=new BoundedCache<>(8192);
    private final ConcurrentHashMap<String,LongAdder> counters=new ConcurrentHashMap<>();
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options) {
        this(seed,sea,ground,options,(x,z)->ground.sample(x,z).height());
    }
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options,CoastalProfile.Heights heights) {
        noise=new CoastalTerrainPlanner(seed);this.sea=sea;this.ground=ground;this.options=options;
        profiles=new CoastalProfile(heights);
    }
    public CoastalProfile.Sample profile(int x,int z) { return profiles.sample(x,z); }
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
        var p=profile(x,z);double cliff=p.cliffWeight(sea),lowWeight=p.lowWeight(sea);
        // Preserve relief inland. Tall coasts retreat only in a narrow strip at the foot.
        double weather=0.78+0.16*noise.noise(x,z,62,601);
        double retained=sea+h*(1-cliff*(1-weather));
        double toe=sea+1.3+Math.min(d,24)*0.055;
        double target=retained+cliff*(1-smooth((d-4)/22))*(Math.min(retained,toe)-retained);
        target+=(noise.noise(x,z,23,607)-0.5)*1.8*(1-cliff)*smooth(h/3);
        double beach=beachField(x,z)*(1-smooth((d-12)/44))*(1-smooth((target-sea-4)/10));
        target+=(sea+0.8+Math.min(d,28)*0.035-target)*beach;
        if(options.pools && lowWeight>0 && target<sea+10) {
            double wx=x+18*(noise.noise(x,z,75,17)-0.5),wz=z+18*(noise.noise(x,z,71,29)-0.5);
            double basin=smooth((.65*noise.noise(wx,wz,25,47)+.35*noise.noise(wx,wz,59,49)-0.48)/0.25);
            double low=sea+1.4+noise.noise(x,z,57,101)-basin*4.2;
            target+=(low-target)*(1-smooth((target-sea-3)/7))*(1-beach*0.7)*p.lowWeight(sea);
        }
        return Math.max(sea-3.5,target);
    }
    private Column planColumn(int x,int z) {
        Ground g=ground.sample(x,z);
        if(g.mask<=0)return new Column(g.height,g.height,0,0,sea,null,null,null);
        double surface=baseSurface(x,z,g),sand=beachField(x,z)*(1-smooth((g.oceanDistance-12)/44))
            *(1-smooth((surface-sea-4)/10))*smooth(g.inlandDistance/24);
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
                        double roof=sea+a.height()+4+2*noise.noise(x,z,11,911);
                        surface+=amount*(Math.max(surface,roof)-surface);arch=a;
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
                    double ox=x-overhang.x(),oz=z-overhang.z();
                    double u=ox*Math.cos(overhang.angle())+oz*Math.sin(overhang.angle());
                    double v=-ox*Math.sin(overhang.angle())+oz*Math.cos(overhang.angle());
                    // Lower the descending approach beyond the roof to open the recess to air.
                    double mouth=(1-smooth((Math.abs(u)-overhang.width()*0.6)/(overhang.width()*0.5)))
                        *smooth((v-overhang.reach()*0.45)/(overhang.reach()*0.3))
                        *(1-smooth((v-(overhang.reach()+5))/8));
                    surface+=mouth*(Math.min(surface,overhang.floor()+1)-surface);
                }
            }
        }
        return new Column(g.height,surface,g.mask,sand,water,pool,arch,overhang);
    }
    private Region region(int cx,int cz) { return regions.get(key(cx,cz),k->planRegion(cx,cz)); }
    private Region planRegion(int cx,int cz) {
        count("regionsPlanned");int x=cx*48+24,z=cz*48+24;double best=-1;
        // Multiple deterministic sites give narrow coastal bands a chance to host a feature.
        for(int attempt=0;attempt<6;attempt++) {
            int px=cx*48+6+(int)(noise.value(cx,cz,801+attempt*19)*36);
            int pz=cz*48+6+(int)(noise.value(cx,cz,831+attempt*19)*36);
            Ground candidate=ground.sample(px,pz);
            if(candidate.mask<=0)continue;
            double score=candidate.mask*(0.6+0.4*profile(px,pz).cliffWeight(sea))
                *(1-.35*smooth((candidate.oceanDistance-24)/64));
            if(score>best){best=score;x=px;z=pz;}
        }
        Ground g=ground.sample(x,z);
        if(g.mask<0.35) {count("regionsRejectedBoundary");return new Region(null,null,null,null);}
        var terrain=profile(x,z);count("profile_"+terrain.type(sea));
        double surface=baseSurface(x,z,g);
        CoastalLandforms.Pool shelf=null,pool=null;CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        if(g.mask>.92 && terrain.relief()<18 && terrain.slope()<.55 && surface>sea+10 && noise.value(cx,cz,1000)<0.45) {
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
                
            } else count("shelvesRejectedSlopeOrBoundary");
        }
        // Select landforms separately, then reject overlapping geometry.
        if(options.arches && terrain.cliffWeight(sea)>.65 && g.oceanDistance<48 && g.height>sea+24 && noise.value(cx,cz,900)<0.16) {
            count("archCandidates");
            double coastX=ground.sample(x+12,z).oceanDistance-ground.sample(x-12,z).oceanDistance;
            double coastZ=ground.sample(x,z+12).oceanDistance-ground.sample(x,z-12).oceanDistance;
            double angle=Math.hypot(coastX,coastZ)>2?Math.atan2(coastZ,coastX)+Math.PI/2:noise.value(cx,cz,901)*Math.PI;
            arch=new CoastalLandforms.Arch(x,z,angle,5+3*noise.value(cx,cz,902),
                10+5*noise.value(cx,cz,903),15+12*noise.value(cx,cz,904),noise.value(cx,cz,905)*2-1,sea);
            if(!supported(arch)) {arch=null;count("archesRejectedSupport");}
            // Keep elevated pools outside the arch mass; the overhang test remains independent.
            if(arch!=null) {
                if(shelf!=null)count("shelvesReplacedByArch");
                shelf=null;pool=null;count("archesAccepted");
            }
        }
        if(terrain.cliffWeight(sea)>.65 && g.height>sea+24 && noise.value(cx,cz,1200)<0.38) {
            double gx=baseSurface(x+12,z,ground.sample(x+12,z))-baseSurface(x-12,z,ground.sample(x-12,z));
            double gz=baseSurface(x,z+12,ground.sample(x,z+12))-baseSurface(x,z-12,ground.sample(x,z-12));
            if(Math.hypot(gx,gz)>5) {
                // Center a recess on the descending cliff face so it opens to exterior air.
                double angle=Math.atan2(gz,gx)+Math.PI/2;
                int ox=(int)Math.round(x-10*gx/Math.hypot(gx,gz)),oz=(int)Math.round(z-10*gz/Math.hypot(gx,gz));
                Ground outer=ground.sample(ox,oz);
                int floor=(int)Math.floor(baseSurface(ox,oz,outer)-4);
                if(outer.mask>0.92 && floor>sea+2 && arch==null && shelf==null) {
                    overhang=new CoastalLandforms.Overhang(ox,oz,angle,7+6*noise.value(cx,cz,1202),
                        11+10*noise.value(cx,cz,1203),8+10*noise.value(cx,cz,1204),floor);count("overhangsAccepted");
                } else count("overhangsRejectedSupportOrOverlap");
            } else count("overhangsRejectedSlope");
        }
        if(shelf!=null)count("shelvesAccepted");if(pool!=null)count("poolsAccepted");
        return new Region(shelf,pool,arch,overhang);
    }
    private boolean supported(CoastalLandforms.Arch a) {
        for(int side:new int[]{-1,1})for(int end:new int[]{-1,0,1}) {
            double u=side*(a.width()+3),v=end*a.length()*.7;
            Ground g=ground.sample((int)Math.round(a.x()+u*Math.cos(a.angle())-v*Math.sin(a.angle())),
                (int)Math.round(a.z()+u*Math.sin(a.angle())+v*Math.cos(a.angle())));
            if(g.mask<.92 || g.height<sea+12)return false;
        }
        for(int end:new int[]{-1,0,1}) {
            Ground g=ground.sample((int)Math.round(a.x()-end*a.length()*Math.sin(a.angle())),
                (int)Math.round(a.z()+end*a.length()*Math.cos(a.angle())));
            if(g.mask<.98)return false;
        }
        return true;
    }
    public double density(double original,int x,int y,int z,double scale) {
        Column c=column(x,z);if(c.mask<=0)return original;
        // Match both the upper surface and the cliff body. Small 3D modulation adds depth.
        double rough=(noise.noise(x+y*0.37,z-y*0.21,19,1701)-0.5)*1.1;
        if(c.pool!=null) rough=0;
        else rough*=smooth((c.surface-sea)/4.0);
        if(c.sand>0.55) rough*=0.12;
        double sculpt=(c.surface+0.5-y+rough)*scale;
        if(c.arch!=null)sculpt=Math.min(sculpt,c.arch.opening(x,y,z)*0.4);
        if(c.overhang!=null)sculpt=Math.min(sculpt,c.overhang.opening(x,y,z)*0.4);
        // Cut existing rock without filling its caves. Add rock above the old surface only,
        // except for a bounded three-block liner immediately beneath a constructed pool.
        boolean poolLiner=c.pool!=null && y<=c.surface+.5 && y>=c.surface-2.5;
        if(!poolLiner && y<c.original-3)sculpt=Math.min(original,sculpt);
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
