package com.fineedge.stonyshore.audit;

import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only observations of already loaded chunks; never requests terrain generation. */
final class ShoreObservations {
    private ShoreObservations() {}
    static JsonObject capture(CommandSourceStack source) {
        JsonObject result=new JsonObject();
        if(source.getEntity()==null) {
            result.addProperty("status","No entity command location; run the audit in-game near the shore.");
            return result;
        }
        var level=source.getLevel();
        var origin=BlockPos.containing(source.getPosition());
        int sea=level.getSeaLevel();
        int minY=origin.getY()>sea+24 ? Math.max(level.getMinBuildHeight(),origin.getY()-32) : sea-4;
        int maxY=Math.min(level.getMaxBuildHeight()-1,origin.getY()>sea+24 ? origin.getY()+16 : sea+24);
        result.addProperty("dimension",level.dimension().location().toString());
        result.addProperty("centerY",origin.getY()); result.addProperty("centerX",origin.getX()); result.addProperty("centerZ",origin.getZ());
        result.addProperty("seaLevel",sea); result.addProperty("minSampleY",minY); result.addProperty("maxSampleY",maxY);
        result.addProperty("spacingBlocks",2);
        result.addProperty("rowFormat","[x,z,worldSurfaceTopY,biomeAtSeaPlus2,plannedSurfaceOrNull,active,sandStrength,blockPaletteIndicesFromMinToMaxY,plannedWaterLevelOrNull,archCandidate]");
        result.addProperty("note","Current blocks, including later feature/player changes. Plan is a raw-density estimate, not measured terrain. Unloaded chunks are skipped. Camera height does not measure basin floor height.");
        Map<String,Integer> palette=new LinkedHashMap<>(); JsonArray rows=new JsonArray();
        int skipped=0;
        for(int x=origin.getX()-32;x<=origin.getX()+32;x+=2) for(int z=origin.getZ()-32;z<=origin.getZ()+32;z+=2) {
            var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16));
            if(chunk==null) { skipped++; continue; }
            JsonArray row=new JsonArray(); row.add(x); row.add(z);
            row.add(level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1);
            row.add(level.getBiome(new BlockPos(x,sea+2,z)).unwrapKey().map(k->k.location().toString()).orElse("unregistered"));
            var column=CoastalTerrainIntegration.column(level,x,z);
            if(column==null || !column.active()) row.add(JsonNull.INSTANCE); else row.add(column.surface());
            row.add(column!=null && column.active()); row.add(column==null ? 0 : column.sandStrength());
            JsonArray blocks=new JsonArray();
            for(int y=minY;y<=maxY;y++) {
                String state=chunk.getBlockState(new BlockPos(x,y,z)).toString();
                blocks.add(palette.computeIfAbsent(state,k->palette.size()));
            }
            row.add(blocks);
            if(column==null)row.add(JsonNull.INSTANCE);else row.add(column.waterLevel());
            row.add(CoastalTerrainIntegration.arch(level,x,z)!=null);
            rows.add(row);
        }
        JsonArray states=new JsonArray();palette.keySet().forEach(states::add);
        result.add("blockPalette",states);result.add("columns",rows);result.addProperty("skippedUnloadedColumns",skipped);
        return result;
    }
}
