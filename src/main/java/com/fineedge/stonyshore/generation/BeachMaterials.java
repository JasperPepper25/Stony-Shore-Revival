package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.CoastalBlockRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

/** Optional Terrain Slabs material finishing; no global mappings or external class linkage. */
public final class BeachMaterials {
    private static final BooleanProperty GENERATED=BooleanProperty.create("generated");
    private static final Set<String> NATURAL_SLABS=Set.of("terrain_stone_slab","terrain_andesite_slab",
        "terrain_diorite_slab","terrain_granite_slab","calcite_slab","terrain_tuff_slab",
        "terrain_cobblestone_slab","terrain_mossy_cobblestone_slab","terrain_sandstone_slab","sand_slab",
        "dirt_slab","grass_slab","gravel_slab","coarse_slab","rooted_dirt_slab","podzol_slab",
        "mycelium_slab","moss_slab");
    private BeachMaterials() {}
    public static boolean naturalTerrainSlab(BlockState state) {
        return naturalTerrainSlab(ForgeRegistries.BLOCKS.getKey(state.getBlock()),state);
    }
    static boolean naturalTerrainSlab(ResourceLocation id,BlockState state) {
        return id!=null && state.getBlock() instanceof SlabBlock
            && state.hasProperty(BlockStateProperties.SLAB_TYPE)
            && state.getValue(BlockStateProperties.SLAB_TYPE)!=SlabType.DOUBLE
            && (id.getNamespace().equals("terrain_slabs") && NATURAL_SLABS.contains(id.getPath())
                || id.toString().equals("stonyshorerevival:coastal_sand_slab"));
    }
    public static boolean generatedTerrainSlab(BlockState state) {
        return generatedTerrainSlab(ForgeRegistries.BLOCKS.getKey(state.getBlock()),state);
    }
    static boolean generatedTerrainSlab(ResourceLocation id,BlockState state) {
        return naturalTerrainSlab(id,state) && (id.toString().equals("stonyshorerevival:coastal_sand_slab")
            || state.hasProperty(GENERATED) && state.getValue(GENERATED));
    }
    /** Kept for callers of the V10 helper; now includes soil, gravel and either half. */
    public static boolean generatedStoneSlab(BlockState state) { return generatedTerrainSlab(state); }
    public static Block sandSlab() {
        // Terrain Slabs' sand TOP halves fall unconditionally. Both replacement halves
        // therefore use stable sediment with vanilla sand textures and waterlogging.
        return CoastalBlockRegistry.SAND_SLAB.isPresent()?CoastalBlockRegistry.SAND_SLAB.get():Blocks.SANDSTONE_SLAB;
    }
    public static BlockState copySlab(BlockState old,Block sand) {
        // Preserve bottom/top and waterlogging, along with every other shared property.
        return sand.withPropertiesOf(old);
    }
}
