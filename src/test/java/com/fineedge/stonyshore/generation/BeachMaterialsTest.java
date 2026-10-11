package com.fineedge.stonyshore.generation;
import com.fineedge.stonyshore.support.TestBlockRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BeachMaterialsTest {
    private static final BooleanProperty GENERATED=BooleanProperty.create("generated");
    private static final class TerrainSlabFixture extends SlabBlock {
        TerrainSlabFixture() { super(BlockBehaviour.Properties.copy(Blocks.STONE)); }
        protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
            super.createBlockStateDefinition(builder);builder.add(GENERATED);
        }
    }
    private static TerrainSlabFixture fixture;
    @BeforeAll static void setup() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        fixture=TestBlockRegistry.register("generated_terrain_slab",TerrainSlabFixture::new);
    }
    @Test void materialChangePreservesSlabGeometryAndWater() {
        for(var half:new SlabType[]{SlabType.BOTTOM,SlabType.TOP})for(boolean wet:new boolean[]{false,true}) {
            var old=fixture.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,half)
                .setValue(BlockStateProperties.WATERLOGGED,wet).setValue(GENERATED,true);
            var replacement=BeachMaterials.copySlab(old,Blocks.SANDSTONE_SLAB);
            assertTrue(replacement.is(Blocks.SANDSTONE_SLAB));
            assertEquals(half,replacement.getValue(BlockStateProperties.SLAB_TYPE));
            assertEquals(wet,replacement.getValue(BlockStateProperties.WATERLOGGED));
            assertFalse(replacement.hasProperty(GENERATED),"our stable sand steps do not use another mod's generated-fluid behavior");
        }
    }
    @Test void observedDirtGravelAndStoneStatesAreEligibleWithEitherHalf() {
        for(String path:new String[]{"dirt_slab","grass_slab","gravel_slab","sand_slab","terrain_stone_slab"})
            for(var half:new SlabType[]{SlabType.BOTTOM,SlabType.TOP}) {
                var old=fixture.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,half)
                    .setValue(BlockStateProperties.WATERLOGGED,true).setValue(GENERATED,true);
                var id=new ResourceLocation("terrain_slabs",path);
                assertTrue(BeachMaterials.naturalTerrainSlab(id,old),path+" "+half);
                assertTrue(BeachMaterials.generatedTerrainSlab(id,old),path+" "+half);
                var diskFinished=old.setValue(GENERATED,false);
                assertTrue(BeachMaterials.naturalTerrainSlab(id,diskFinished),"disk/ore finishers can reset generated");
                assertFalse(BeachMaterials.generatedTerrainSlab(id,diskFinished),"reset states still require sand context in the finisher");
            }
    }
    @Test void registryMappingRejectsUnknownMaterialsBuildingIdsAndWrongClasses() {
        var old=fixture.defaultBlockState().setValue(GENERATED,true);
        assertFalse(BeachMaterials.naturalTerrainSlab(null,old));
        assertFalse(BeachMaterials.naturalTerrainSlab(new ResourceLocation("minecraft:stone_slab"),old));
        assertFalse(BeachMaterials.naturalTerrainSlab(new ResourceLocation("terrain_slabs:diamond_slab"),old));
        assertFalse(BeachMaterials.naturalTerrainSlab(new ResourceLocation("terrain_slabs:dirt_slab"),Blocks.DIRT.defaultBlockState()));
        assertFalse(BeachMaterials.naturalTerrainSlab(new ResourceLocation("terrain_slabs:dirt_slab"),
            old.setValue(BlockStateProperties.SLAB_TYPE,SlabType.DOUBLE)));
    }
    @Test void absentOptionalModAndDeferredRegistryHaveSafeFallback() {
        assertNull(ShoreBlocks.optionalBlock("terrain_slabs:sand_slab"));
        assertSame(Blocks.SANDSTONE_SLAB,BeachMaterials.sandSlab());
        assertTrue(BeachMaterials.copySlab(Blocks.STONE_SLAB.defaultBlockState(),BeachMaterials.sandSlab())
            .is(Blocks.SANDSTONE_SLAB));
    }
    @Test void ordinaryBuildingSlabsAreNotGeneratedTerrainSlabs() {
        assertFalse(BeachMaterials.generatedStoneSlab(Blocks.STONE_SLAB.defaultBlockState()));
        assertFalse(BeachMaterials.generatedStoneSlab(Blocks.STONE.defaultBlockState()));
    }
}
