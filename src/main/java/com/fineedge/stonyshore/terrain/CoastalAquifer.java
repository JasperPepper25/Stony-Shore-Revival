package com.fineedge.stonyshore.terrain;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import java.util.concurrent.atomic.AtomicBoolean;

/** Retains the pack's aquifer except in our newly carved sea-level and validated elevated basin volumes. */
public final class CoastalAquifer implements Aquifer {
    private final Aquifer delegate;
    private final FluidPicker fluids;
    private final CoastalColumnSampler columns;
    private final AtomicBoolean failed;
    private boolean sourceWater;

    public CoastalAquifer(Aquifer delegate, FluidPicker fluids, CoastalColumnSampler columns, AtomicBoolean failed) {
        this.delegate = delegate; this.fluids = fluids; this.columns = columns; this.failed = failed;
    }
    @Override public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
        sourceWater = false;
        BlockState original = delegate.computeSubstance(context, density);
        // Positive final density includes structure beards. Existing fluids (including lava)
        // are never replaced, and the global picker must permit ordinary water at the coastal datum.
        if (failed.get() || density > 0 || (original != null && !original.isAir())) return original;
        try {
            int x = context.blockX(), y = context.blockY(), z = context.blockZ();
            if (columns.waterCandidate(x, y, z) && fluids.computeFluid(x, y, z).at(Math.min(y,columns.seaLevel()-1)).is(Blocks.WATER)) {
                sourceWater = true;
                columns.waterSelected();
                return Blocks.WATER.defaultBlockState();
            }
        } catch (RuntimeException ex) {
            if (failed.compareAndSet(false, true))
                LogUtils.getLogger().error("Coastal adapter disabled after a shallow-water sampling failure", ex);
        }
        return original;
    }
    @Override public boolean shouldScheduleFluidUpdate() {
        // A complete layer of source water needs no initial flow tick. Other fluid decisions
        // retain the delegate's scheduling behavior; normal later block updates still work.
        return !sourceWater && delegate.shouldScheduleFluidUpdate();
    }
}
