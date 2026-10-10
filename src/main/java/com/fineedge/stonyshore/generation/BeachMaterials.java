package com.fineedge.stonyshore.generation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;

/** Optional Terrain Slabs 4.1.1 integration; no hard dependency or global mapping change. */
public final class BeachMaterials {
    private static final BooleanProperty GENERATED=BooleanProperty.create("generated");
    private static final Set<String> STONE_SLABS=Set.of("terrain_stone_slab","terrain_andesite_slab",
        "terrain_diorite_slab","terrain_granite_slab","calcite_slab","terrain_tuff_slab",
        "terrain_cobblestone_slab","terrain_mossy_cobblestone_slab","terrain_sandstone_slab","sand_slab");
    private BeachMaterials() {}
    public static boolean generatedStoneSlab(BlockState state) {
        ResourceLocation id=ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id!=null && id.getNamespace().equals("terrain_slabs") && STONE_SLABS.contains(id.getPath())
            && state.hasProperty(GENERATED) && state.getValue(GENERATED)
            && state.hasProperty(BlockStateProperties.SLAB_TYPE)
            && state.getValue(BlockStateProperties.SLAB_TYPE)==SlabType.BOTTOM;
    }
    public static BlockState copySlab(BlockState old,Block sand) {
        // Preserve bottom/top, generated, waterlogging and any shared offset properties.
        return sand.withPropertiesOf(old);
    }
}
