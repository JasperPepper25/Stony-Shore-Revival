package com.fineedge.stonyshore.audit;

import com.google.gson.*;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** An explicit, local-only snapshot of loaded worldgen; never generates or edits chunks. */
public final class ShoreAuditCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private record ExportResult(Path path, int warnings) {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int ENTRY_LIMIT = 2 * 1024 * 1024;
    private static final long TOTAL_LIMIT = 64L * 1024 * 1024;
    private static final Map<ServerLevel,AuditSiteStore<TerrainDiagnostics.Report>> SITES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> CONFIG_ROOTS = Set.of("tectonic", "terrablender", "biome_replacer",
        "biomereplacer", "biolith", "lithostitched", "fragmentum", "terralith", "terrain_slabs", "stonyshorerevival");
    private ShoreAuditCommand() {}

    public static void register(RegisterCommandsEvent event) {
        RecordingCommand.register(event);
        event.getDispatcher().register(Commands.literal("stonyshore").requires(s->s.hasPermission(2))
            .then(Commands.literal("mark").then(Commands.argument("name",StringArgumentType.word()).executes(c->{
                String name=StringArgumentType.getString(c,"name");
                try {
                    if(!name.matches("[a-zA-Z0-9_-]{1,32}"))throw new IllegalArgumentException("Use 1–32 letters, numbers, hyphens or underscores.");
                    var report=TerrainDiagnostics.capture(c.getSource());
                    var store=SITES.computeIfAbsent(c.getSource().getLevel(),k->new AuditSiteStore<>(16));
                    var saved=store.append(name,report);
                    String evicted=saved.evicted();
                    int shore=report.json().get("activeShoreColumns").getAsInt();
                    c.getSource().sendSuccess(()->Component.literal("Saved shore site "+saved.name()+" ("+shore+" active shore columns). Included in /stonyshore audit."
                        +(shore==0?" No shore sampled: move directly over the formation and mark again.":"")
                        +(evicted==null?"":" Oldest site removed: "+evicted)),false);return 1;
                } catch(RuntimeException ex) {
                    LOGGER.error("Shore site capture failed",ex);c.getSource().sendFailure(Component.literal("Site capture failed: "+ex.getMessage()));return 0;
                }
            })))
            .then(Commands.literal("marks").then(Commands.literal("clear").executes(c->{
                var store=SITES.get(c.getSource().getLevel());if(store!=null)store.clear();
                c.getSource().sendSuccess(()->Component.literal("Saved shore sites cleared."),false);return 1;
            }))));
        event.getDispatcher().register(Commands.literal("stonyshore").requires(source -> source.hasPermission(2))
            .then(Commands.literal("audit").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("Exporting loaded worldgen settings..."), false);
                try {
                    ExportResult result = export(context.getSource().getServer(), context.getSource());
                    context.getSource().sendSuccess(() -> Component.literal("Shore audit saved: stonyshore-audits/"
                        + result.path().getFileName() + (result.warnings() == 0 ? "" : " (partial report; " + result.warnings() + " warnings)")
                        + ". Attach this ZIP for compatibility analysis."), false);
                    return 1;
                } catch (Exception ex) {
                    LOGGER.error("Stony Shore audit export failed", ex);
                    context.getSource().sendFailure(Component.literal("Shore audit failed (" + ex.getClass().getSimpleName()
                        + "). Details are in logs/latest.log; please attach that log."));
                    return 0;
                }
            })));
    }

    private static ExportResult export(MinecraftServer server, CommandSourceStack source) throws IOException {
        Path directory = FMLPaths.GAMEDIR.get().resolve("stonyshore-audits");
        Files.createDirectories(directory);
        String stamp = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path output = Files.createTempFile(directory, "shore-audit-" + stamp + "-", ".zip");
        boolean complete = false;
        int warnings;
        try (Archive archive = new Archive(output)) {
            JsonObject info = new JsonObject();
            info.addProperty("format", 8);
            info.addProperty("densityEncoding", "Root holders unwrapped; referenced density keys preserved and exported separately.");
            info.addProperty("createdUtc", Instant.now().toString());
            info.addProperty("scope", "Loaded registry encodings, selected packs, worldgen resource stacks, and allowlisted worldgen configs. Includes command-location X/Y/Z and a sparse nearby loaded-block sample. No player inventories, world seed or existing logs are collected. Export failures include diagnostic stack traces. Runtime mixins may make additional changes not represented here.");
            info.add("selectedPacksInRepositoryOrder", GSON.toJsonTree(server.getPackRepository().getSelectedIds()));
            JsonObject mods = new JsonObject();
            ModList.get().getMods().forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
            info.add("mods", mods);
            archive.json("summary.json", info);
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            archive.section("dimensions", () -> {
                for (var level : server.getAllLevels()) {
                    var generator = level.getChunkSource().getGenerator();
                    JsonObject dimension = new JsonObject();
                    dimension.add("coastalTerrain", CoastalTerrainIntegration.status(level));
                    dimension.addProperty("generatorClass", generator.getClass().getName());
                    dimension.addProperty("biomeSourceClass", generator.getBiomeSource().getClass().getName());
                    dimension.add("generator", archive.encode("dimension generator: " + level.dimension().location(), ChunkGenerator.CODEC, generator, ops));
                    archive.json("resolved/dimensions/" + resourcePath(level.dimension().location()), dimension);
                }
            });
            archive.section("noise settings", () -> {
                var settings = server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
                for (var entry : settings.entrySet())
                    archive.json("resolved/noise_settings/" + resourcePath(entry.getKey().location()),
                        archive.encode("noise settings: " + entry.getKey().location(), NoiseGeneratorSettings.DIRECT_CODEC, AuditDensitySerialization.expand(entry.getValue()), ops));
            });
            archive.section("density functions", () -> {
                var densities = server.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
                for (var entry : densities.entrySet())
                    archive.json("resolved/density_functions/" + resourcePath(entry.getKey().location()),
                        archive.encode("density function: " + entry.getKey().location(), DensityFunction.DIRECT_CODEC, AuditDensitySerialization.expand(entry.getValue()), ops));
            });
            archive.section("stony shore biome and features", () -> {
                var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
                Biome shore = biomes.get(Biomes.STONY_SHORE);
                if (shore != null) {
                    archive.json("resolved/stony_shore.json", archive.encode("stony shore biome", Biome.DIRECT_CODEC, shore, ops));
                    JsonArray features = new JsonArray();
                    var stages = shore.getGenerationSettings().features();
                    for (int stage = 0; stage < stages.size(); stage++) {
                        for (var holder : stages.get(stage)) {
                            JsonObject feature = new JsonObject();
                            feature.addProperty("stageIndex", stage);
                            feature.addProperty("id", holder.unwrapKey().map(key -> key.location().toString()).orElse("inline"));
                            feature.add("placed", archive.encode("placed feature: " + feature.get("id"), PlacedFeature.DIRECT_CODEC, holder.value(), ops));
                            feature.add("configured", archive.encode("configured feature: " + feature.get("id"), ConfiguredFeature.DIRECT_CODEC, holder.value().feature().value(), ops));
                            features.add(feature);
                        }
                    }
                    archive.json("resolved/stony_shore_features.json", features);
                }

            });

            // Resource stack order is retained verbatim, with an explicit effective pack marker.
            JsonArray index = new JsonArray();
            var manager = server.getResourceManager();
            var stacks = new TreeMap<>(AuditResourceScanner.collect(
                root -> manager.listResourceStacks(root, ShoreAuditCommand::worldgenResource), archive::failure));
            for (var entry : stacks.entrySet()) {
                JsonObject record = new JsonObject();
                record.addProperty("id", entry.getKey().toString());
                archive.section("effective resource: " + entry.getKey(), () ->
                    manager.getResource(entry.getKey()).ifPresent(r -> record.addProperty("effectivePack", r.sourcePackId())));
                JsonArray layers = new JsonArray();
                int layer = 0;
                for (Resource resource : entry.getValue()) {
                    String path = "resource-stacks/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath() + "/" + layer++ + ".json";
                    JsonObject item = new JsonObject();
                    item.addProperty("pack", resource.sourcePackId());
                    item.addProperty("archivePath", path);
                    try (InputStream input = resource.open()) { item.addProperty("included", archive.bytes(path, input)); }
                    catch (Exception ex) { archive.failure("read resource: " + path, ex); }
                    layers.add(item);
                }
                record.add("stackInManagerOrder", layers);
                index.add(record);
            }
            archive.json("resource-index.json", index);
            archive.section("common configs", () -> configs(archive, FMLPaths.CONFIGDIR.get(), "configs/common/"));
            archive.section("world configs", () -> configs(archive, server.getWorldPath(LevelResource.ROOT).resolve("serverconfig"), "configs/world/"));
            archive.json("diagnostics/generation-recording.json",GenerationRecording.snapshot(source.getLevel()));
            var sites=SITES.get(source.getLevel());
            if(sites!=null)for(var site:sites.snapshot().entrySet()) {
                String prefix="diagnostics/sites/"+site.getKey()+"/";var report=site.getValue();
                archive.json(prefix+"terrain.json",report.json());
                archive.bytes(prefix+"columns.csv",new ByteArrayInputStream(report.csv().getBytes(StandardCharsets.UTF_8)));
                archive.bytes(prefix+"maps.svg",new ByteArrayInputStream(report.svg().getBytes(StandardCharsets.UTF_8)));
            }
            archive.section("expanded terrain diagnostics", () -> {
                var diagnostics=TerrainDiagnostics.capture(source);
                archive.json("diagnostics/terrain.json",diagnostics.json());
                archive.bytes("diagnostics/columns.csv",new ByteArrayInputStream(diagnostics.csv().getBytes(StandardCharsets.UTF_8)));
                archive.bytes("diagnostics/maps.svg",new ByteArrayInputStream(diagnostics.svg().getBytes(StandardCharsets.UTF_8)));
            });
            archive.section("nearby shore observations", () -> archive.json("observations/nearby-shore.json", ShoreObservations.capture(source)));
            archive.finishReport();
            warnings = archive.errors.size();
            complete = true;
        } finally {
            if (!complete) Files.deleteIfExists(output);
        }
        return new ExportResult(output, warnings);
    }

    private static boolean worldgenResource(ResourceLocation id) {
        String path = id.getPath();
        return path.endsWith(".json") && (path.startsWith("worldgen/density_function/")
            || path.startsWith("worldgen/noise_settings/") || path.startsWith("worldgen/noise/")
            || path.startsWith("worldgen/multi_noise_biome_source_parameter_list/")
            || path.startsWith("worldgen/world_preset/") || path.startsWith("worldgen/biome/")
            || path.startsWith("dimension/")
            || path.startsWith("dimension_type/") || path.startsWith("forge/biome_modifier/")
            || path.startsWith("lithostitched/worldgen_modifier/") || path.startsWith("lithostitched/biome_injector/")
            || path.startsWith("forge/structure_modifier/") || path.startsWith("tags/worldgen/"));
    }

    private static String resourcePath(ResourceLocation id) { return id.getNamespace() + "/" + id.getPath() + ".json"; }

    private static void configs(Archive archive, Path root, String prefix) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root, 4)) {
            for (Path path : paths.sorted().toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                Path relative = root.relativize(path);
                String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
                boolean selected = CONFIG_ROOTS.stream().anyMatch(name -> first.equals(name)
                    || first.startsWith(name + ".") || first.startsWith(name + "-") || first.startsWith(name + "_"));
                String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!selected || !(filename.endsWith(".json") || filename.endsWith(".json5") || filename.endsWith(".toml"))) continue;
                String target = prefix + relative.toString().replace('\\', '/');
                try (InputStream input = Files.newInputStream(path)) { archive.bytes(target, input); }
                catch (IOException ex) { archive.errors.add("Config read failed: " + target); }
            }
        }
    }

    private static final class Archive implements AutoCloseable {
        private final ZipOutputStream zip;
        private long written;
        private final JsonArray failures = new JsonArray();
        private final List<String> errors = new ArrayList<>();
        Archive(Path output) throws IOException { zip = new ZipOutputStream(Files.newOutputStream(output)); }
        void section(String stage, AuditSections.Action action) throws IOException {
            AuditSections.run(stage, action, this::failure);
        }
        void failure(String stage, Exception ex) {
            LOGGER.warn("Stony Shore audit could not export {}", stage, ex);
            errors.add(stage + " (" + ex.getClass().getSimpleName() + ")");
            JsonObject detail = new JsonObject();
            detail.addProperty("stage", stage);
            StringWriter trace = new StringWriter();
            ex.printStackTrace(new PrintWriter(trace));
            detail.addProperty("stackTrace", trace.toString());
            failures.add(detail);
        }
        private <T> JsonElement encode(String stage, Codec<T> codec, T value, RegistryOps<JsonElement> ops) {
            try {
                var result = codec.encodeStart(ops, value);
                if (result.result().isPresent()) return result.result().get();
                JsonObject error = new JsonObject();
                error.addProperty("encodingError", result.error().map(e -> e.message()).orElse("Unknown codec error"));
                errors.add(stage + ": " + error.get("encodingError").getAsString());
                return error;
            } catch (RuntimeException ex) {
                failure(stage, ex);
                JsonObject error = new JsonObject();
                error.addProperty("encodingError", ex.getClass().getSimpleName());
                return error;
            }
        }

        boolean bytes(String path, InputStream input) throws IOException {
            byte[] bytes = input.readNBytes(ENTRY_LIMIT + 1);
            if (bytes.length > ENTRY_LIMIT || written + bytes.length > TOTAL_LIMIT) {
                errors.add("Skipped by size limit: " + path);
                return false;
            }
            put(path, bytes);
            return true;
        }
        void json(String path, JsonElement json) throws IOException {
            bytes(path, new ByteArrayInputStream(GSON.toJson(json).getBytes(StandardCharsets.UTF_8)));
        }
        private void put(String path, byte[] bytes) throws IOException {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(bytes);
            zip.closeEntry();
            written += bytes.length;
        }
        void finishReport() throws IOException {
            JsonObject report = new JsonObject();
            report.addProperty("completeWithoutReadOrSizeErrors", errors.isEmpty());
            report.addProperty("payloadBytes", written);
            report.add("warnings", GSON.toJsonTree(errors));
            report.add("failures", failures);
            // Always retain the completion report, even when payload limits were reached.
            put("completion.json", GSON.toJson(report).getBytes(StandardCharsets.UTF_8));
        }
        public void close() throws IOException { zip.close(); }
    }
}
