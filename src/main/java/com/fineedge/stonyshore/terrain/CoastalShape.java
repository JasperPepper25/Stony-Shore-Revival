package com.fineedge.stonyshore.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Pure coastal solid/air model. Plans are bounded, seeded and independent of chunk order. */
public final class CoastalShape {
    public record Ground(double height,double mask,double oceanDistance,double inlandDistance,double shoreHeight,boolean ocean) {
        public Ground(double height,double mask,double oceanDistance,double inlandDistance) {
            this(height,mask,oceanDistance,inlandDistance,height,oceanDistance<0);
        }
        public Ground(double height,double mask,double oceanDistance) { this(height,mask,oceanDistance,96); }
    }
    public record Column(double original,double surface,double mask,double sand,int water,
                         CoastalLandforms.Pool pool,CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang,
                         double featureInfluence,double terrainSurface) {}
    public record Options(boolean landforms,boolean pools,boolean arches,boolean beaches,double beachFrequency) {}
    @FunctionalInterface public interface GroundSampler { Ground sample(int x,int z); }
    @FunctionalInterface public interface Solid { boolean test(int x,int y,int z); }
    private record Region(List<CoastalLandforms.Pool> pools,CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang) {}
    private record Facing(double angle) {
        int x(int root,double distance) { return (int)Math.round(root+Math.cos(angle)*distance); }
        int z(int root,double distance) { return (int)Math.round(root+Math.sin(angle)*distance); }
    }
    private final CoastalTerrainPlanner noise;
    private final GroundSampler ground;
    private final Solid support;
    private final CoastalProfile profiles;
    private final CoastalBeachProfile beaches;
    private final CoastalPools pools;
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
        profiles=new CoastalProfile(heights);beaches=new CoastalBeachProfile(sea,noise,options);
        pools=new CoastalPools(noise,sea,ground,this::poolSurface,support);
    }
    public CoastalProfile.Sample profile(int x,int z) { return profiles.sample(x,z); }
    private void count(String name) { counters.computeIfAbsent(name,k->new LongAdder()).increment(); }
    public Map<String,Long> stats() {
        Map<String,Long> result=new TreeMap<>();counters.forEach((k,v)->result.put(k,v.sum()));
        pools.stats().forEach((k,v)->result.merge(k,v,Long::sum));
        result.put("regionCacheHits",regions.hits());result.put("regionCacheMisses",regions.misses());
        result.put("columnCacheHits",columns.hits());result.put("columnCacheMisses",columns.misses());return result;
    }
    public Column column(int x,int z) { return columns.get(key(x,z),k->planColumn(x,z)); }
    public double beachField(int x,int z) {
        return beaches.field(x,z);
    }
    private double baseSurface(int x,int z,Ground g) { return beaches.sample(x,z,g).surface(); }
    private double poolSurface(int x,int z) {
        Ground g=ground.sample(x,z);
        return g.height+g.mask*(baseSurface(x,z,g)-g.height);
    }
    private Column planColumn(int x,int z) {
        Ground g=ground.sample(x,z);
        if(g.mask<=0)return new Column(g.height,g.height,0,0,sea,null,null,null,0,g.height);
        var beach=beaches.sample(x,z,g);
        double surface=beach.surface(),featureInfluence=0,sand=beach.sand();
        CoastalLandforms.Pool pool=null;CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        int water=sea;
        if(options.landforms) {
            int cx=Math.floorDiv(x,48),cz=Math.floorDiv(z,48);
            // Plans travel with their complete volumes across both chunks and region boundaries.
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                Region r=region(cx+dx,cz+dz);
                for(var p:r.pools) {
                    double amount=p.influence(x,z,noise);
                    if(amount>0) {
                        surface=p.floor(x,z,poolSurface(x,z),noise);pool=p;water=p.water();
                        featureInfluence=Math.max(featureInfluence,amount);
                        // Cliff vegetation may remain on shelves, but sand never caps a pool liner.
                        sand=0;
                    }
                }
                if(r.arch!=null && r.arch.reserve(x,z)>0) {
                    arch=r.arch;featureInfluence=Math.max(featureInfluence,arch.reserve(x,z));sand=0;
                }
                if(r.overhang!=null && r.overhang.reserve(x,z)>0) {
                    overhang=r.overhang;featureInfluence=Math.max(featureInfluence,overhang.reserve(x,z));sand=0;
                }
            }
        }
        double terrainSurface=surface;
        if(arch!=null)surface=Math.max(surface,arch.fin().top(x,z));
        if(overhang!=null)surface=Math.max(surface,overhang.ledge().top(x,z));
        return new Column(g.height,surface,g.mask,sand,water,pool,arch,overhang,featureInfluence,terrainSurface);
    }
    private Region rawRegion(int cx,int cz) { return rawRegions.get(key(cx,cz),k->planRegion(cx,cz)); }
    private Region region(int cx,int cz) { return regions.get(key(cx,cz),k->resolveRegion(cx,cz)); }
    private Region resolveRegion(int cx,int cz) {
        Region r=rawRegion(cx,cz);var arch=r.arch;var overhang=r.overhang;
        var accepted=new ArrayList<>(r.pools);
        if(arch==null && accepted.isEmpty() && overhang==null)return r;
        // Whole-footprint arbitration is based on raw plans and never recurses into neighbors.
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            if(dx==0 && dz==0)continue;
            Region n=rawRegion(cx+dx,cz+dz);boolean prior=dx<0 || (dx==0 && dz<0);
            if(arch!=null && n.arch!=null && prior && intersects(arch,n.arch))arch=null;
            var neighborArch=n.arch;
            accepted.removeIf(p->neighborArch!=null && intersects(p,neighborArch));
            if(prior)for(var other:n.pools)accepted.removeIf(p->intersects(p,other));
            if(overhang!=null && n.arch!=null && intersects(overhang,n.arch))overhang=null;
            if(overhang!=null && prior && n.overhang!=null && intersects(overhang,n.overhang))overhang=null;
            if(overhang!=null)for(var other:n.pools)if(intersects(other,overhang)) {overhang=null;break;}
        }
        if(arch!=null)count("archesAccepted");
        for(var p:accepted) {
            count("poolsAccepted");count(p.water()>sea+4?"elevatedPoolsAccepted":"lowPoolsAccepted");
            count(p.rx()<5?"smallPoolsAccepted":p.rx()<8?"mediumPoolsAccepted":"largePoolsAccepted");
        }
        if(overhang!=null)count("overhangsAccepted");
        if(accepted.size()<r.pools.size() || arch!=r.arch || overhang!=r.overhang)count("landformsRejectedOverlap");
        return new Region(List.copyOf(accepted),arch,overhang);
    }
    private static boolean near(double x,double z,double r,double nx,double nz,double nr) { return Math.hypot(x-nx,z-nz)<r+nr; }
    private static boolean intersects(CoastalLandforms.Pool a,CoastalLandforms.Pool b) { return near(a.x(),a.z(),a.footprintRadius(),b.x(),b.z(),b.footprintRadius()); }
    private static boolean intersects(CoastalLandforms.Arch a,CoastalLandforms.Arch b) {
        return near(a.fin().centerX(),a.fin().centerZ(),a.fin().radius(),b.fin().centerX(),b.fin().centerZ(),b.fin().radius());
    }
    private static boolean intersects(CoastalLandforms.Pool p,CoastalLandforms.Arch a) {
        return near(p.x(),p.z(),p.footprintRadius(),a.fin().centerX(),a.fin().centerZ(),a.fin().radius());
    }
    private static boolean intersects(CoastalLandforms.Overhang h,CoastalLandforms.Arch a) { return near(h.x(),h.z(),h.ledge().radius(),a.fin().centerX(),a.fin().centerZ(),a.fin().radius()); }
    private static boolean intersects(CoastalLandforms.Overhang a,CoastalLandforms.Overhang b) { return near(a.x(),a.z(),a.ledge().radius(),b.x(),b.z(),b.ledge().radius()); }
    private static boolean intersects(CoastalLandforms.Pool p,CoastalLandforms.Overhang h) { return near(p.x(),p.z(),p.footprintRadius(),h.x(),h.z(),h.ledge().radius()); }
    private Facing oceanFacing(int x,int z) {
        double gx=ground.sample(x+12,z).oceanDistance-ground.sample(x-12,z).oceanDistance;
        double gz=ground.sample(x,z+12).oceanDistance-ground.sample(x,z-12).oceanDistance;
        if(Math.hypot(gx,gz)<2)return null;
        Facing f=new Facing(Math.atan2(-gz,-gx));
        Ground here=ground.sample(x,z),outside=ground.sample(f.x(x,24),f.z(z,24));
        return outside.oceanDistance<here.oceanDistance-8?f:null;
    }
    private Region planRegion(int cx,int cz) {
        count("regionsPlanned");
        CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        for(int attempt=0;attempt<16;attempt++) {
            int salt=attempt*37;
            int x=cx*48+3+(int)(noise.value(cx,cz,801+salt)*42),z=cz*48+3+(int)(noise.value(cx,cz,831+salt)*42);
            Ground g=ground.sample(x,z);
            if(g.mask<.5 || g.ocean || g.oceanDistance<6 || g.oceanDistance>28 || g.inlandDistance<8 || g.height<sea+30)continue;
            Facing facing=oceanFacing(x,z);if(facing==null)continue;
            if(arch==null && options.arches && noise.value(cx,cz,900)<.32) {
                count("archCandidates");
                double reach=34+12*noise.value(cx,cz,902+salt),thickness=6.5+3*noise.value(cx,cz,903+salt);
                double openingHeight=Math.min(38,Math.max(22,(g.height-sea)*.55));
                double crest=Math.max(sea+openingHeight+14,Math.min(g.height+2,sea+82));
                var fin=new CoastalProjection.Fin(x,z,facing.angle,reach,thickness,crest);
                int ax=facing.x(x,reach*.53),az=facing.z(z,reach*.53);
                var candidate=new CoastalLandforms.Arch(ax,az,facing.angle,8+4*noise.value(cx,cz,904+salt),
                    thickness+2,openingHeight,noise.value(cx,cz,905+salt)*1.4-.7,sea,fin);
                if(attachedArch(candidate)) {arch=candidate;count("archesSupported");}
                else count("archesRejectedAttachmentOrPortals");
            }
            if(overhang==null && noise.value(cx,cz,1200)<.5) {
                double reach=12+8*noise.value(cx,cz,1202+salt),width=10+8*noise.value(cx,cz,1203+salt);
                double crest=g.height-1;
                var ledge=new CoastalProjection.Ledge(x,z,facing.angle,reach,width,crest);
                int hx=facing.x(x,reach*.6),hz=facing.z(z,reach*.6);
                var candidate=new CoastalLandforms.Overhang(hx,hz,facing.angle-Math.PI/2,width,reach,12,(int)Math.floor(crest-13),ledge);
                if(attachedLedge(candidate)) {overhang=candidate;count("overhangsSupported");}
                else count("overhangsRejectedAttachmentOrFacing");
            }
        }
        List<CoastalLandforms.Pool> candidates=options.pools?pools.plan(cx,cz):List.of();
        List<CoastalLandforms.Pool> local=new ArrayList<>();
        for(var p:candidates)if(arch==null || !intersects(p,arch))local.add(p);
        if(overhang!=null && arch!=null && intersects(overhang,arch))overhang=null;
        if(overhang!=null)for(var p:local)if(intersects(p,overhang)) {overhang=null;break;}
        if(arch==null && overhang==null && local.isEmpty())count("regionsWithoutLandforms");
        return new Region(List.copyOf(local),arch,overhang);
    }
    private boolean attachedArch(CoastalLandforms.Arch a) {
        var fin=a.fin();
        int rootY=(int)Math.floor(Math.min(ground.sample(fin.rootX(),fin.rootZ()).height()-4,fin.crest()-8));
        for(int side=-1;side<=1;side++) {
            int x=(int)Math.round(fin.rootX()-Math.sin(fin.direction())*side*fin.thickness()*.5);
            int z=(int)Math.round(fin.rootZ()+Math.cos(fin.direction())*side*fin.thickness()*.5);
            Ground root=ground.sample(x,z);
            if(root.mask<.45 || baseSurface(x,z,root)<rootY+3 || !support.test(x,rootY,z))return false;
        }
        int tipX=(int)Math.round(fin.rootX()+Math.cos(fin.direction())*(fin.reach()-4));
        int tipZ=(int)Math.round(fin.rootZ()+Math.sin(fin.direction())*(fin.reach()-4));
        Ground tip=ground.sample(tipX,tipZ);
        if(tip.mask<=0 || tip.inlandDistance<4 || tip.oceanDistance>2)return false;
        // The crosswise passage must already face exterior space: never excavate approaches.
        int openingY=(int)(sea+a.height()*.43);
        for(int sign:new int[]{-1,1}) {
            int x=(int)Math.round(a.x()-sign*Math.sin(a.angle())*(a.length()+3));
            int z=(int)Math.round(a.z()+sign*Math.cos(a.angle())*(a.length()+3));
            Ground g=ground.sample(x,z);
            if(g.mask<=0 || g.inlandDistance<4 || baseSurface(x,z,g)>openingY-3)return false;
        }
        return true;
    }
    private boolean attachedLedge(CoastalLandforms.Overhang a) {
        var p=a.ledge();int rootY=(int)Math.floor(p.crest()-5);
        if(baseSurface(p.rootX(),p.rootZ(),ground.sample(p.rootX(),p.rootZ()))<rootY+3
            || !support.test(p.rootX(),rootY,p.rootZ()))return false;
        for(int side:new int[]{-1,1}) {
            int x=(int)Math.round(p.rootX()-Math.sin(p.direction())*side*p.width()*.5);
            int z=(int)Math.round(p.rootZ()+Math.cos(p.direction())*side*p.width()*.5);
            Ground root=ground.sample(x,z);
            if(root.mask<.45 || baseSurface(x,z,root)<rootY+1 || !support.test(x,rootY-2,z))return false;
        }
        Ground outer=ground.sample(a.x(),a.z());
        return outer.mask>.2 && outer.inlandDistance>4 && outer.oceanDistance<ground.sample(p.rootX(),p.rootZ()).oceanDistance-5
            && baseSurface(a.x(),a.z(),outer)<p.underside(a.x(),a.z())-3;
    }
    public double density(double original,int x,int y,int z,double scale) {
        Column c=column(x,z);if(c.mask<=0)return original;
        double rough=(noise.noise(x+y*.37,z-y*.21,27,1701)-.5)*.7;
        if(c.pool!=null || c.sand>.5)rough=0;
        double sculpt=(c.terrainSurface+.5-y+rough)*scale;
        // Ordinary shaping preserves original underground cave air. Validated pools have a bounded liner.
        boolean basin=c.pool!=null && c.pool.radius(x,z,noise)<1 && c.terrainSurface<poolSurface(x,z)-.001;
        boolean liner=basin && y<=c.terrainSurface+.5 && y>=c.terrainSurface-2.5;
        if(!liner && y<c.original-3)sculpt=Math.min(original,sculpt);
        double influence=c.pool!=null?1:c.mask;
        double depthFade=smooth((y-(sea-96))/24.0);
        double result=original+influence*depthFade*(sculpt-original);
        if(c.arch!=null) {
            var a=c.arch;double body=a.fin().solid(x,y,z)*scale;
            // Add the seaward fin above the original terrain; retain existing cave air in its attachment.
            if(y>=c.original-3)result=Math.max(result,body);
            if(a.fin().edge(x,z)>-1 && a.opening(x,y,z)<0)
                result=Math.min(result,a.opening(x,y,z)*.65);
        }
        if(c.overhang!=null && y>=c.original-3)
            result=Math.max(result,c.overhang.ledge().solid(x,y,z)*scale);
        return result;
    }
    public boolean waterCandidate(int x,int y,int z) {
        Column c=column(x,z);
        if(y>=c.water || y<=c.terrainSurface+.5 || y<sea-32)return false;
        if(c.pool!=null)return c.pool.wetAt(x,z,poolSurface(x,z),noise);
        return c.mask>.999 && y<sea && c.arch==null && c.overhang==null;
    }
    static double smooth(double v) { v=Math.max(0,Math.min(1,v));return v*v*v*(v*(v*6-15)+10); }
    static long key(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
}
