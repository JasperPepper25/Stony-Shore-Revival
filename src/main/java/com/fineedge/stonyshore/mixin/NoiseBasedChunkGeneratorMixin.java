package com.fineedge.stonyshore.mixin;

import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Coordinates the resolver used to write stored chunk biome cells, retaining its cached sampler. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    // The pinned official and SRG invocation names differ. Exactly one selector must match;
    // the group enforces this in both development and production without a Mixin refmap.
    @Group(name="coastalBiomeFill",min=1,max=1)
    @Redirect(method={
        "doCreateBiomes(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        "m_224291_(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V"
    },at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"),require=0,remap=false)
    private void stonyshore$fillOfficial(ChunkAccess target,BiomeResolver resolver,Climate.Sampler cached,
                                       Blender blender,RandomState random,StructureManager structures,ChunkAccess chunk) {
        stonyshore$fill(target,resolver,cached,blender,random);
    }
    @Group(name="coastalBiomeFill",min=1,max=1)
    @Redirect(method={
        "doCreateBiomes(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        "m_224291_(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V"
    },at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/ChunkAccess;m_183442_(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"),require=0,remap=false)
    private void stonyshore$fillProduction(ChunkAccess target,BiomeResolver resolver,Climate.Sampler cached,
                                         Blender blender,RandomState random,StructureManager structures,ChunkAccess chunk) {
        stonyshore$fill(target,resolver,cached,blender,random);
    }
    @Unique
    private void stonyshore$fill(ChunkAccess chunk,BiomeResolver resolver,Climate.Sampler cached,
                               Blender blender,RandomState random) {
        var source=((NoiseBasedChunkGenerator)(Object)this).getBiomeSource();
        chunk.fillBiomesFromNoise(CoastalTerrainIntegration.coordinateBiomeFill(random,blender,source,resolver),cached);
    }
}
