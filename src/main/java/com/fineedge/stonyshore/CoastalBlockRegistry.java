package com.fineedge.stonyshore;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Stable coastal sediment steps; independent of optional terrain-smoothing mods. */
public final class CoastalBlockRegistry {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,StonyShoreRevival.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,StonyShoreRevival.ID);
    public static final RegistryObject<Block> SAND_SLAB=BLOCKS.register("coastal_sand_slab",
        CoastalBlockRegistry::sandSlabBlock);
    public static final RegistryObject<Item> SAND_SLAB_ITEM=ITEMS.register("coastal_sand_slab",
        ()->new BlockItem(SAND_SLAB.get(),new Item.Properties()));
    private CoastalBlockRegistry() {}
    static SlabBlock sandSlabBlock() { return new SlabBlock(BlockBehaviour.Properties.copy(Blocks.SAND).noOcclusion()); }
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus); }
}
