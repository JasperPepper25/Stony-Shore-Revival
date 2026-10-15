package com.fineedge.stonyshore.support;

import java.util.function.Supplier;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;

/** Bind test-only block holders explicitly; plain Bootstrap has already frozen its wrapper. */
public final class TestBlockRegistry {
    private TestBlockRegistry() {}
    @SuppressWarnings("deprecation")
    public static synchronized <B extends Block> B register(String path,Supplier<B> factory) {
        var id=new ResourceLocation("terrain11_test",path);
        if(ForgeRegistries.BLOCKS.containsKey(id))throw new IllegalStateException("Duplicate test fixture: "+id);
        var vanilla=(MappedRegistry<Block>)BuiltInRegistries.BLOCK;
        var forge=(ForgeRegistry<Block>)ForgeRegistries.BLOCKS;
        boolean forgeWasLocked=forge.isLocked();
        vanilla.unfreeze();forge.unfreeze();
        try {
            B block=factory.get();
            // Registration binds the constructor's intrusive holder, so freezing does
            // not leave an unbound test object in Minecraft's global block registry.
            forge.register(id,block);
            // GameData initializes these after normal mod registration. A standalone
            // JUnit bootstrap needs the same cache setup for fluid/shape queries.
            block.getStateDefinition().getPossibleStates().forEach(state->state.initCache());
            return block;
        } finally {
            if(forgeWasLocked)forge.freeze();
            vanilla.freeze();
        }
    }
}
