package com.fineedge.stonyshore.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Pure, bounded planning against the original seeded terrain, with per-worker caches. */
public final class CoastalColumnSampler {
    @FunctionalInterface public interface Terrain { double density(int x, int y, int z); }
    @FunctionalInterface public interface Shore { boolean contains(int x, int z); }
    @FunctionalInterface public interface SurfaceShore { boolean contains(int x,int y,int z); }
    public record Column(double surface, boolean active, double sandStrength, int waterLevel, double original) {}
    private final Terrain terrain;
    private final Shore shore;
    private final CoastalTerrainPlanner planner;
    private final int sea;
    private final boolean advanced;
    private final int maxY;
    private final Shore ocean;
    private final SurfaceShore surfaceShore;
    private final boolean sandyShelves;
    private final CoastalLandforms landforms;
    private final ThreadLocal<Map<Long, CoastalLandforms.Base>> bases = ThreadLocal.withInitial(() -> boundedMap(8192));
    private final ThreadLocal<Map<Long, Boolean>> oceans = ThreadLocal.withInitial(() -> boundedMap(8192));
    private final ThreadLocal<Map<Long, Column>> columns = ThreadLocal.withInitial(() -> boundedMap(1024));
    private final ThreadLocal<Map<Long, Boolean>> biomes = ThreadLocal.withInitial(() -> boundedMap(2048));
    private final LongAdder planned = new LongAdder(), eligible = new LongAdder();
    private final LongAdder aquifers = new LongAdder(), water = new LongAdder();

    public CoastalColumnSampler(long seed, int sea, Terrain terrain, Shore shore) {
        this(seed, sea, terrain, shore, true);
    }
    public CoastalColumnSampler(long seed, int sea, Terrain terrain, Shore shore, boolean sandyShelves) {
        this(seed,sea,terrain,shore,sandyShelves,(x,z)->false,false,false,320);
    }
    public CoastalColumnSampler(long seed,int sea,Terrain terrain,Shore shore,boolean sandyShelves,
                               Shore ocean,boolean advanced,boolean arches,int maxY) {
        this(seed,sea,terrain,shore,sandyShelves,ocean,advanced,arches,maxY,(x,y,z)->shore.contains(x,z));
    }
    public CoastalColumnSampler(long seed,int sea,Terrain terrain,Shore shore,boolean sandyShelves,
                               Shore ocean,boolean advanced,boolean arches,int maxY,SurfaceShore surfaceShore) {
        this.surfaceShore=surfaceShore;
        this.terrain = terrain; this.shore = shore; this.sea = sea;
        this.planner = new CoastalTerrainPlanner(seed, sandyShelves);
        this.sandyShelves=sandyShelves; this.ocean=ocean; this.advanced=advanced; this.maxY=maxY;
        this.landforms=advanced ? new CoastalLandforms(seed,sea,this::base,this::density,this::isShore,arches) : null;
    }
    public int seaLevel() { return sea; }
    public long plannedColumns() { return planned.sum(); }
    public long eligibleColumns() { return eligible.sum(); }
    public long aquiferAttachments() { return aquifers.sum(); }
    public long waterDecisions() { return water.sum(); }
    public void aquiferAttached() { aquifers.increment(); }
    public void waterSelected() { water.increment(); }

