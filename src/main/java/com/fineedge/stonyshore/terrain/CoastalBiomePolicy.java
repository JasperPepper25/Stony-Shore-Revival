package com.fineedge.stonyshore.terrain;

/** Pure geometry policy. Registry selection and compatibility checks live in the adapter. */
public final class CoastalBiomePolicy {
    public enum Choice { ORIGINAL, ROCKY_SHORE, BEACH, SNOWY_BEACH }
    public record Context(double originalHeight, double surface, double mask, double sand,
                          boolean pool, boolean rockVolume, boolean protectedBiome, boolean cold) {}
    private CoastalBiomePolicy() {}

    public static Choice choose(Context c, int y, int sea) {
        if (c.protectedBiome || !Double.isFinite(c.surface) || c.mask < .55) return Choice.ORIGINAL;
        // Submerged shelves keep their original ocean temperature/type. Added dry headlands
        // can become rocky shore, while their deep foundations retain the original biome.
        if (c.surface < sea - .5 || y < Math.max(sea - 8, Math.min(c.originalHeight, c.surface) - 8))
            return Choice.ORIGINAL;
        if (!c.pool && !c.rockVolume && c.sand > .52 && c.surface <= sea + 8)
            return c.cold ? Choice.SNOWY_BEACH : Choice.BEACH;
        return Choice.ROCKY_SHORE;
    }
}
