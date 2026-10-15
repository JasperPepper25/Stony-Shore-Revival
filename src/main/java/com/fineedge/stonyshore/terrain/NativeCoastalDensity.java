package com.fineedge.stonyshore.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.concurrent.atomic.AtomicBoolean;

/** Registered, serializable graph node. Lithostitched installs it before RandomState seeding. */
public final class NativeCoastalDensity implements DensityFunction {
    public interface Observer extends Visitor { void inspect(NativeCoastalDensity node); }
    public static final KeyDispatchDataCodec<NativeCoastalDensity> CODEC=KeyDispatchDataCodec.of(
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(NativeCoastalDensity::input),
            Codec.doubleRange(0.01,1).fieldOf("scale").forGetter(NativeCoastalDensity::scale)
        ).apply(instance,NativeCoastalDensity::new)));
    private static final class Binding {
        volatile NativeCoastalModel model;
        volatile AtomicBoolean failed;
    }
    private final DensityFunction input;
    private final double scale;
    private final Binding binding;
    public NativeCoastalDensity(DensityFunction input,double scale) { this(input,scale,new Binding()); }
    private NativeCoastalDensity(DensityFunction input,double scale,Binding binding) {
        this.input=input;this.scale=scale;this.binding=binding;
    }
    public DensityFunction input() { return input; }
    public double scale() { return scale; }
    public void bind(NativeCoastalModel model,AtomicBoolean failed) { binding.failed=failed;binding.model=model; }
    @Override public double compute(FunctionContext context) {
        double original=input.compute(context);
        NativeCoastalModel model=binding.model;AtomicBoolean failed=binding.failed;
        if(model==null || failed.get() || context.getBlender()!=Blender.empty()) return original;
        try { return model.cap(original,context.blockX(),context.blockY(),context.blockZ(),scale); }
        catch(RuntimeException ex) {
            if(failed.compareAndSet(false,true)) com.mojang.logging.LogUtils.getLogger()
                .error("Native coastal terrain disabled after a sampling failure",ex);
            return original;
        }
    }
    @Override public void fillArray(double[] values,ContextProvider provider) { provider.fillAllDirectly(values,this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        if(visitor instanceof Observer observer)observer.inspect(this);
        // Registry templates remain unbound. Each seeded graph gets its own context;
        // subsequent NoiseChunk cache mapping shares the already-bound world context.
        Binding mappedBinding=binding.model==null?new Binding():binding;
        return visitor.apply(new NativeCoastalDensity(input.mapAll(visitor),scale,mappedBinding));
    }
    @Override public double minValue() { return Math.min(-4096,input.minValue()); }
    @Override public double maxValue() { return Math.max(4096,input.maxValue()); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
