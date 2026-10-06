package com.fineedge.stonyshore.audit;

import com.fineedge.stonyshore.terrain.*;
import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import java.util.*;

/** Named samples and sections of loaded terrain. No calls that request missing chunks. */
final class TerrainDiagnostics {
    record Report(JsonObject json,String csv,String svg) {}
    static Report capture(CommandSourceStack source) {
        var level=source.getLevel();var origin=BlockPos.containing(source.getPosition());
        var model=CoastalTerrainIntegration.sampler(level);var baseline=CoastalTerrainIntegration.baselineFinal(level);var random=level.getChunkSource().randomState();
        JsonObject result=new JsonObject();result.addProperty("format",1);
        result.addProperty("note","Read-only sparse samples of loaded terrain. Original heights are coarse preliminary-density estimates. Current blocks may include later mods, structures or player edits. Landform verification checks sampled openings and roofs, not full connectivity.");
        result.add("pipeline",CoastalTerrainIntegration.status(level));
        JsonArray rows=new JsonArray(),sections=new JsonArray(),landforms=new JsonArray();
        Set<String> seen=new HashSet<>();int skipped=0;
        StringBuilder csv=new StringBuilder("x,z,biome_at_65,surface_biome,original_height_estimate,planned_height,influence,sand,water_plane,actual_floor,actual_water_top,pool_depth,excavation_to_floor\n");
        StringBuilder svg=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"720\" height=\"760\" viewBox=\"0 0 720 760\"><rect width=\"720\" height=\"760\" fill=\"#18222b\"/><g font-family=\"sans-serif\" font-size=\"15\" fill=\"white\"><text x=\"20\" y=\"25\">Planned elevation</text><text x=\"375\" y=\"25\">Measured ground elevation</text><text x=\"20\" y=\"390\">Coastal influence</text><text x=\"375\" y=\"390\">Sand coverage</text><text x=\"20\" y=\"750\">Hover a cell for coordinates. Gray cells were not loaded.</text></g>");
        int sea=level.getSeaLevel();
        for(int ix=0;ix<17;ix++)for(int iz=0;iz<17;iz++) {
            int x=origin.getX()-32+ix*4,z=origin.getZ()-32+iz*4;
            var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16));
            if(chunk==null) {skipped++;for(int panel=0;panel<4;panel++)cell(svg,panel,ix,iz,"#42474a",x,z,0);continue;}
            int floor=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x&15,z&15)-1;
            int top=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)-1;
            var planned=model==null?null:model.detail(x,z);
            if(planned!=null && planned.mask()==0)planned=null;
            JsonObject row=new JsonObject();row.addProperty("x",x);row.addProperty("z",z);
            row.addProperty("measuredFloorY",floor);row.addProperty("worldSurfaceY",top);
            String biome=chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(65),QuartPos.fromBlock(z))
                .unwrapKey().map(k->k.location().toString()).orElse("unregistered");
            String surfaceBiome=chunk.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(floor),QuartPos.fromBlock(z))
                .unwrapKey().map(k->k.location().toString()).orElse("unregistered");
            row.addProperty("biomeAt65",biome);row.addProperty("biomeAtMeasuredFloor",surfaceBiome);
            var climate=random.sampler().sample(QuartPos.fromBlock(x),QuartPos.fromBlock(floor),QuartPos.fromBlock(z));
            JsonObject values=new JsonObject();values.addProperty("temperature",climate.temperature()/10000.0);
            values.addProperty("humidity",climate.humidity()/10000.0);values.addProperty("continentalness",climate.continentalness()/10000.0);
            values.addProperty("erosion",climate.erosion()/10000.0);values.addProperty("depth",climate.depth()/10000.0);values.addProperty("weirdness",climate.weirdness()/10000.0);
            row.add("climateAtMeasuredFloor",values);
            int waterTop=Integer.MIN_VALUE,waterPlane=planned==null?sea:planned.water();
            for(int y=Math.max(level.getMinBuildHeight(),waterPlane-7);y<=Math.min(level.getMaxBuildHeight()-1,waterPlane+2);y++)
                if(chunk.getBlockState(new BlockPos(x,y,z)).is(Blocks.WATER))waterTop=Math.max(waterTop,y);
            if(waterTop!=Integer.MIN_VALUE)row.addProperty("measuredWaterTopY",waterTop);
            boolean intended=planned!=null && model.waterCandidate(x,waterPlane-1,z);
            row.addProperty("expectsWaterAtPlaneMinus1",intended);
            row.addProperty("waterAtExpectedLevel",intended && chunk.getBlockState(new BlockPos(x,waterPlane-1,z)).is(Blocks.WATER));
            if(planned!=null) {
                row.addProperty("originalHeightEstimate",planned.original());row.addProperty("plannedSurface",planned.surface());
                row.addProperty("influence",planned.mask());row.addProperty("sandStrength",planned.sand());row.addProperty("plannedWaterPlane",waterPlane);
                row.addProperty("plannedVersusMeasuredFloorDelta",floor-planned.surface());
                row.addProperty("excavationFromOriginalEstimateToFloor",planned.original()-floor);
                if(planned.pool()!=null) {row.addProperty("poolDepthBelowWaterPlane",waterPlane-1-floor);row.addProperty("plannedPoolDepth",planned.pool().depth());}
                if(planned.arch()!=null && seen.add("arch:"+planned.arch().x()+":"+planned.arch().z()))
                    landforms.add(archCheck(source,planned.arch()));
                if(planned.overhang()!=null && seen.add("overhang:"+planned.overhang().x()+":"+planned.overhang().z()))
                    landforms.add(overhangCheck(source,planned.overhang()));
            }
            rows.add(row);
            csv.append(x).append(',').append(z).append(',').append(biome).append(',').append(surfaceBiome).append(',')
                .append(planned==null?"":planned.original()).append(',').append(planned==null?"":planned.surface()).append(',')
                .append(planned==null?0:planned.mask()).append(',').append(planned==null?0:planned.sand()).append(',').append(waterPlane).append(',')
                .append(floor).append(',').append(waterTop==Integer.MIN_VALUE?"":waterTop).append(',')
                .append(planned==null||planned.pool()==null?"":waterPlane-1-floor).append(',').append(planned==null?"":planned.original()-floor).append('\n');
            cell(svg,0,ix,iz,color(planned==null?floor:planned.surface(),sea),x,z,planned==null?floor:planned.surface());
            cell(svg,1,ix,iz,color(floor,sea),x,z,floor);
            cell(svg,2,ix,iz,gray(planned==null?0:planned.mask()),x,z,planned==null?0:planned.mask());
            cell(svg,3,ix,iz,gray(planned==null?0:planned.sand()),x,z,planned==null?0:planned.sand());
        }
        // Two full vertical slices. Actual and predicted solid/air can be compared at each point.
        for(int axis=0;axis<2;axis++) {
            JsonObject section=new JsonObject();section.addProperty("axis",axis==0?"X":"Z");JsonArray points=new JsonArray();
            int minY=Math.max(level.getMinBuildHeight(),sea-12),maxY=Math.min(level.getMaxBuildHeight()-1,Math.max(sea+40,origin.getY()+24));
            section.addProperty("minY",minY);section.addProperty("maxY",maxY);section.addProperty("verticalSpacing",2);
            for(int offset=-32;offset<=32;offset+=2) {
                int x=origin.getX()+(axis==0?offset:0),z=origin.getZ()+(axis==1?offset:0);
                var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16));if(chunk==null)continue;
                JsonObject line=new JsonObject();line.addProperty("x",x);line.addProperty("z",z);JsonArray samples=new JsonArray();
                for(int y=minY;y<=maxY;y+=2) {
                    var ctx=new DensityFunction.SinglePointContext(x,y,z);JsonArray sample=new JsonArray();sample.add(y);
                    sample.add(random.router().finalDensity().compute(ctx));
                    if(baseline==null)sample.add(JsonNull.INSTANCE);else sample.add(baseline.compute(ctx));
                    sample.add(chunk.getBlockState(new BlockPos(x,y,z)).toString());samples.add(sample);
                }
                line.add("samples",samples);points.add(line);
            }
            section.addProperty("sampleFormat","[y,modifiedRawDensity,originalRawDensity,actualBlockState]");section.add("columns",points);sections.add(section);
        }
        result.add("columns",rows);result.add("crossSections",sections);result.add("landformChecks",landforms);result.addProperty("skippedUnloadedColumns",skipped);
        svg.append("</svg>");return new Report(result,csv.toString(),svg.toString());
    }
    private static JsonObject archCheck(CommandSourceStack source,CoastalLandforms.Arch a) {
        JsonObject j=new JsonObject();j.addProperty("type","arch");j.addProperty("centerX",a.x());j.addProperty("centerZ",a.z());
        j.addProperty("height",a.height());j.addProperty("width",a.width());j.addProperty("length",a.length());
        j.add("openingCenter",block(source,a.x(),(int)(a.sea()+a.height()*0.43),a.z()));
        j.add("roof",block(source,a.x(),(int)Math.ceil(a.sea()+a.height()+3),a.z()));
        for(int sign:new int[]{-1,1})j.add(sign<0?"portalA":"portalB",block(source,
            (int)Math.round(a.x()-sign*Math.sin(a.angle())*(a.length()+2)),(int)(a.sea()+a.height()*0.43),
            (int)Math.round(a.z()+sign*Math.cos(a.angle())*(a.length()+2))));return j;
    }
    private static JsonObject overhangCheck(CommandSourceStack source,CoastalLandforms.Overhang a) {
        JsonObject j=new JsonObject();j.addProperty("type","overhang");j.addProperty("centerX",a.x());j.addProperty("centerZ",a.z());
        j.addProperty("floorY",a.floor());j.addProperty("height",a.height());j.add("openingCenter",block(source,a.x(),a.floor()+5,a.z()));
        j.add("roof",block(source,a.x(),a.floor()+(int)a.height()+1,a.z()));return j;
    }
    private static JsonObject block(CommandSourceStack source,int x,int y,int z) {
        JsonObject result=new JsonObject();result.addProperty("x",x);result.addProperty("y",y);result.addProperty("z",z);
        var chunk=source.getLevel().getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16));
        result.addProperty("loaded",chunk!=null);if(chunk==null)return result;
        var state=chunk.getBlockState(new BlockPos(x,y,z));result.addProperty("state",state.toString());
        result.addProperty("open",state.isAir() || !state.getFluidState().isEmpty());return result;
    }
    private static String gray(double v) {int c=40+(int)(Math.max(0,Math.min(1,v))*200);return String.format("#%02x%02x%02x",c,c,c);}
    private static String color(double y,int sea) {
        if(y<sea)return "#347cbd";int c=(int)Math.max(30,Math.min(230,70+(y-sea)*2));return String.format("#%02x%02x%02x",c,Math.min(255,c+15),Math.max(0,c-20));
    }
    private static void cell(StringBuilder svg,int panel,int ix,int iz,String color,int x,int z,double v) {
        int px=20+(panel%2)*355+ix*19,py=40+(panel/2)*365+iz*19;
        svg.append("<rect x=\"").append(px).append("\" y=\"").append(py).append("\" width=\"18\" height=\"18\" fill=\"").append(color)
            .append("\"><title>X=").append(x).append(" Z=").append(z).append(" value=").append(v).append("</title></rect>");
    }
    private TerrainDiagnostics() {}
}
