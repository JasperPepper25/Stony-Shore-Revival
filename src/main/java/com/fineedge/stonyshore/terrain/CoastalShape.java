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
                         double featureInfluence,double terrainSurface,SeaArchCatalog.Section archSection) {
        public Column(double original,double surface,double mask,double sand,int water,
                CoastalLandforms.Pool pool,CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang,
                double featureInfluence,double terrainSurface) {
            this(original,surface,mask,sand,water,pool,arch,overhang,featureInfluence,terrainSurface,null);
        }
    }
    public record Options(boolean landforms,boolean pools,boolean arches,boolean beaches,double beachFrequency,
                          boolean tiers,boolean embayments,boolean caves) {
        public Options(boolean landforms,boolean pools,boolean arches,boolean beaches,double beachFrequency) {
            this(landforms,pools,arches,beaches,beachFrequency,true,true,true);
        }
    }
    @FunctionalInterface public interface GroundSampler { Ground sample(int x,int z); }
    @FunctionalInterface public interface Solid { boolean test(int x,int y,int z); }
    private record Region(List<CoastalLandforms.Pool> pools,CoastalLandforms.Arch arch,CoastalLandforms.Overhang overhang) {}
    private record Facing(double angle) {
        int x(int root,double distance) { return (int)Math.round(root+Math.cos(angle)*distance); }
        int z(int root,double distance) { return (int)Math.round(root+Math.sin(angle)*distance); }
    }
    private record CliffRoot(int x,int z,Ground ground,Facing facing) {}
    private final CoastalTerrainPlanner noise;
    private final GroundSampler ground;
    private final Solid support;
    private final CoastalProfile profiles;
    private final CoastalBeachProfile beaches;
    private final CoastalPools pools;
    private final CoastalCaves caves;
    private final BoundedLongCache<Boolean> caveFits=new BoundedLongCache<>(256);
    private final int sea;
    private final Options options;
    private final BoundedLongCache<Region> rawRegions=new BoundedLongCache<>(256);
    private final BoundedLongCache<Region> regions=new BoundedLongCache<>(256);
    private final BoundedLongCache<Column> columns=new BoundedLongCache<>(8192,8);
    private final BoundedLongCache<Boolean> apronSupport=new BoundedLongCache<>(8192);
    private final ConcurrentHashMap<String,LongAdder> counters=new ConcurrentHashMap<>();
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options) {
        this(seed,sea,ground,options,(x,z)->ground.sample(x,z).height());
    }
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options,CoastalProfile.Heights heights) {
        this(seed,sea,ground,options,heights,(x,y,z)->y<ground.sample(x,z).height()+.5);
    }
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options,CoastalProfile.Heights heights,Solid support) {
        this(seed,sea,ground,options,heights,support,(x,y,z)->true);
    }
    public CoastalShape(long seed,int sea,GroundSampler ground,Options options,CoastalProfile.Heights heights,Solid support,
                        CoastalCaves.Allowed caveAllowed) {
        noise=new CoastalTerrainPlanner(seed);this.sea=sea;this.ground=ground;this.options=options;this.support=support;
        profiles=new CoastalProfile(heights);beaches=new CoastalBeachProfile(sea,noise,options);
        pools=new CoastalPools(noise,sea,ground,this::poolSurface,support);
        caves=new CoastalCaves(noise,sea,ground,this::poolSurface,support,caveAllowed);
    }
    public CoastalProfile.Sample profile(int x,int z) { return profiles.sample(x,z); }
    private void count(String name) { counters.computeIfAbsent(name,k->new LongAdder()).increment(); }
    public Map<String,Long> stats() {
        Map<String,Long> result=new TreeMap<>();counters.forEach((k,v)->result.put(k,v.sum()));
        pools.stats().forEach((k,v)->result.merge(k,v,Long::sum));
        caves.stats().forEach((k,v)->result.merge(k,v,Long::sum));
        result.put("regionCacheHits",regions.hits());result.put("regionCacheMisses",regions.misses());
        result.put("columnCacheHits",columns.hits());result.put("columnCacheMisses",columns.misses());
        result.put("columnCacheRecentHits",columns.recentHits());result.put("columnCacheLocalHits",columns.localHits());return result;
    }
    public Column column(int x,int z) { return columns.get(key(x,z),k->planColumn(x,z)); }
    public CoastalCaves.Cave cave(int x,int z) {
        if(!options.landforms() || !options.caves())return null;
        var cave=caves.cave(x,z);if(cave==null)return null;
        return caveFits.get(key(cave.x(),cave.z()),k->caveFits(cave))?cave:null;
    }
    private boolean caveFits(CoastalCaves.Cave cave) {
        int cx=Math.floorDiv(cave.x(),48),cz=Math.floorDiv(cave.z(),48);
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            var r=rawRegion(cx+dx,cz+dz);
            for(var p:r.pools)if(near(cave.centerX(),cave.centerZ(),cave.radius(),p.x(),p.z(),p.footprintRadius()))return false;
            if(r.arch!=null && near(cave.centerX(),cave.centerZ(),cave.radius(),r.arch.fin().centerX(),r.arch.fin().centerZ(),r.arch.fin().radius()))return false;
            if(r.overhang!=null && near(cave.centerX(),cave.centerZ(),cave.radius(),r.overhang.ledge().centerX(),r.overhang.ledge().centerZ(),r.overhang.ledge().radius()))return false;
        }
        count(cave.flooded()?"seaCavesFitted":"shelfCavesFitted");return true;
    }
    public double carveCave(double density,int x,int y,int z,double scale) {
        var cave=cave(x,z);if(cave==null || y<sea-8)return density;
        return Math.min(density,cave.opening(x,y,z)*scale);
    }
    public boolean caveWaterCandidate(int x,int y,int z) {
        if(y>=sea || y<sea-8)return false;
        var cave=cave(x,z);
        return cave!=null && cave.flooded() && cave.opening(x,y,z)<-.15;
    }
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
        // Local terrain influence can end before a previously validated rock volume does.
        // Distant columns still bypass all regional planning, including cached negative plans.
        if(g.mask<=0 && (!options.landforms || Math.abs(g.oceanDistance)>112 || g.inlandDistance<=0))
            return new Column(g.height,g.height,0,0,sea,null,null,null,0,g.height);
        var beach=beaches.sample(x,z,g);
        double surface=beach.surface(),featureInfluence=0,sand=beach.sand();
        CoastalLandforms.Pool pool=null;CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        int water=sea;
        if(options.landforms) {
            int cx=Math.floorDiv(x,48),cz=Math.floorDiv(z,48);
            // Plans travel with their complete volumes across both chunks and region boundaries.
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                Region r=region(cx+dx,cz+dz);
                for(var p:r.pools) {
                    double amount=g.mask>0?p.influence(x,z,noise):0;
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
        if(pool==null && !g.ocean && g.height>sea+12) {
            // A beach toe cannot remove the cliff attachment of an accepted additive form.
            double attachment=arch!=null?arch.reserve(x,z):overhang!=null?overhang.reserve(x,z):0;
            surface+=attachment*(Math.max(surface,g.height)-surface);
        }
        double terrainSurface=surface;
        if(arch!=null && arch.fin().edge(x,z)>0)surface=Math.max(surface,arch.fin().top(x,z));
        if(overhang!=null && overhang.ledge().edge(x,z)>0)surface=Math.max(surface,overhang.ledge().top(x,z));
        var archSection=arch!=null && arch.fin().catalog()!=null?new SeaArchCatalog.Section(arch,x,z):null;
        return new Column(g.height,surface,g.mask,sand,water,pool,arch,overhang,featureInfluence,terrainSurface,archSection);
    }
    private Region rawRegion(int cx,int cz) { return rawRegions.get(key(cx,cz),k->planRegion(cx,cz)); }
    private Region region(int cx,int cz) { return regions.get(key(cx,cz),k->resolveRegion(cx,cz)); }
    private Region resolveRegion(int cx,int cz) {
        Region r=rawRegion(cx,cz);var arch=r.arch;var overhang=r.overhang;
        var accepted=new ArrayList<>(r.pools);
        if(arch==null && accepted.isEmpty() && overhang==null)return r;
        // Whole-footprint arbitration is based on raw plans and never recurses into neighbors.
        // Conservative circles include root offsets and opposed 66-block fins in coves.
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++) {
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
        if(arch!=null) {
            count("archesAccepted");
            if(arch.fin().catalog()!=null)count("archStyle"+arch.fin().catalog().style().name()+"Accepted");
        }
        for(var p:accepted) {
            count("poolsAccepted");count(p.water()>sea+4?"elevatedPoolsAccepted":"lowPoolsAccepted");
            count(p.rx()<5?"smallPoolsAccepted":p.rx()<8?"mediumPoolsAccepted":"largePoolsAccepted");
            if(p.rx()>=12)count("extraLargePoolsAccepted");
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
    private static boolean intersects(CoastalLandforms.Overhang h,CoastalLandforms.Arch a) { return near(h.ledge().centerX(),h.ledge().centerZ(),h.ledge().radius(),a.fin().centerX(),a.fin().centerZ(),a.fin().radius()); }
    private static boolean intersects(CoastalLandforms.Overhang a,CoastalLandforms.Overhang b) { return near(a.ledge().centerX(),a.ledge().centerZ(),a.ledge().radius(),b.ledge().centerX(),b.ledge().centerZ(),b.ledge().radius()); }
    private static boolean intersects(CoastalLandforms.Pool p,CoastalLandforms.Overhang h) { return near(p.x(),p.z(),p.footprintRadius(),h.ledge().centerX(),h.ledge().centerZ(),h.ledge().radius()); }
    private Facing oceanFacing(int x,int z) {
        double gx=ground.sample(x+12,z).oceanDistance-ground.sample(x-12,z).oceanDistance;
        double gz=ground.sample(x,z+12).oceanDistance-ground.sample(x,z-12).oceanDistance;
        if(Math.hypot(gx,gz)<2)return null;
        Facing f=new Facing(Math.atan2(-gz,-gx));
        Ground here=ground.sample(x,z),outside=ground.sample(f.x(x,24),f.z(z,24));
        return outside.oceanDistance<here.oceanDistance-8?f:null;
    }
    private CliffRoot cliffRoot(int cx,int cz,int x,int z) {
        Ground origin=ground.sample(x,z);
        if(origin.mask<.35 || origin.inlandDistance<4 || origin.oceanDistance< -24 || origin.oceanDistance>64
            || origin.shoreHeight<sea+24)return null;
        Facing facing=oceanFacing(x,z);if(facing==null)return null;
        CliffRoot best=null;double bestScore=12;
        // Locate the supported high side of a real cliff, rather than using a random
        // inland point and requiring a pre-existing cave underneath its proposed lip.
        for(int offset=-24;offset<=24;offset+=4) {
            int px=facing.x(x,offset),pz=facing.z(z,offset);
            if(Math.floorDiv(px,48)!=cx || Math.floorDiv(pz,48)!=cz)continue;
            Ground root=ground.sample(px,pz);
            if(root.mask<.5 || root.ocean || root.inlandDistance<4 || root.height<sea+24
                || root.oceanDistance<0 || root.oceanDistance>48)continue;
            Ground outer=ground.sample(facing.x(px,12),facing.z(pz,12));
            double drop=root.height-outer.height;
            double score=drop-.15*Math.max(0,root.oceanDistance);
            if(drop>12 && score>bestScore && outer.oceanDistance<root.oceanDistance-5) {
                bestScore=score;best=new CliffRoot(px,pz,root,facing);
            }
        }
        return best;
    }
    private Region planRegion(int cx,int cz) {
        count("regionsPlanned");
        CoastalLandforms.Arch arch=null;CoastalLandforms.Overhang overhang=null;
        boolean archRegion=options.arches && noise.value(cx,cz,900)<.32;
        SeaArchCatalog.Style archStyle=archRegion?SeaArchCatalog.style(noise,cx,cz):null;
        if(archStyle!=null)count("archStyle"+archStyle.name()+"Selected");
        boolean ledgeRegion=noise.value(cx,cz,1200)<.5;
        for(int attempt=0;attempt<16 && ((archRegion && arch==null) || (ledgeRegion && overhang==null));attempt++) {
            int salt=attempt*37;
            int x=cx*48+3+(int)(noise.value(cx,cz,801+salt)*42),z=cz*48+3+(int)(noise.value(cx,cz,831+salt)*42);
            CliffRoot root=cliffRoot(cx,cz,x,z);if(root==null)continue;
            x=root.x;z=root.z;Ground g=root.ground;Facing facing=root.facing;
            if(arch==null && archRegion) {
                count("archCandidates");
                var candidate=SeaArchCatalog.create(archStyle,noise,cx,cz,salt,x,z,facing.angle,sea,g,ground);
                if(candidate!=null && attachedArch(candidate)) {
                    arch=candidate;count("archesSupported");
                    count("archStyle"+archStyle.name()+"Supported");
                    count(archStyle.rising()?"rockFinArchesSupported":"roundedArchesSupported");
                } else count("archesRejectedAttachmentOrPortals");
            }
            if(overhang==null && ledgeRegion) {
                count("overhangCandidates");
                double family=noise.value(cx,cz,1204+salt);boolean tinyLip=family<.3;
                double reach=tinyLip?4+3*noise.value(cx,cz,1202+salt)
                    :family<.85?10+8*noise.value(cx,cz,1202+salt):18+6*noise.value(cx,cz,1202+salt);
                double width=tinyLip?6+5*noise.value(cx,cz,1203+salt):10+9*noise.value(cx,cz,1203+salt);
                double crest=g.height-1;
                double rootDepth=tinyLip?6+5*noise.value(cx,cz,1205+salt)
                    :Math.min(42,Math.max(14,(g.height-sea)*(.35+.18*noise.value(cx,cz,1205+salt))));
                double tipThickness=tinyLip?1.6+noise.value(cx,cz,1206+salt):3+2*noise.value(cx,cz,1206+salt);
                double phase=noise.value(cx,cz,1207+salt)*Math.PI*2;
                var ledge=new CoastalProjection.Ledge(x,z,facing.angle,reach,width,crest,
                    rootDepth,tipThickness,tinyLip?.35:1.2,phase);
                int hx=facing.x(x,reach*.78),hz=facing.z(z,reach*.78);
                var candidate=new CoastalLandforms.Overhang(hx,hz,facing.angle-Math.PI/2,width,reach,
                    rootDepth+tipThickness,(int)Math.floor(crest-rootDepth-tipThickness),ledge);
                if(attachedLedge(candidate)) {
                    overhang=candidate;count("overhangsSupported");count(tinyLip?"smallLipsSupported":"buttressedOverhangsSupported");
                }
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
            if(root.mask<.45 || root.height<rootY+3 || !support.test(x,rootY,z))return false;
        }
        int tipX=(int)Math.round(fin.rootX()+Math.cos(fin.direction())*(fin.reach()-4));
        int tipZ=(int)Math.round(fin.rootZ()+Math.sin(fin.direction())*(fin.reach()-4));
        Ground tip=ground.sample(tipX,tipZ);
        if(tip.inlandDistance<4 || tip.oceanDistance>2 || tip.oceanDistance< -96)return false;
        // The crosswise passage must already face exterior space: never excavate approaches.
        int openingY=fin.catalog()==null?(int)a.openingCenterY():sea+6;
        for(int sign:new int[]{-1,1}) {
            int x=(int)Math.round(a.x()-sign*Math.sin(a.angle())*(a.length()+3));
            int z=(int)Math.round(a.z()+sign*Math.cos(a.angle())*(a.length()+3));
            Ground g=ground.sample(x,z);
            if(g.inlandDistance<4 || g.oceanDistance< -96 || g.height>openingY-3 || baseSurface(x,z,g)>openingY-3)return false;
        }
        if(fin.catalog()!=null)for(var stack:fin.catalog().stacks()) {
            for(int side=-1;side<=1;side++) {
                int x=(int)Math.round(stack.x()+Math.cos(stack.angle())*side*stack.rx()*.65);
                int z=(int)Math.round(stack.z()+Math.sin(stack.angle())*side*stack.rx()*.65);
                Ground g=ground.sample(x,z);
                if(g.inlandDistance<4 || g.oceanDistance< -96 || g.oceanDistance>2
                    || !support.test(x,(int)Math.floor(stack.floor()),z))return false;
            }
        }
        return true;
    }
    private boolean attachedLedge(CoastalLandforms.Overhang a) {
        var p=a.ledge();int rootY=(int)Math.floor(p.crest()-5);
        if(ground.sample(p.rootX(),p.rootZ()).height<rootY+3
            || !support.test(p.rootX(),rootY,p.rootZ()))return false;
        for(int side:new int[]{-1,1}) {
            int x=(int)Math.round(p.rootX()-Math.sin(p.direction())*side*p.width()*.5);
            int z=(int)Math.round(p.rootZ()+Math.cos(p.direction())*side*p.width()*.5);
            Ground root=ground.sample(x,z);
            if(root.mask<.45 || root.height<rootY+1 || !support.test(x,rootY-2,z))return false;
        }
        Ground root=ground.sample(p.rootX(),p.rootZ());
        // Check an actual visible strip below the outer lip, including its shoulders.
        for(int side=-1;side<=1;side++) {
            int x=(int)Math.round(a.x()-Math.sin(p.direction())*side*p.width()*.35);
            int z=(int)Math.round(a.z()+Math.cos(p.direction())*side*p.width()*.35);
            Ground outer=ground.sample(x,z);
            if(outer.inlandDistance<4 || outer.oceanDistance< -96 || outer.oceanDistance>root.oceanDistance-Math.min(5,p.reach()*.45)
                || baseSurface(x,z,outer)>p.underside(x,z)-Math.min(4,Math.max(1,p.reach()*.18)))return false;
        }
        return true;
    }
    public double density(double original,int x,int y,int z,double scale) {
        Column c=column(x,z);if(c.mask<=0 && c.featureInfluence<=0)return original;
        double rough=(noise.noise(x+y*.37,z-y*.21,27,1701)-.5)*.7;
        if(c.pool!=null || c.sand>.5)rough=0;
        double sculpt=(c.terrainSurface+.5-y+rough)*scale;
        // A reconstructed surface is a planning estimate, not proof of rock below it.
        // Intersect ordinary shaping with the upstream field at every height; replacing
        // even a shallow band manufactures detached skins on steep cliff/stack edges.
        // Explicit pool liners and connected low sandy aprons are bounded fill volumes.
        boolean basin=c.pool!=null && c.pool.radius(x,z,noise)<1 && c.terrainSurface<poolSurface(x,z)-.001;
        boolean liner=basin && y<=c.terrainSurface+.5 && y>=c.terrainSurface-2.5;
        int apronFloor=Math.max(sea-96,Math.min(sea-32,(int)Math.floor(c.original)-2));
        boolean apron=c.sand>.5 && c.terrainSurface>c.original && c.terrainSurface<=sea+6
            && y>=apronFloor && apronSupport.get(key(x,z),k->support.test(x,apronFloor,z));
        if(!liner && !apron)sculpt=Math.min(original,sculpt);
        double influence=c.pool!=null?1:c.mask;
        double depthFade=smooth((y-(sea-96))/24.0);
        double result=original+influence*depthFade*(sculpt-original);
        if(c.arch!=null) {
            var a=c.arch;double body=(c.archSection!=null?c.archSection.solid(y):a.fin().solid(x,y,z))*scale;
            // An accepted solid has a complete interior. The smoothed original height
            // cannot clip that body into a shell; the finite analytic volume bounds it.
            result=Math.max(result,body);
            if(body>0) {
                double opening=c.archSection!=null?c.archSection.opening(y):a.opening(x,y,z);
                if(opening<0)result=Math.min(result,opening*.65);
            }
        }
        if(c.overhang!=null)
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
