package com.fineedge.stonyshore.terrain;

/**
 * Pure terrain prototype, deliberately not connected to Minecraft generation yet.
 * Samples absolute coordinates; no chunk RNG, mutable state, or world writes.
 * A future integration must supply a smooth biome/coast mask and validate the
 * density-stage insertion against the pack's resolved terrain graph.
 */
public final class CoastalTerrainPlanner {
    private final long seed;
    public CoastalTerrainPlanner(long seed) { this.seed = seed; }

    public record Column(double targetSurface, double basinStrength, double influence) {}

    /** Surface elevations use block coordinates, not heightmap first-air heights. */
    public Column sample(int x, int z, double originalSurface, int seaLevel, double shoreMask) {
        if (!Double.isFinite(originalSurface) || !Double.isFinite(shoreMask))
            throw new IllegalArgumentException("Surface and mask must be finite");
        // Fade out before steep high coasts and deep water; this prototype is for low shelves.
        double vertical = smooth(seaLevel - 5.0, seaLevel, originalSurface)
            * (1 - smooth(seaLevel + 8.0, seaLevel + 18.0, originalSurface));
        double influence = clamp(shoreMask) * vertical;
        if (influence == 0) return new Column(originalSurface, 0, 0);
        double wx = x + 18 * (noise(x, z, 79, 11) - 0.5);
        double wz = z + 18 * (noise(x, z, 79, 29) - 0.5);
        double field = 0.8 * noise(wx, wz, 52, 47) + 0.2 * noise(wx, wz, 22, 71);
        double basin = smooth(0.38, 0.72, field);
        double shelf = seaLevel + 1.5 + 2.5 * noise(wx, wz, 65, 101);
        double depth = 2.0 + 3.0 * noise(wx, wz, 35, 139);
        double target = shelf - basin * depth;
        // Hard bound for the first prototype. No unlimited continental cliff flattening.
        double delta = Math.max(-8, Math.min(3, target - originalSurface));
        return new Column(originalSurface + influence * delta, basin, influence);
    }

    private double noise(double x, double z, double scale, long salt) {
        double sx = x / scale, sz = z / scale;
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
