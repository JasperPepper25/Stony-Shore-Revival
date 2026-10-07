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
                         CoastalLandforms.Pool pool,CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang,double featureInfluence) {}
    public record Options(boolean landforms,boolean pools,boolean arches,boolean beaches,double beachFrequency) {}
    @FunctionalInterface public interface GroundSampler { Ground sample(int x,int z); }
    @FunctionalInterface public interface Solid { boolean test(int x,int y,int z); }
    private record Region(CoastalLandforms.Pool shelf,CoastalLandforms.Pool pool,
                          CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang) {}
    private final CoastalTerrainPlanner noise;
    private final GroundSampler ground;
    private final Solid support;
    private final CoastalProfile profiles;
    private final int sea;
    private final Options options;
    private final BoundedCache<Long,Region> rawRegions=new BoundedCache<>(256);
    private final BoundedCache<Long,Region> regions=new BoundedCache<>(256);
    private final BoundedCache<Long,Column> columns=new BoundedCache<>(8192);
    private final ConcurrentHashMap<String,LongAdder> counters=new ConcurrentHashMap<>();
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options) {
        this(seed,sea,ground,options,(x,z)->ground.sample(x,z).height());
    }
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options,CoastalProfile.Heights heights) {
        this(seed,sea,ground,options,heights,(x,y,z)->y<ground.sample(x,z).height()+.5);
    }
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options,CoastalProfile.Heights heights,Solid support) {
        noise=new CoastalTerrainPlanner(seed);this.sea=sea;this.ground=ground;this.options=options;this.support=support;
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
        return options.beaches ? smooth((noise.noise(x,z,176,611)-(0.72-options.beachFrequency*0.48))/0.20) : 0;
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
        // Select a low bench before the material height gate, including beneath tall cliffs.
        double beach=smooth(beachField(x,z)/.65)*(1-smooth((d-26)/16));
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
        if(g.mask<=0)return new Column(g.height,g.height,0,0,sea,null,null,null,0);
        double surface=baseSurface(x,z,g),featureInfluence=0,sand=smooth(beachField(x,z)/.65)*(1-smooth((g.oceanDistance-26)/16))
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
                    featureInfluence=Math.max(featureInfluence,blend);
                }
                if(r.pool!=null && r.pool.radius(x,z,noise)<1.6) {
                    double radius=r.pool.radius(x,z,noise);
                    double rim=smooth((radius-.75)/.25)*(1-smooth((radius-1.2)/.35));
                    surface+=rim*(Math.max(surface,r.pool.water()+1.5)-surface);
                    featureInfluence=Math.max(featureInfluence,rim);
                    surface=r.pool.floor(x,z,surface,noise);
                    if(r.pool.radius(x,z,noise)<1 && surface+0.5<r.pool.water()) {pool=r.pool;water=r.pool.water();featureInfluence=1;}
                }
                if(r.arch!=null) {
                    var a=r.arch;double dx2=x-a.x(),dz2=z-a.z(),cs=Math.cos(a.angle()),sn=Math.sin(a.angle());
                    double u=dx2*cs+dz2*sn,v=-dx2*sn+dz2*cs;
                    double amount=a.reserve(x,z);
                    if(amount>0) {
                        double roof=sea+a.height()+4+2*noise.noise(x,z,11,911);
                        surface+=amount*(Math.max(surface,roof)-surface);arch=a;
                        featureInfluence=Math.max(featureInfluence,amount);
                    }
                    // Open both approaches as part of the same terrain field, avoiding blind tunnels.
                    double approach=(1-smooth((Math.abs(u)-a.width())/4))
                        *smooth((Math.abs(v)-(a.length()-5))/5)
                        *(1-smooth((Math.abs(v)-(a.length()+6))/8));
                    if(approach>0) {
                        surface+=approach*(Math.min(surface,sea+a.height()*0.43-2)-surface);arch=a;
                        featureInfluence=Math.max(featureInfluence,approach);
                    }
                }
                if(r.overhang!=null && r.overhang.reserve(x,z)>0) {
                    overhang=r.overhang;double amount=overhang.reserve(x,z);
                    surface+=amount*(Math.max(surface,overhang.floor()+overhang.height()+3)-surface);
                    featureInfluence=Math.max(featureInfluence,amount);
                    double ox=x-overhang.x(),oz=z-overhang.z();
                    double u=ox*Math.cos(overhang.angle())+oz*Math.sin(overhang.angle());
                    double v=-ox*Math.sin(overhang.angle())+oz*Math.cos(overhang.angle());
                    // Lower the descending approach beyond the roof to open the recess to air.
                    double mouth=(1-smooth((Math.abs(u)-overhang.width()*0.6)/(overhang.width()*0.5)))
                        *smooth((v-overhang.reach()*0.45)/(overhang.reach()*0.3))
                        *(1-smooth((v-(overhang.reach()+5))/8));
                    surface+=mouth*(Math.min(surface,overhang.floor()+1)-surface);
                    featureInfluence=Math.max(featureInfluence,mouth);
                }
            }
        }
        return new Column(g.height,surface,g.mask,sand,water,pool,arch,overhang,featureInfluence);
    }
    private Region rawRegion(int cx,int cz) { return rawRegions.get(key(cx,cz),k->planRegion(cx,cz)); }
    private Region region(int cx,int cz) { return regions.get(key(cx,cz),k->resolveRegion(cx,cz)); }
    private Region resolveRegion(int cx,int cz) {
        Region r=rawRegion(cx,cz);var arch=r.arch;var pool=r.pool;var overhang=r.overhang;
        if(arch==null && pool==null && overhang==null)return r;
        // Resolve whole footprints from raw plans, never recursively requesting resolved neighbors.
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            if(dx==0 && dz==0)continue;
            Region n=rawRegion(cx+dx,cz+dz);
            boolean prior=dx<0 || (dx==0 && dz<0);
            if(arch!=null && n.arch!=null && prior && intersects(arch.x(),arch.z(),archRadius(arch),n.arch.x(),n.arch.z(),archRadius(n.arch)))arch=null;
            if(pool!=null && ((n.arch!=null && intersects(pool.x(),pool.z(),poolRadius(pool),n.arch.x(),n.arch.z(),archRadius(n.arch)))
                || (n.pool!=null && prior && intersects(pool.x(),pool.z(),poolRadius(pool),n.pool.x(),n.pool.z(),poolRadius(n.pool)))))pool=null;
            if(overhang!=null && ((n.arch!=null && intersects(overhang.x(),overhang.z(),overhangRadius(overhang),n.arch.x(),n.arch.z(),archRadius(n.arch)))
                || (n.pool!=null && intersects(overhang.x(),overhang.z(),overhangRadius(overhang),n.pool.x(),n.pool.z(),poolRadius(n.pool)))
                || (n.overhang!=null && prior && intersects(overhang.x(),overhang.z(),overhangRadius(overhang),n.overhang.x(),n.overhang.z(),overhangRadius(n.overhang)))))overhang=null;
        }
        if(arch!=null)count("archesAccepted");
        if(pool!=null) {
            count("shelvesAccepted");count("poolsAccepted");
            count(pool.water()>sea+4?"elevatedPoolsAccepted":"lowPoolsAccepted");
            count(pool.rx()<=4?"smallPoolsAccepted":pool.rx()<=6.5?"mediumPoolsAccepted":"largePoolsAccepted");
        }
        if(overhang!=null)count("overhangsAccepted");
        if((r.pool!=null && pool==null) || (r.arch!=null && arch==null) || (r.overhang!=null && overhang==null))count("landformsRejectedOverlap");
        return new Region(pool==null?null:r.shelf,pool,arch,overhang);
    }
    private static boolean intersects(int x,int z,double radius,int nx,int nz,double other) { return Math.hypot(x-nx,z-nz)<radius+other; }
    private static double poolRadius(CoastalLandforms.Pool p) { return Math.max(p.rx(),p.rz())*1.8+3; }
    private static double archRadius(CoastalLandforms.Arch a) { return Math.max(a.width()+9,a.length()+14); }
    private static double overhangRadius(CoastalLandforms.Overhang a) { return a.width()+a.reach(); }
    private Region planRegion(int cx,int cz) {
        count("regionsPlanned");
        CoastalLandforms.Pool shelf=null,pool=null;
        CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        // Features evaluate their own sites. A failed first location does not discard the region.
        for(int attempt=0;attempt<12;attempt++) {
            int salt=attempt*37;
            int x=cx*48+4+(int)(noise.value(cx,cz,801+salt)*40);
            int z=cz*48+4+(int)(noise.value(cx,cz,831+salt)*40);
            Ground g=ground.sample(x,z);if(g.mask<.4)continue;
            var terrain=profile(x,z);count("profile_"+terrain.type(sea));
            double surface=baseSurface(x,z,g);
            if(arch==null && options.arches && noise.value(cx,cz,900)<.28
                    && terrain.cliffWeight(sea)>.55 && g.oceanDistance<48 && g.height>sea+22) {
                double coastX=ground.sample(x+8,z).oceanDistance-ground.sample(x-8,z).oceanDistance;
                double coastZ=ground.sample(x,z+8).oceanDistance-ground.sample(x,z-8).oceanDistance;
                double angle=Math.hypot(coastX,coastZ)>1?Math.atan2(coastZ,coastX)+Math.PI/2:noise.value(cx,cz,901+salt)*Math.PI;
                for(int turn=0;turn<3 && arch==null;turn++) {
                    count("archCandidates");
                    var candidate=new CoastalLandforms.Arch(x,z,angle+(turn-1)*Math.PI/8,
                        3.5+2.5*noise.value(cx,cz,902+salt),5.5+4*noise.value(cx,cz,903+salt),
                        12+10*noise.value(cx,cz,904+salt),noise.value(cx,cz,905+salt)*2-1,sea);
                    if(supported(candidate)) {arch=candidate;count("archesSupported");}
                    else count("archesRejectedSupport");
                }
            }
            if(pool==null && options.pools && noise.value(cx,cz,1000)<.72 && g.mask>.75
                    && surface>sea+1 && surface<sea+76 && terrain.slope()<.85) {
                count("shelfCandidates");
                double size=noise.value(cx,cz,1003+salt),rx,rz;
                if(size<.6) {rx=2.2+1.8*noise.value(cx,cz,1004+salt);rz=2+1.5*noise.value(cx,cz,1005+salt);}
                else if(size<.9) {rx=4+2.5*noise.value(cx,cz,1004+salt);rz=3.5+2*noise.value(cx,cz,1005+salt);}
                else {rx=7+2*noise.value(cx,cz,1004+salt);rz=5.5+2*noise.value(cx,cz,1005+salt);}
                int water=(int)Math.floor(surface-1);
                if(water<sea)water=sea;
                int depth=1+(int)(noise.value(cx,cz,1008+salt)*(size<.6?2:3));
                double angle=noise.value(cx,cz,1006+salt)*Math.PI;
                var candidate=new CoastalLandforms.Pool(x,z,rx,rz,angle,water,depth,1007+salt);
                if(contained(candidate)) {
                    pool=candidate;
                    shelf=new CoastalLandforms.Pool(x,z,rx+2,rz+2,angle,water,depth,1007+salt);
                    count("poolsSupported");
                } else count("shelvesRejectedSlopeOrBoundary");
            }
            if(overhang==null && terrain.slope()>.12 && terrain.cliffWeight(sea)>.55 && g.height>sea+24
                    && noise.value(cx,cz,1200)<.45) {
                double gx=baseSurface(x+8,z,ground.sample(x+8,z))-baseSurface(x-8,z,ground.sample(x-8,z));
                double gz=baseSurface(x,z+8,ground.sample(x,z+8))-baseSurface(x,z-8,ground.sample(x,z-8));
                double slope=Math.hypot(gx,gz);
                if(slope>4) {
                    double angle=Math.atan2(gz,gx)+Math.PI/2;
                    int ox=(int)Math.round(x-6*gx/slope),oz=(int)Math.round(z-6*gz/slope);
                    Ground outer=ground.sample(ox,oz);int floor=(int)Math.floor(baseSurface(ox,oz,outer)-5);
                    if(outer.mask>.65 && floor>sea+2 && outer.inlandDistance>10) {
                        var candidate=new CoastalLandforms.Overhang(ox,oz,angle,6+4*noise.value(cx,cz,1202+salt),
                            9+6*noise.value(cx,cz,1203+salt),9+6*noise.value(cx,cz,1204+salt),floor);
                        if(overhangFits(candidate)) {overhang=candidate;count("overhangsSupported");}
                        else count("overhangsRejectedSupportOrOverlap");
                    } else count("overhangsRejectedSupportOrOverlap");
                } else count("overhangsRejectedSlope");
            }
        }
        // Region overlap decisions are deterministic and apply to the complete shape.
        if(arch!=null) {shelf=null;pool=null;overhang=null;}
        else if(pool!=null)overhang=null;
        if(arch==null && pool==null && overhang==null)count("regionsWithoutLandforms");
        return new Region(shelf,pool,arch,overhang);
    }
    private boolean contained(CoastalLandforms.Pool pool) {
        int extent=(int)Math.ceil(poolRadius(pool));
        for(int dx=-extent;dx<=extent;dx++)for(int dz=-extent;dz<=extent;dz++) {
            int x=pool.x()+dx,z=pool.z()+dz;double radius=pool.radius(x,z,noise);
            if(radius>1.55)continue;
            Ground g=ground.sample(x,z);double surface=baseSurface(x,z,g);
            if(g.mask<.6 || g.inlandDistance<4 || Math.abs(surface-(pool.water()+1.5))>3.5)return false;
            if(radius<1.2 && !support.test(x,pool.water()-pool.depth()-2,z))return false;
            if(radius>.85 && radius<1.3 && !support.test(x,pool.water()-1,z))return false;
        }
        return true;
    }
    private boolean supported(CoastalLandforms.Arch a) {
        for(int side:new int[]{-1,1})for(int end:new int[]{-1,0,1}) {
            double u=side*(a.width()+3),v=end*a.length()*.7;
            int x=(int)Math.round(a.x()+u*Math.cos(a.angle())-v*Math.sin(a.angle()));
            int z=(int)Math.round(a.z()+u*Math.sin(a.angle())+v*Math.cos(a.angle()));
            Ground g=ground.sample(x,z);
            if(g.mask<.55 || g.height<sea+12 || g.inlandDistance<4 || !support.test(x,sea+5,z))return false;
        }
        for(int end:new int[]{-1,0,1}) {
            Ground g=ground.sample((int)Math.round(a.x()-end*(a.length()+9)*Math.sin(a.angle())),
                (int)Math.round(a.z()+end*(a.length()+9)*Math.cos(a.angle())));
            if(g.mask<.4 || g.inlandDistance<4)return false;
        }
        int roof=sea+(int)Math.ceil(a.height())+3;
        if(roof<ground.sample(a.x(),a.z()).height()-3 && !support.test(a.x(),roof,a.z()))return false;
        return true;
    }
    private boolean overhangFits(CoastalLandforms.Overhang a) {
        if(!support.test(a.x(),a.floor()-2,a.z()))return false;
        int roof=a.floor()+(int)Math.ceil(a.height())+1;
        if(roof<ground.sample(a.x(),a.z()).height()-3 && !support.test(a.x(),roof,a.z()))return false;
        for(int v:new int[]{0,(int)a.reach(),(int)a.reach()+8})for(int side:new int[]{-1,1}) {
            int x=(int)Math.round(a.x()+side*a.width()*Math.cos(a.angle())-v*Math.sin(a.angle()));
            int z=(int)Math.round(a.z()+side*a.width()*Math.sin(a.angle())+v*Math.cos(a.angle()));
            Ground g=ground.sample(x,z);if(g.mask<.4 || g.inlandDistance<4)return false;
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
        double feature=c.featureInfluence;
        if(c.arch!=null)feature=Math.max(feature,smooth(-c.arch.opening(x,y,z)/.18));
        if(c.overhang!=null)feature=Math.max(feature,smooth(-c.overhang.opening(x,y,z)/.18));
        double influence=Math.max(c.mask,feature)*depthFade;
        return original+influence*(sculpt-original);
    }
    public boolean waterCandidate(int x,int y,int z) {
        Column c=column(x,z);
        if((c.pool==null && c.mask<0.999) || y>=c.water || y<c.surface+0.5 || y<sea-3) return false;
        return c.pool!=null || (y<sea && c.arch==null && c.overhang==null);
    }
    static double smooth(double v) { v=Math.max(0,Math.min(1,v));return v*v*v*(v*(v*6-15)+10); }
    static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
}
