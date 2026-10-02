package com.fineedge.stonyshore.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Pure, bounded planning against the original seeded terrain, with per-worker caches. */
public final class CoastalColumnSampler {
    @FunctionalInterface public interface Terrain { double density(int x, int y, int z); }
    @FunctionalInterface public interface Shore { boolean contains(int x, int z); }
    public record Column(double surface, boolean active, double sandStrength) {}
    private final Terrain terrain;
    private final Shore shore;
    private final CoastalTerrainPlanner planner;
    private final int sea;
    private final ThreadLocal<Map<Long, Column>> columns = ThreadLocal.withInitial(() -> boundedMap(1024));
    private final ThreadLocal<Map<Long, Boolean>> biomes = ThreadLocal.withInitial(() -> boundedMap(2048));
    private final LongAdder planned = new LongAdder(), eligible = new LongAdder();
    private final LongAdder aquifers = new LongAdder(), water = new LongAdder();

    public CoastalColumnSampler(long seed, int sea, Terrain terrain, Shore shore) {
        this(seed, sea, terrain, shore, true);
    }
    public CoastalColumnSampler(long seed, int sea, Terrain terrain, Shore shore, boolean sandyShelves) {
        this.terrain = terrain; this.shore = shore; this.sea = sea;
        this.planner = new CoastalTerrainPlanner(seed, sandyShelves);
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
        Column unchanged = new Column(sea + 18, false, 0);
        if (!isShore(x, z)) return unchanged;
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
        return new Column(target, active, proposed.sandStrength());
    }
    private double boundaryMask(int x, int z) {
        // Distance to the nearest non-shore quart CELL, evaluated at each block coordinate.
        // Unlike the old quart stencil, this does not jump every four blocks. At an actual
        // biome boundary it reaches zero before the exact-biome exclusion takes over.
        int qx = Math.floorDiv(x, 4), qz = Math.floorDiv(z, 4);
        double distance = 12;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int bx = (qx + dx) * 4, bz = (qz + dz) * 4;
            if (isShore(bx, bz)) continue;
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
    public double cap(double original, int x, int y, int z, double scale) {
        if (y < sea - 3 || y >= sea + 24) return original;
        Column column = column(x, z);
        if (!column.active()) return original;
        double cut = Math.min(original, (column.surface() + 0.5 - y) * scale);
        // Return smoothly to the actual incoming density, even if its interpolated surface
        // differs from the raw planning estimate. No abrupt density switch at Y=77.
        double fade = 1 - smooth((y - (sea + 12)) / 12.0);
        return original + fade * (cut - original);
    }
    /** Only the shallow volume cut out of previously solid land belongs to our water pass. */
    public boolean waterCandidate(int x, int y, int z) {
        if (y < sea - 3 || y >= sea) return false;
        Column column = column(x, z);
        return column.active() && y >= column.surface() + 0.5 && density(x, y, z) > 0;
    }
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
