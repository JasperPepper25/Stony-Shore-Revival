package com.fineedge.stonyshore.generation;

final class ShoreMath {
    private ShoreMath() {}
    static double valueNoise(int x, int z, int scale) {
        int gx = Math.floorDiv(x, scale), gz = Math.floorDiv(z, scale);
        double fx = (x - gx * scale) / (double) scale, fz = (z - gz * scale) / (double) scale;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = unit(hash(gx, 0, gz)), b = unit(hash(gx + 1, 0, gz));
        double c = unit(hash(gx, 0, gz + 1)), d = unit(hash(gx + 1, 0, gz + 1));
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    static long hash(long x, long y, long z) {
        long h = x * 0x632BE59BD9B4E019L ^ y * 0x9E3779B97F4A7C15L ^ z * 0xC6BC279692B5CC83L;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27; h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
}
