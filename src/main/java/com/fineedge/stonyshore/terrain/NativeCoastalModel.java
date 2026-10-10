package com.fineedge.stonyshore.terrain;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/** Runtime context for the registered density nodes; all terrain queries are pure. */
public final class NativeCoastalModel extends CoastalColumnSampler {
    private final Terrain baseline;
    private final Shore shore,ocean;
    private final SurfaceShore surfaceShore;
    private final RegionalCoast coast;
    private final int sea,maxY,inlandReach;
    private final CoastalShape shape;
    private final BoundedCache<Long,Double> heights=new BoundedCache<>(16384);
    private final BoundedCache<Long,Integer> biomes=new BoundedCache<>(16384);
    private final BoundedCache<Long,CoastalShape.Ground> grounds=new BoundedCache<>(8192);
    private final LongAdder probes=new LongAdder(),planningNanos=new LongAdder(),water=new LongAdder(),aquifers=new LongAdder();
    public NativeCoastalModel(long seed,int sea,int maxY,Terrain baseline,Shore shore,Shore ocean,
                              SurfaceShore surfaceShore,CoastalShape.Options options) {
        this(seed,sea,maxY,baseline,shore,ocean,surfaceShore,options,baseline);
    }
    public NativeCoastalModel(long seed,int sea,int maxY,Terrain baseline,Shore shore,Shore ocean,
                              SurfaceShore surfaceShore,CoastalShape.Options options,Terrain rock) {
        this(seed,sea,maxY,baseline,shore,ocean,surfaceShore,options,rock,96);
    }
    public NativeCoastalModel(long seed,int sea,int maxY,Terrain baseline,Shore shore,Shore ocean,
                              SurfaceShore surfaceShore,CoastalShape.Options options,Terrain rock,int inlandReach) {
        super(seed,sea,baseline,shore,false);this.baseline=rock;this.shore=shore;this.ocean=ocean;
        this.surfaceShore=surfaceShore;
        this.sea=sea;this.maxY=maxY;this.inlandReach=Math.max(48,Math.min(160,inlandReach));
        coast=new RegionalCoast(sea,this.inlandReach,this::originalHeight,
            (x,z)->biome(x,z)==1,(x,z)->biome(x,z)==2);
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
            if(shore.contains(px,pz))return 1;
            if(ocean.contains(px,pz))return 2;
            // Only nearby ocean can make a terrain-top supplement relevant. Negative inland
            // classification must not reconstruct thousands of distant density columns.
            boolean nearOcean=ocean.contains(px-64,pz) || ocean.contains(px+64,pz)
                || ocean.contains(px,pz-64) || ocean.contains(px,pz+64);
            return nearOcean && surfaceShore.contains(px,(int)Math.floor(originalHeight(px,pz)),pz)?1:0;
        });
    }
    private double probe(int x,int y,int z) {
        probes.increment();double value=baseline.density(x,y,z);
        if(!Double.isFinite(value))throw new IllegalStateException("Non-finite baseline density");return value;
    }
    private double heightNode(int x,int z) {
        return heights.get(CoastalShape.key(x,z),k->{
            int top=maxY-1;if(probe(x,top,z)>0)return (double)top;
            // Use the immutable final upstream field, including paired terrain additions.
            // Eight-block probes locate the highest substantial surface before refining it.
            for(int low=top-8;low>=sea-96;low-=8) {
                if(probe(x,low,z)>0) {
                    int bottom=low,upper=low+8;
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
    public boolean shoreColumn(int x,int z) {
        GroundContext g=context(x,z);return biome(x,z)==1 || g.mask>.5 && g.distance>=0;
    }
    private record GroundContext(double mask,double distance,double relief) {}
    private GroundContext context(int x,int z) {
        var c=coast.sample(x,z);
        double land=1-CoastalShape.smooth((c.distance()-(inlandReach-32))/32);
        double water=1-CoastalShape.smooth((-c.distance()-(RegionalCoast.OCEAN_REACH-32))/32);
        return new GroundContext(c.eligibility()*land*water,c.distance(),c.relief());
    }
    public CoastalShape.Ground ground(int x,int z) {
        return grounds.get(CoastalShape.key(x,z),k->{
            long started=System.nanoTime();
            try {
                double height=originalHeight(x,z);GroundContext c=context(x,z);
                return new CoastalShape.Ground(height,c.mask,c.distance,
                    Math.max(0,inlandReach-Math.max(0,c.distance)),c.relief,c.distance<0);
            } finally {planningNanos.add(System.nanoTime()-started);}
        });
    }
    public CoastalShape.Column detail(int x,int z) { return shape.column(x,z); }
    public CoastalProfile.Sample profile(int x,int z) { return shape.profile(x,z); }
    @Override public Column column(int x,int z) {
        var c=detail(x,z);
        return new Column(c.surface(),c.mask()>0 || c.featureInfluence()>0,c.sand(),c.water(),c.original());
    }
    @Override public double cap(double original,int x,int y,int z,double scale) {
        if(y<sea-96 || y>=maxY)return original;
        // Categorical local biome labels never clip a neighboring complete coastal plan.
        GroundContext c=context(x,z);
        if(c.mask<=0 && Math.abs(c.distance)>112)return original;
        return shape.density(original,x,y,z,scale);
    }
    @Override public boolean waterCandidate(int x,int y,int z) {
        return y>=sea-3 && y<maxY && shape.waterCandidate(x,y,z);
    }
    @Override public CoastalLandforms.Arch arch(int x,int z) { return detail(x,z).arch(); }
    @Override public CoastalLandforms.Overhang overhang(int x,int z) { return detail(x,z).overhang(); }
    @Override public double beachField(int x,int z) { return shape.beachField(x,z); }
    @Override public double sandCover(int x,int z,int floor) {
        if(floor<sea-28 || floor>sea+8)return 0;
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
        s.putAll(coast.stats());
        s.put("groundPlanningNanos",planningNanos.sum());return s;
    }
}
