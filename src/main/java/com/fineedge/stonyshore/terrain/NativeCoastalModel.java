package com.fineedge.stonyshore.terrain;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/** Runtime context for the registered density nodes; all terrain queries are pure. */
public final class NativeCoastalModel extends CoastalColumnSampler {
    private final Terrain baseline;
    private final Shore shore,ocean;
    private final SurfaceShore surfaceShore;
    private final int sea,maxY;
    private final CoastalShape shape;
    private final BoundedCache<Long,Double> heights=new BoundedCache<>(4096);
    private final BoundedCache<Long,Integer> biomes=new BoundedCache<>(16384);
    private record SurfaceKey(int x,int y,int z) {}
    private final BoundedCache<SurfaceKey,Boolean> surfaceBiomes=new BoundedCache<>(16384);
    private final BoundedCache<Long,CoastalShape.Ground> grounds=new BoundedCache<>(8192);
    private final LongAdder probes=new LongAdder(),planningNanos=new LongAdder(),water=new LongAdder(),aquifers=new LongAdder();
    public NativeCoastalModel(long seed,int sea,int maxY,Terrain baseline,Shore shore,Shore ocean,
                              SurfaceShore surfaceShore,CoastalShape.Options options) {
        super(seed,sea,baseline,shore,false);this.baseline=baseline;this.shore=shore;this.ocean=ocean;
        this.surfaceShore=surfaceShore;this.sea=sea;this.maxY=maxY;
        this.shape=new CoastalShape(seed,sea,this::ground,options);
    }
    private int biome(int x,int z) {
        int qx=Math.floorDiv(x,4),qz=Math.floorDiv(z,4);
        return biomes.get(CoastalShape.key(qx,qz),k->shore.contains(qx*4,qz*4)?1:ocean.contains(qx*4,qz*4)?2:0);
    }
    private boolean surfaceBiome(int x,int y,int z) {
        var key=new SurfaceKey(Math.floorDiv(x,4),Math.floorDiv(y,4),Math.floorDiv(z,4));
        return surfaceBiomes.get(key,k->surfaceShore.contains(k.x()*4,k.y()*4,k.z()*4));
    }
    private double probe(int x,int y,int z) {
        probes.increment();double value=baseline.density(x,y,z);
        if(!Double.isFinite(value))throw new IllegalStateException("Non-finite baseline density");return value;
    }
    private double heightNode(int x,int z) {
        return heights.get(CoastalShape.key(x,z),k->{
            int top=maxY-1;if(probe(x,top,z)>0)return (double)top;
            // Preliminary density has no carved cave layers; coarse crossing probes are bounded.
            for(int low=top-16;low>=sea-32;low-=16) {
                if(probe(x,low,z)>0) {
                    int bottom=low,upper=low+16;
                    while(upper-bottom>1) {int mid=(bottom+upper)/2;if(probe(x,mid,z)>0)bottom=mid;else upper=mid;}
                    double a=probe(x,bottom,z),b=probe(x,upper,z);
                    return bottom+a/(a-b)-0.5;
                }
            }
            return (double)(sea-32);
        });
    }
    public double originalHeight(int x,int z) {
        int gx=Math.floorDiv(x,16)*16,gz=Math.floorDiv(z,16)*16;
        double fx=CoastalShape.smooth(Math.floorMod(x,16)/16.0),fz=CoastalShape.smooth(Math.floorMod(z,16)/16.0);
        double a=heightNode(gx,gz),b=heightNode(gx+16,gz),c=heightNode(gx,gz+16),d=heightNode(gx+16,gz+16);
        return (a+(b-a)*fx)*(1-fz)+(c+(d-c)*fx)*fz;
    }
    public CoastalShape.Ground ground(int x,int z) {
        return grounds.get(CoastalShape.key(x,z),k->{
            long started=System.nanoTime();
            try {
                if(biome(x,z)!=1)return new CoastalShape.Ground(sea,0,96);
                double height=originalHeight(x,z);
                if(!surfaceBiome(x,(int)Math.round(height),z))
                    return new CoastalShape.Ground(height,0,96);
                int qx=Math.floorDiv(x,4),qz=Math.floorDiv(z,4);double distance=12;
                for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                    int bx=(qx+dx)*4,bz=(qz+dz)*4;
                    // Ocean is a true boundary too. Reach zero before the exact-biome gate.
                    if(biome(bx,bz)==1 && surfaceBiome(bx,(int)Math.round(height),bz))continue;
                    double nx=Math.max(0,Math.max(bx-x,x-(bx+4))),nz=Math.max(0,Math.max(bz-z,z-(bz+4)));
                    distance=Math.min(distance,Math.hypot(nx,nz));
                }
                double oceanDistance=96;
                int gx=Math.floorDiv(x,16)*16,gz=Math.floorDiv(z,16)*16;
                for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++) {
                    int px=gx+dx*16,pz=gz+dz*16;double d=Math.hypot(px-x,pz-z);
                    if(d<oceanDistance && biome(px,pz)==2)oceanDistance=d;
                }
                return new CoastalShape.Ground(height,CoastalShape.smooth(distance/12),oceanDistance);
            } finally {planningNanos.add(System.nanoTime()-started);}
        });
    }
    public CoastalShape.Column detail(int x,int z) { return shape.column(x,z); }
    @Override public Column column(int x,int z) {
        var c=detail(x,z);
        return new Column(c.surface(),c.mask()>0,c.sand(),c.water(),c.original());
    }
    @Override public double cap(double original,int x,int y,int z,double scale) {
        if(y<sea-24 || y>=maxY || biome(x,z)!=1)return original;
        return shape.density(original,x,y,z,scale);
    }
    @Override public boolean waterCandidate(int x,int y,int z) {
        return y>=sea-3 && y<maxY && biome(x,z)==1 && shape.waterCandidate(x,y,z);
    }
    @Override public CoastalLandforms.Arch arch(int x,int z) { return biome(x,z)==1?detail(x,z).arch():null; }
    @Override public CoastalLandforms.Overhang overhang(int x,int z) { return biome(x,z)==1?detail(x,z).overhang():null; }
    @Override public double beachField(int x,int z) { return shape.beachField(x,z); }
    @Override public double sandCover(int x,int z,int floor) {
        if(floor<sea-24 || floor>sea+6)return 0;
        double strength=biome(x,z)==1?detail(x,z).sand():0;
        if(biome(x,z)==2) {
            // Small bounded material apron; no raw height probes outside the local shore reach.
            for(int dx=-32;dx<=32;dx+=8)for(int dz=-32;dz<=32;dz+=8) {
                double d=Math.hypot(dx,dz);if(d>32 || biome(x+dx,z+dz)!=1)continue;
                var c=detail(x+dx,z+dz);
                if(c.surface()<sea+6)strength=Math.max(strength,c.sand()*(1-CoastalShape.smooth(d/36)));
            }
        }
        return strength*(1-CoastalShape.smooth((sea-6-floor)/18.0));
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
        s.put("surfaceBiomeCacheHits",surfaceBiomes.hits());s.put("surfaceBiomeCacheMisses",surfaceBiomes.misses());
        s.put("groundPlanningNanos",planningNanos.sum());return s;
    }
}
