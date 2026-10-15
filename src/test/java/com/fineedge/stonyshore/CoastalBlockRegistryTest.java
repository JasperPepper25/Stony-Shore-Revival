package com.fineedge.stonyshore;

import com.fineedge.stonyshore.support.TestBlockRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalBlockRegistryTest {
    private static SlabBlock block;
    @BeforeAll static void setup() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        block=TestBlockRegistry.register("stable_sand_slab",CoastalBlockRegistry::sandSlabBlock);
    }
    @Test void stableSandStepsKeepRealHalfHeightAndWaterWithoutGravityOrGeneratedFlag() {
        assertFalse(block instanceof Fallable,"upper sand steps must not turn into falling entities");
        for(var half:new SlabType[]{SlabType.BOTTOM,SlabType.TOP}) {
            var state=block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,half)
                .setValue(BlockStateProperties.WATERLOGGED,true);
            var shape=state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
            assertEquals(half==SlabType.BOTTOM?0:.5,shape.min(Direction.Axis.Y));
            assertEquals(half==SlabType.BOTTOM?.5:1,shape.max(Direction.Axis.Y));
            assertTrue(state.getFluidState().isSource());
            assertFalse(state.hasProperty(BooleanProperty.create("generated")));
        }
    }
}