    public Column column(int x, int z) {
        long key = key(x, z);
        Map<Long, Column> cache = columns.get();
        Column existing = cache.get(key);
        if (existing != null) return existing;
        Column next = plan(x, z);
        cache.put(key, next);
        return next;
    }
    private Column plan(int x, int z) {
        planned.increment();
        Column unchanged = new Column(sea + 18, false, 0, sea, sea + 18);
        if (!isShore(x, z)) return unchanged;
        if (advanced) return regionalColumn(x,z);
        // The fade is already zero below this upper guard. Never flatten a high cliff.
        if (density(x, sea + 18, z) > 0 || density(x, sea, z) <= 0
            || density(x, sea - 4, z) <= 0 || density(x, sea - 5, z) <= 0
            || density(x, sea - 6, z) <= 0) return unchanged;
        int solidY = sea + 17;
        double above = density(x, sea + 18, z), solid = density(x, solidY, z);
        while (solidY > sea && solid <= 0) {
            above = solid;
            solid = density(x, --solidY, z);
        }
        // Fractional zero crossing: do not round the terrain down into one-block bands.
        double surface = solidY + solid / (solid - above) - 0.5;
        double mask = boundaryMask(x, z);
        var proposed = planner.sample(x, z, surface, sea, mask);
        // Preserve the checked sea-4 floor; carving only, with up to three shallow water layers.
        double target = Math.max(sea - 3.5, Math.min(surface, proposed.targetSurface()));
        boolean active = target < surface - 1e-6;
        if (active) eligible.increment();
        return new Column(target, active, proposed.sandStrength(), sea, surface);
    }
    private double boundaryMask(int x, int z) {
        // Distance to the nearest non-shore quart CELL, evaluated at each block coordinate.
        // Unlike the old quart stencil, this does not jump every four blocks. At an actual
        // biome boundary it reaches zero before the exact-biome exclusion takes over.
        int qx = Math.floorDiv(x, 4), qz = Math.floorDiv(z, 4);
        double distance = 12;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int bx = (qx + dx) * 4, bz = (qz + dz) * 4;
            if (isShore(bx, bz) || (advanced && isOcean(bx,bz))) continue;
            double nx = Math.max(0, Math.max(bx - x, x - (bx + 4)));
            double nz = Math.max(0, Math.max(bz - z, z - (bz + 4)));
            distance = Math.min(distance, Math.hypot(nx, nz));
        }
        return smooth(distance / 12);
    }
    private double density(int x, int y, int z) {
        double value = terrain.density(x, y, z);
        if (!Double.isFinite(value)) throw new IllegalStateException("Non-finite baseline density");
        return value;
    }
    private boolean isShore(int x, int z) {
        int qx = Math.floorDiv(x, 4), qz = Math.floorDiv(z, 4);
        return biomes.get().computeIfAbsent(key(qx, qz), k -> shore.contains(qx * 4, qz * 4));
    }
    private CoastalLandforms.Base base(int x,int z) {
        return bases.get().computeIfAbsent(key(x,z), k -> planBase(x,z));
    }
    private CoastalLandforms.Base planBase(int x,int z) {
        // Coarse vertical search followed by a fractional crossing. Bounds come from this
        // world's build height; high stony shores are no longer rejected at one fixed Y.
        int y=maxY-1;
        while(y>sea-16 && density(x,y,z)<=0) y-=8;
        if(y<=sea-16 && density(x,y,z)<=0) return new CoastalLandforms.Base(sea-16,sea-16,0,0);
        int upper=Math.min(maxY-1,y+8);
        double above=density(x,upper,z);
        while(upper>y+1 && density(x,upper-1,z)<=0) { upper--; above=density(x,upper,z); }
        int solidY=upper-1;double solid=density(x,solidY,z);
        double original=solidY+solid/(solid-above)-0.5;
        if(!Double.isFinite(original)) return new CoastalLandforms.Base(maxY-1,maxY-1,0,0);
        if(!isShore(x,z) || !surfaceShore.contains(x,(int)Math.round(original),z))
            return new CoastalLandforms.Base(original,original,0,0);
        double mask=boundaryMask(x,z), d=oceanDistance(x,z);
        double beach=sandyShelves ? beachField(x,z)*(1-smooth((d-12)/24)) : 0;
        double target=original;
        if(original>=sea && original<sea+18 && density(x,sea-4,z)>0
            && density(x,sea-5,z)>0 && density(x,sea-6,z)>0) {
            // Keep the successful low-coast relief, including its natural residual pillars.
            target=planner.sample(x,z,original,sea,mask).targetSurface();
        }
        double upperBlend=smooth((original-(sea+9))/14);
        if(upperBlend>0) {
            double h=Math.max(0,original-sea);
            double coastal=h*(0.08+0.92*smooth(d/96));
            double warp=5*(planner.noise(x,z,180,601)-0.5);
            double band=(coastal+warp)/16;
            double tier=Math.floor(band);
            double stepped=(tier+smooth((band-tier-0.52)/0.42))*16-warp;
            double profile=sea+Math.max(1,0.12*coastal+0.88*stepped);
            double proposed=original+mask*(Math.max(original-96,Math.min(original,profile))-original);
            target+=upperBlend*(proposed-target);
        }
        if(beach>0 && original>=sea-2) {
            // Retreat selected cliff feet to make an actual beach bench. Width follows the
            // ocean distance and a broad seed field, rather than low altitude alone.
            double pocket=smooth((planner.noise(x,z,19,617)-0.62)/0.18);
            double bench=sea+0.6+Math.min(d,20)*0.055-3.0*pocket;
            double desired=Math.max(original-96,Math.min(target,bench));
            target+=beach*mask*(desired-target);
        }
        target=Math.min(original,Math.max(sea-3.5,target));
        return new CoastalLandforms.Base(target,original,mask,beach);
    }
    public double beachField(int x,int z) {
        return smooth((planner.noise(x,z,144,611)-0.48)/0.24);
    }
    private double oceanDistance(int x,int z) {
        double distance=96;
        int gx=Math.floorDiv(x,8)*8,gz=Math.floorDiv(z,8)*8;
        for(int dx=-12;dx<=12;dx++) for(int dz=-12;dz<=12;dz++) {
            int px=gx+dx*8,pz=gz+dz*8;
            double d=Math.hypot(px-x,pz-z);
            if(d<distance && isOcean(px,pz)) distance=d;
        }
        return distance;
    }
    private boolean isOcean(int x,int z) {
        int qx=Math.floorDiv(x,4),qz=Math.floorDiv(z,4);
        return oceans.get().computeIfAbsent(key(qx,qz),k->ocean.contains(qx*4,qz*4));
    }
    private Column regionalColumn(int x,int z) {
        var base=base(x,z); double surface=base.surface(); int water=sea;
        if(base.mask()>0) {
            var arch=landforms.arch(x,z);
            if(arch!=null) surface+=arch.reserve(x,z)*(base.original()-surface);
            var pool=landforms.pool(x,z);
            if(pool!=null) {
                surface=landforms.poolFloor(pool,x,z,surface);
                if(surface+0.5<pool.water()) water=pool.water();
            }
        }
        boolean active=surface<base.original()-1e-6;
        if(active) eligible.increment();
        return new Column(surface,active,base.sand(),water,base.original());
    }
    public CoastalLandforms.Arch arch(int x,int z) {
        return advanced && isShore(x,z) && base(x,z).mask()>0 ? landforms.arch(x,z) : null;
    }
    /** Material-only apron: adjacent ocean floor can receive sand, but its density is unchanged. */
    public double sandCover(int x,int z,int floorY) {
        if(!advanced || !sandyShelves || floorY<sea-11 || floorY>sea+5) return 0;
        double strength=isShore(x,z) ? base(x,z).sand() : 0;
        if(strength==0 && isOcean(x,z)) {
            for(int dx=-12;dx<=12;dx+=4) for(int dz=-12;dz<=12;dz+=4) {
                double d=Math.hypot(dx,dz);
                if(d>12 || !isShore(x+dx,z+dz)) continue;
                var b=base(x+dx,z+dz);
                if(b.surface()>sea+5) continue;
                strength=Math.max(strength,b.sand()*(1-smooth(d/16)));
            }
        }
        return strength*(1-smooth((sea-2-floorY)/9.0));
    }
    public double cap(double original, int x, int y, int z, double scale) {
        if(y<sea-3 || y>= (advanced ? maxY : sea+24)) return original;
        Column column=column(x,z); double result=original;
        if(column.active()) {
            double cut=Math.min(original,(column.surface()+0.5-y)*scale);
            double start=advanced ? Math.max(sea+12,column.original()+2) : sea+12;
            double fade=1-smooth((y-start)/12.0);
            result=original+fade*(cut-original);
        }
        var arch=arch(x,z);
        if(arch!=null) result=Math.min(result,arch.opening(x,y,z)*0.4);
        return result;
    }
    /** Each elevated basin has a single validated water plane, never one level per column. */
    public boolean waterCandidate(int x, int y, int z) {
        if(y<sea-3 || y>= (advanced ? maxY : sea)) return false;
        Column column=column(x,z);
        if(column.active() && y<column.waterLevel() && y>=column.surface()+0.5 && density(x,y,z)>0) return true;
        var arch=arch(x,z);
        return y<sea && arch!=null && arch.opening(x,y,z)<0 && density(x,y,z)>0;
    }
    public boolean elevatedWater(int x,int y,int z) { return y>=sea && waterCandidate(x,y,z); }
    private static double smooth(double v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * v * (v * (v * 6 - 15) + 10);
    }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private static <V> Map<Long, V> boundedMap(int capacity) {
        return new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, V> entry) { return size() > capacity; }
        };
    }
}
