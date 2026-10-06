package com.fineedge.stonyshore.audit;

import com.google.gson.*;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import java.time.Instant;
import java.util.*;

/** Optional, bounded session data. No per-density-call logging and no world/chunk requests. */
public final class GenerationRecording {
    private static final int LIMIT=256;
    private static final Map<ServerLevel,Session> SESSIONS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<RandomState,Session> RANDOM=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class ChunkSample {
        final int x,z;final long firstNoise;
        long featureNanos,loadedElapsedNanos;int featurePasses,noiseConstructions;
        ChunkSample(int x,int z) {this.x=x;this.z=z;firstNoise=System.nanoTime();}
    }
    private static final class Session {
        boolean active=true;final String started=Instant.now().toString();String stopped;
        final LinkedHashMap<Long,ChunkSample> chunks=new LinkedHashMap<>();
        final JsonObject initialMetrics;long evicted;
        Session(JsonObject metrics) {initialMetrics=metrics;}
        synchronized ChunkSample chunk(int x,int z) {
            long key=ChunkPos.asLong(x,z);var sample=chunks.get(key);
            if(sample==null) {
                sample=new ChunkSample(x,z);chunks.put(key,sample);
                if(chunks.size()>LIMIT) {chunks.remove(chunks.keySet().iterator().next());evicted++;}
            }
            return sample;
        }
    }
    public static void start(ServerLevel level) {
        Session s=new Session(CoastalTerrainIntegration.status(level));
        SESSIONS.put(level,s);RANDOM.put(level.getChunkSource().randomState(),s);
    }
    public static void stop(ServerLevel level) {
        var s=SESSIONS.get(level);if(s!=null)synchronized(s){s.active=false;s.stopped=Instant.now().toString();}
    }
    public static boolean active(ServerLevel level) {
        var s=SESSIONS.get(level);if(s==null)return false;synchronized(s){return s.active;}
    }
    public static void noiseStarted(RandomState random,int blockX,int blockZ) {
        var s=RANDOM.get(random);if(s==null)return;
        synchronized(s){if(s.active)s.chunk(Math.floorDiv(blockX,16),Math.floorDiv(blockZ,16)).noiseConstructions++;}
    }
    public static void feature(ServerLevel level,ChunkPos chunk,long nanos) {
        var s=SESSIONS.get(level);if(s==null)return;
        synchronized(s){if(s.active){var c=s.chunk(chunk.x,chunk.z);c.featureNanos+=nanos;c.featurePasses++;}}
    }
    public static void loaded(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var s=SESSIONS.get(level);if(s==null)return;var pos=event.getChunk().getPos();
        synchronized(s){var c=s.chunks.get(pos.toLong());if(s.active && c!=null)c.loadedElapsedNanos=System.nanoTime()-c.firstNoise;}
    }
    public static JsonObject snapshot(ServerLevel level) {
        JsonObject result=new JsonObject();var s=SESSIONS.get(level);
        if(s==null){result.addProperty("status","No recording. Use /stonyshore record start before exploring new chunks.");return result;}
        synchronized(s) {
            result.addProperty("active",s.active);result.addProperty("startedUtc",s.started);result.addProperty("stoppedUtc",s.stopped);
            result.addProperty("maxRetainedChunks",LIMIT);result.addProperty("evictedSamples",s.evicted);
            result.addProperty("timingNote","featureNanos measures our feature work. constructorToLoadedNanos is elapsed wall time including all generation, dependency waits and scheduling; neither is isolated noise CPU time. Noise construction may also be a height query, and samples are not unique new-world chunks.");
            result.add("metricsAtStart",s.initialMetrics);result.add("metricsAtExport",CoastalTerrainIntegration.status(level));
            JsonArray samples=new JsonArray();for(var c:s.chunks.values()) {
                JsonObject row=new JsonObject();row.addProperty("chunkX",c.x);row.addProperty("chunkZ",c.z);
                row.addProperty("noiseConstructions",c.noiseConstructions);row.addProperty("featurePasses",c.featurePasses);
                row.addProperty("featureNanos",c.featureNanos);
                if(c.loadedElapsedNanos>0)row.addProperty("constructorToLoadedNanos",c.loadedElapsedNanos);
                samples.add(row);
            }
            result.add("chunks",samples);return result;
        }
    }
    private GenerationRecording() {}
}
