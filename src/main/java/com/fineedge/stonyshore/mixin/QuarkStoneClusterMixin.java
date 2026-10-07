package com.fineedge.stonyshore.mixin;

import com.fineedge.stonyshore.generation.QuarkStonePolicy;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Quark hook checks each destination, including clusters originating in other biomes. */
@Pseudo
@Mixin(targets="org.violetmoon.quark.content.world.gen.BigStoneClusterGenerator",remap=false)
public abstract class QuarkStoneClusterMixin {
    @Shadow(remap=false) @Final private BlockState placeState;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void stonyshore$generatorConstructed(CallbackInfo ci) { QuarkStonePolicy.generatorConstructed(); }
    @Inject(method="canPlaceBlock",at=@At("HEAD"),cancellable=true,remap=false)
    private void stonyshore$excludeClusterStone(ServerLevelAccessor world,BlockPos pos,CallbackInfoReturnable<Boolean> ci) {
        QuarkStonePolicy.observedHook();
        var id=ForgeRegistries.BLOCKS.getKey(placeState.getBlock());
        if(id==null || !QuarkStonePolicy.excluded(id.toString(),true,false))return;
        if(QuarkStonePolicy.excluded(id.toString(),world.getBiome(pos).is(Biomes.STONY_SHORE),
            CoastalTerrainIntegration.shoreColumn(world.getLevel(),pos.getX(),pos.getZ())
                || world.getBiome(new BlockPos(pos.getX(),world.getLevel().getSeaLevel()+2,pos.getZ())).is(Biomes.STONY_SHORE))) {
            QuarkStonePolicy.rejectedPlacement();ci.setReturnValue(false);
        }
    }
}
