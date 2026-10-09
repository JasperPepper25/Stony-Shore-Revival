package com.fineedge.stonyshore.terrain;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/** Runtime context for the registered density nodes; all terrain queries are pure. */
public final class NativeCoastalModel extends CoastalColumnSampler {
    private final Terrain baseline;
    private final Shore shore,ocean;
    private final SurfaceShore surfaceShore;
    private record Boundary(double ocean,double inland,int shoreX,int shoreZ,boolean hasShore) {}
    private final BoundedCache<Long,Boundary> boundaries=new BoundedCache<>(4096);
    private final int sea,maxY;
    private final CoastalShape shape;
    private final BoundedCache<Long,Double> heights=new BoundedCache<>(4096);
    private final BoundedCache<Long,Integer> biomes=new BoundedCache<>(16384);
    private final BoundedCache<Long,CoastalShape.Ground> grounds=new BoundedCache<>(8192);
    private final LongAdder probes=new LongAdder(),planningNanos=new LongAdder(),water=new LongAdder(),aquifers=new LongAdder();
    public NativeCoastalModel(long seed,int sea,int maxY,Terrain baseline,Shore shore,Shore ocean,
                              SurfaceShore surfaceShore,CoastalShape.Options options) {
        this(seed,sea,maxY,baseline,shore,ocean,surfaceShore,options,baseline);
    }
    public NativeCoastalModel(long seed,int sea,int maxY,Terrain baseline,Shore shore,Shore ocean,
                              SurfaceShore surfaceShore,CoastalShape.Options options,Terrain rock) {
        super(seed,sea,baseline,shore,false);this.baseline=baseline;this.shore=shore;this.ocean=ocean;
        this.surfaceShore=surfaceShore;
        this.sea=sea;this.maxY=maxY;
        this.shape=new CoastalShape(seed,sea,this::ground,options,this::originalHeight,(x,y,z)->{
            probes.increment();double density=rock.density(x,y,z);
            if(!Double.isFinite(density))throw new IllegalStateException("Non-finite support density");
            return density>0;
        });
    }
    private int biome(int x,int z) {
        int qx=Math.floorDiv(x,4),qz=Math.floorDiv(z,4);
        return biomes.get(CoastalShape.key(qx,qz),k->{
            int px=qx*4,pz=qz*4;
            // The climate projection can miss a high shore. Only the reconstructed terrain
            // top supplements it; a cave biome at sea level cannot expand the footprint.
            if(shore.contains(px,pz) || surfaceShore.contains(px,(int)Math.floor(originalHeight(px,pz)),pz))return 1;
            return ocean.contains(px,pz)?2:0;
        });
    }
    private double probe(int x,int y,int z) {
        probes.increment();double value=baseline.density(x,y,z);
        if(!Double.isFinite(value))throw new IllegalStateException("Non-finite baseline density");return value;
    }
    private double heightNode(int x,int z) {
        return heights.get(CoastalShape.key(x,z),k->{
            int top=maxY-1;if(probe(x,top,z)>0)return (double)top;
            // Preliminary density has no carved cave layers; coarse crossing probes are bounded.
            for(int low=top-16;low>=sea-96;low-=16) {
                if(probe(x,low,z)>0) {
                    int bottom=low,upper=low+16;
                    while(upper-bottom>1) {int mid=(bottom+upper)/2;if(probe(x,mid,z)>0)bottom=mid;else upper=mid;}
                    double a=probe(x,bottom,z),b=probe(x,upper,z);
                    return bottom+a/(a-b)-0.5;
                }
            }
            return (double)(sea-96);
        });
    }
    public double originalHeight(int x,int z) {
        int gx=Math.floorDiv(x,16)*16,gz=Math.floorDiv(z,16)*16;
        double fx=Math.floorMod(x,16)/16.0,fz=Math.floorMod(z,16)/16.0;
        double[] rows=new double[4];
        for(int i=0;i<4;i++) {
            int nz=gz+(i-1)*16;
            rows[i]=CoastalInterpolation.cubic(heightNode(gx-16,nz),heightNode(gx,nz),
                heightNode(gx+16,nz),heightNode(gx+32,nz),fx);
        }
        return CoastalInterpolation.cubic(rows[0],rows[1],rows[2],rows[3],fz);
    }
    /** Predicates describe surface climate, not the biome in a cave at sea level. */
    public boolean shoreColumn(int x,int z) { return biome(x,z)==1; }
    private Boundary boundaryNode(int x,int z) {
        return boundaries.get(CoastalShape.key(x,z),k->{
            int here=biome(x,z);double oceanDistance=96,inlandDistance=48,shoreDistance=96;
            int shoreX=x,shoreZ=z;boolean hasShore=here==1;
            // Cached eight-block distance nodes replace a full scan for every solid/air query.
            for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++) {
                int px=x+dx*8,pz=z+dz*8;
                double distance=Math.hypot(Math.max(0,Math.abs(px-x)-4),Math.max(0,Math.abs(pz-z)-4));
                if(distance>=96)continue;
                int type=biome(px,pz);
                if(type==2)oceanDistance=Math.min(oceanDistance,distance);
                if(type==0)inlandDistance=Math.min(inlandDistance,distance);
                if(type==1 && distance<shoreDistance) {
                    shoreDistance=distance;shoreX=px;shoreZ=pz;hasShore=true;
                }
            }
            return new Boundary(here==2?-shoreDistance:oceanDistance,inlandDistance,shoreX,shoreZ,hasShore);
        });
    }
    private Boundary boundary(int x,int z) {
        int gx=Math.floorDiv(x,8)*8,gz=Math.floorDiv(z,8)*8;
        double fx=Math.floorMod(x,8)/8.0,fz=Math.floorMod(z,8)/8.0;
        Boundary a=boundaryNode(gx,gz),b=boundaryNode(gx+8,gz),c=boundaryNode(gx,gz+8),d=boundaryNode(gx+8,gz+8);
        Boundary nearest=a;
        for(Boundary node:new Boundary[]{b,c,d}) {
            if(node.hasShore && (!nearest.hasShore
                || Math.hypot(node.shoreX-x,node.shoreZ-z)<Math.hypot(nearest.shoreX-x,nearest.shoreZ-z)))nearest=node;
        }
        return new Boundary(lerp(a.ocean,b.ocean,c.ocean,d.ocean,fx,fz),
            lerp(a.inland,b.inland,c.inland,d.inland,fx,fz),nearest.shoreX,nearest.shoreZ,nearest.hasShore);
    }
    private static double lerp(double a,double b,double c,double d,double x,double z) {
        return (a+(b-a)*x)*(1-z)+(c+(d-c)*x)*z;
    }
    public CoastalShape.Ground ground(int x,int z) {
        return grounds.get(CoastalShape.key(x,z),k->{
            long started=System.nanoTime();
            try {
                int type=biome(x,z);double height=originalHeight(x,z);
                if(type==0)return new CoastalShape.Ground(height,0,96,0,height,false);
                Boundary b=boundary(x,z);
                double width=Math.min(48,24+Math.max(0,height-sea)*.4);
                // The outer twenty blocks taper to untouched ocean, without changing biomes.
                double oceanMask=1-CoastalShape.smooth((-b.ocean-(CoastalBeachProfile.OCEAN_REACH-20))/20);
                double mask=Math.min(oceanMask,CoastalShape.smooth(b.inland/width));
                double shoreHeight=type==1?height:b.hasShore && mask>0?originalHeight(b.shoreX,b.shoreZ):height;
                return new CoastalShape.Ground(height,mask,b.ocean,b.inland,shoreHeight,type==2);
            } finally {planningNanos.add(System.nanoTime()-started);}
        });
    }
    public CoastalShape.Column detail(int x,int z) { return shape.column(x,z); }
    public CoastalProfile.Sample profile(int x,int z) { return shape.profile(x,z); }
    @Override public Column column(int x,int z) {
        var c=detail(x,z);
        return new Column(c.surface(),c.mask()>0,c.sand(),c.water(),c.original());
    }
    @Override public double cap(double original,int x,int y,int z,double scale) {
        if(y<sea-96 || y>=maxY)return original;
        int type=biome(x,z);
        if(type==0 || (type==2 && boundary(x,z).ocean<=-CoastalBeachProfile.OCEAN_REACH))return original;
        return shape.density(original,x,y,z,scale);
    }
    @Override public boolean waterCandidate(int x,int y,int z) {
        return y>=sea-3 && y<maxY && shape.waterCandidate(x,y,z);
    }
    @Override public CoastalLandforms.Arch arch(int x,int z) { return detail(x,z).arch(); }
    @Override public CoastalLandforms.Overhang overhang(int x,int z) { return detail(x,z).overhang(); }
    @Override public double beachField(int x,int z) { return shape.beachField(x,z); }
    @Override public double sandCover(int x,int z,int floor) {
        if(floor<sea-28 || floor>sea+8 || biome(x,z)==0)return 0;
        var c=detail(x,z);
        // Material follows the very same signed-distance beach/seabed profile as density.
        return c.sand()*(1-CoastalShape.smooth((sea-12-floor)/16.0));
    }
    @Override public int seaLevel() { return sea; }
    @Override public long plannedColumns() { return grounds.misses(); }
    @Override public long eligibleColumns() { return shape.stats().getOrDefault("columnCacheMisses",0L); }
    @Override public void aquiferAttached() { aquifers.increment(); }
    @Override public long aquiferAttachments() { return aquifers.sum(); }
    @Override public void waterSelected() { water.increment(); }
    @Override public long waterDecisions() { return water.sum(); }
    @Override public Map<String,Long> landformStats() {
        var s=new TreeMap<>(shape.stats());s.put("baselineDensityProbes",probes.sum());
        s.put("heightCacheHits",heights.hits());s.put("heightCacheMisses",heights.misses());
        s.put("groundCacheHits",grounds.hits());s.put("groundCacheMisses",grounds.misses());
        s.put("biomeClassificationCacheHits",biomes.hits());s.put("biomeClassificationCacheMisses",biomes.misses());
        s.put("coastDistanceCacheHits",boundaries.hits());s.put("coastDistanceCacheMisses",boundaries.misses());
        s.put("groundPlanningNanos",planningNanos.sum());return s;
    }
}
