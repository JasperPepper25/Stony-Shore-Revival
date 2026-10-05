package com.fineedge.stonyshore.generation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BeachMaterialsTest {
    @BeforeAll static void setup() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void materialChangePreservesSlabGeometryAndWater() {
        var old=Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.BOTTOM)
            .setValue(BlockStateProperties.WATERLOGGED,true);
        var replacement=BeachMaterials.copySlab(old,Blocks.SANDSTONE_SLAB);
        assertTrue(replacement.is(Blocks.SANDSTONE_SLAB));
        assertEquals(SlabType.BOTTOM,replacement.getValue(BlockStateProperties.SLAB_TYPE));
        assertTrue(replacement.getValue(BlockStateProperties.WATERLOGGED));
    }
    @Test void ordinaryBuildingSlabsAreNotGeneratedTerrainSlabs() {
        assertFalse(BeachMaterials.generatedStoneSlab(Blocks.STONE_SLAB.defaultBlockState()));
        assertFalse(BeachMaterials.generatedStoneSlab(Blocks.STONE.defaultBlockState()));
    }
}
