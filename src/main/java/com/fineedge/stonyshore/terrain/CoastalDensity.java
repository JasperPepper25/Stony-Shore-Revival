package com.fineedge.stonyshore.terrain;

import com.mojang.logging.LogUtils;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime-only adapter: keeps the original router graph as its child. */
public final class CoastalDensity implements DensityFunction {
    private final DensityFunction input;
    private final CoastalColumnSampler columns;
    private final double scale;
    private final AtomicBoolean failed;
    public CoastalDensity(DensityFunction input, CoastalColumnSampler columns, double scale, AtomicBoolean failed) {
        this.input = input; this.columns = columns; this.scale = scale; this.failed = failed;
    }
    CoastalColumnSampler columns() { return columns; }
    AtomicBoolean failureFlag() { return failed; }
    @Override public double compute(FunctionContext context) {
        double original = input.compute(context);
        // Leave old-version terrain blending to the existing generator.
        if (failed.get() || context.getBlender() != Blender.empty()) return original;
        try {
            return columns.cap(original, context.blockX(), context.blockY(), context.blockZ(), scale);
        } catch (RuntimeException ex) {
            if (failed.compareAndSet(false, true))
                LogUtils.getLogger().error("Coastal terrain prototype disabled after a sampling failure; original density retained", ex);
            return original;
        }
    }
    @Override public void fillArray(double[] values, ContextProvider provider) { provider.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new CoastalDensity(input.mapAll(visitor), columns, scale, failed));
    }
    @Override public double minValue() { return Math.min(input.minValue(), -4096); }
    @Override public double maxValue() { return input.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        // Like NoiseChunk's runtime caches, this captures live state and is not a datapack value.
        throw new UnsupportedOperationException("CoastalDensity is runtime-only; serialize NoiseGeneratorSettings instead");
    }
}
