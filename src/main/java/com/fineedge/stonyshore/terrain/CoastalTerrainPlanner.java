package com.fineedge.stonyshore.terrain;

/** Seeded, continuous coastal relief in absolute coordinates; no chunk RNG or world writes. */
public final class CoastalTerrainPlanner {
    private final long seed;
    private final boolean sandyShelves;
    public CoastalTerrainPlanner(long seed) { this(seed, true); }
    public CoastalTerrainPlanner(long seed, boolean sandyShelves) {
        this.seed = seed; this.sandyShelves = sandyShelves;
    }

    public record Column(double targetSurface, double basinStrength, double influence, double sandStrength) {}

    /** Surface elevations use block coordinates, not heightmap first-air heights. */
    public Column sample(int x, int z, double originalSurface, int seaLevel, double shoreMask) {
        if (!Double.isFinite(originalSurface) || !Double.isFinite(shoreMask))
            throw new IllegalArgumentException("Surface and mask must be finite");
        // Reach zero BEFORE the sampler's high-coast exclusion, avoiding a clipped contour.
        double vertical = smooth(seaLevel - 5.0, seaLevel, originalSurface)
            * (1 - smooth(seaLevel + 6.0, seaLevel + 17.0, originalSurface));
        double influence = clamp(shoreMask) * vertical;
        if (influence == 0) return new Column(originalSurface, 0, 0, 0);
        double wx = x + 18 * (noise(x, z, 79, 11) - 0.5);
        double wz = z + 18 * (noise(x, z, 79, 29) - 0.5);
        double field = 0.8 * noise(wx, wz, 52, 47) + 0.2 * noise(wx, wz, 22, 71);
        double basin = smooth(0.34, 0.66, field);
        double shelf = seaLevel + 1.0 + 1.5 * noise(wx, wz, 65, 101);
        double depth = 3.0 + 3.0 * noise(wx, wz, 35, 139);
        double target = shelf - basin * depth;
        // A separate broad field produces occasional sand shelves, not per-chunk patches.
        double sand = sandyShelves ? smooth(0.64, 0.82, noise(wx, wz, 96, 211)) : 0;
        target = lerp(target, seaLevel + 0.4 + 0.8 * noise(wx, wz, 70, 239), sand);
        double delta = Math.max(-8, Math.min(3, target - originalSurface));
        return new Column(originalSurface + influence * delta, basin, influence, sand * influence);
    }

    private double noise(double x, double z, double scale, long salt) {
        // Rotate the lattice; domain warping then breaks up the remaining straight contours.
        double sx = (0.8 * x + 0.6 * z) / scale, sz = (-0.6 * x + 0.8 * z) / scale;
        long ix = (long) Math.floor(sx), iz = (long) Math.floor(sz);
        double fx = fade(sx - ix), fz = fade(sz - iz);
        return lerp(lerp(value(ix, iz, salt), value(ix + 1, iz, salt), fx),
            lerp(value(ix, iz + 1, salt), value(ix + 1, iz + 1, salt), fx), fz);
    }
    private double value(long x, long z, long salt) {
        long h = seed ^ x * 0x632BE59BD9B4E019L ^ z * 0xC6BC279692B5CC83L ^ salt * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return ((h ^ (h >>> 31)) >>> 11) * 0x1.0p-53;
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double fade(double v) { return v * v * v * (v * (v * 6 - 15) + 10); }
    private static double smooth(double low, double high, double v) { return fade(clamp((v - low) / (high - low))); }
}
