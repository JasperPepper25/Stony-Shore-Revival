package com.fineedge.stonyshore.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Pure, bounded planning against the original seeded terrain, with per-worker caches. */
public final class CoastalColumnSampler {
    @FunctionalInterface public interface Terrain { double density(int x, int y, int z); }
    @FunctionalInterface public interface Shore { boolean contains(int x, int z); }
    public record Column(double surface, boolean active) {}
    private final Terrain terrain;
    private final Shore shore;
    private final CoastalTerrainPlanner planner;
    private final int sea;
    private final ThreadLocal<Map<Long, Column>> columns = ThreadLocal.withInitial(() -> boundedMap(1024));
    private final ThreadLocal<Map<Long, Boolean>> biomes = ThreadLocal.withInitial(() -> boundedMap(2048));
    private final LongAdder planned = new LongAdder(), eligible = new LongAdder();

    public CoastalColumnSampler(long seed, int sea, Terrain terrain, Shore shore) {
        this.terrain = terrain; this.shore = shore; this.sea = sea;
        this.planner = new CoastalTerrainPlanner(seed);
    }
    public int seaLevel() { return sea; }
    public long plannedColumns() { return planned.sum(); }
    public long eligibleColumns() { return eligible.sum(); }

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
        Column unchanged = new Column(sea + 14, false);
        if (!isShore(x, z)) return unchanged;
        // Exclude tall coasts, submerged floors and caves immediately under the proposed floor.
        if (density(x, sea + 14, z) > 0 || density(x, sea, z) <= 0
            || density(x, sea - 4, z) <= 0 || density(x, sea - 5, z) <= 0
            || density(x, sea - 6, z) <= 0) return unchanged;
        int surface = sea + 13;
        while (surface > sea && density(x, surface, z) <= 0) surface--;
        // A centered stencil fades towards biome boundaries without reading/generating chunks.
        double mask = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double weight = (3 - Math.abs(dx)) * (3 - Math.abs(dz));
            if (isShore(x + dx * 4, z + dz * 4)) mask += weight;
        }
        mask /= 81.0;
        // Require a broad interior; the exact center biome check also forbids any spill outside shore.
        mask = Math.max(0, (mask - 0.55) / 0.45);
        mask = mask * mask * (3 - 2 * mask);
        double proposed = planner.sample(x, z, surface, sea, mask).targetSurface();
        // Carving only: maintain at least the checked sea-4 floor; never fill existing caves.
        double target = Math.max(sea - 3, Math.min(surface, proposed));
        boolean active = target < surface - 0.05;
        if (active) eligible.increment();
        return new Column(target, active);
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
        if (y < sea - 3 || y > sea + 14) return original;
        Column column = column(x, z);
        if (!column.active()) return original;
        return Math.min(original, (column.surface() + 0.5 - y) * scale);
    }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private static <V> Map<Long, V> boundedMap(int capacity) {
        return new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, V> entry) { return size() > capacity; }
        };
    }
}
