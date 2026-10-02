package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalTerrainPlannerTest {
    @Test void maskAndAltitudeExcludeUnrelatedTerrain() {
        var planner = new CoastalTerrainPlanner(42);
        for (int x = -128; x <= 128; x++) {
            assertEquals(66, planner.sample(x, -31, 66, 63, 0).targetSurface());
            assertEquals(100, planner.sample(x, -31, 100, 63, 1).targetSurface());
            assertEquals(45, planner.sample(x, -31, 45, 63, 1).targetSurface());
        }
    }
    @Test void terrainChangesStayBounded() {
        var planner = new CoastalTerrainPlanner(-912345L);
        for (int x = -100; x < 100; x++) for (int z = -100; z < 100; z++) {
            double original = 57 + Math.floorMod(x, 30);
            var column = planner.sample(x, z, original, 63, 1);
            assertTrue(column.targetSurface() >= original - 8);
            assertTrue(column.targetSurface() <= original + 3);
            assertTrue(column.basinStrength() >= 0 && column.basinStrength() <= 1);
        }
    }
    @Test void independentChunkTraversalMatchesWholeRegionIncludingNegativeCoordinates() {
        var whole = new CoastalTerrainPlanner(9876);
        double[][] expected = new double[96][96];
        for (int x = -48; x < 48; x++) for (int z = -48; z < 48; z++)
            expected[x + 48][z + 48] = whole.sample(x, z, 66, 63, 1).targetSurface();
        for (int cx = 2; cx >= -3; cx--) for (int cz = 2; cz >= -3; cz--) {
            var chunkPlanner = new CoastalTerrainPlanner(9876);
            for (int lx = 15; lx >= 0; lx--) for (int lz = 15; lz >= 0; lz--) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                assertEquals(expected[x + 48][z + 48], chunkPlanner.sample(x, z, 66, 63, 1).targetSurface());
            }
        }
    }
    @Test void fieldIsContinuousAcrossChunkAndNoiseGridBoundaries() {
        var planner = new CoastalTerrainPlanner(9012);
        for (int x = -256; x < 256; x++) for (int z = -64; z < 64; z++) {
            double y = planner.sample(x, z, 66, 63, 1).targetSurface();
            assertTrue(Math.abs(y - planner.sample(x + 1, z, 66, 63, 1).targetSurface()) < 1.5);
            assertTrue(Math.abs(y - planner.sample(x, z + 1, 66, 63, 1).targetSurface()) < 1.5);
        }
    }
    @Test void seedsChangeShapeAndMaskFadesSmoothly() {
        var a = new CoastalTerrainPlanner(12);
        var b = new CoastalTerrainPlanner(13);
        int different = 0;
        for (int x = -100; x < 100; x++) {
            double full = a.sample(x, 42, 66, 63, 1).targetSurface();
            assertEquals((66 + full) / 2, a.sample(x, 42, 66, 63, 0.5).targetSurface(), 1e-12);
            if (Math.abs(full - b.sample(x, 42, 66, 63, 1).targetSurface()) > 0.1) different++;
        }
        assertTrue(different > 100);
    }
    @Test void invalidInputsFailClearly() {
        var planner = new CoastalTerrainPlanner(0);
        assertThrows(IllegalArgumentException.class, () -> planner.sample(0, 0, Double.NaN, 63, 1));
        assertThrows(IllegalArgumentException.class, () -> planner.sample(0, 0, 66, 63, Double.POSITIVE_INFINITY));
    }
}
