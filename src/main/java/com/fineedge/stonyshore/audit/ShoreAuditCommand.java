package com.fineedge.stonyshore.audit;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.Commands;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int ENTRY_LIMIT = 2 * 1024 * 1024;
    private static final long TOTAL_LIMIT = 64L * 1024 * 1024;
    private static final Set<String> CONFIG_ROOTS = Set.of("tectonic", "terrablender", "biome_replacer",
        "biomereplacer", "biolith", "lithostitched", "fragmentum", "terralith", "terrain_slabs", "stonyshorerevival");
    private ShoreAuditCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stonyshore").requires(source -> source.hasPermission(2))
            .then(Commands.literal("audit").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("Exporting loaded worldgen settings..."), false);
                try {
                    Path result = export(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("Shore audit saved: stonyshore-audits/"
                        + result.getFileName() + ". Attach this ZIP for compatibility analysis."), false);
                    return 1;
                } catch (Exception ex) {
                    context.getSource().sendFailure(Component.literal("Shore audit failed (" + ex.getClass().getSimpleName()
                        + "). Check that the instance folder is writable."));
                    return 0;
                }
            })));
    }

    private static Path export(MinecraftServer server) throws IOException {
        Path directory = FMLPaths.GAMEDIR.get().resolve("stonyshore-audits");
        Files.createDirectories(directory);
        String stamp = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path output = Files.createTempFile(directory, "shore-audit-" + stamp + "-", ".zip");
        boolean complete = false;
        try (Archive archive = new Archive(output)) {
            JsonObject info = new JsonObject();
            info.addProperty("format", 1);
            info.addProperty("createdUtc", Instant.now().toString());
            info.addProperty("scope", "Loaded registry encodings, selected packs, worldgen resource stacks, and allowlisted worldgen configs. No chunks, player data, world seed or logs are exported. Runtime mixins may make additional changes not represented here.");
            info.add("selectedPacksInRepositoryOrder", GSON.toJsonTree(server.getPackRepository().getSelectedIds()));
            JsonObject mods = new JsonObject();
            ModList.get().getMods().forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
            info.add("mods", mods);
            archive.json("summary.json", info);
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            for (var level : server.getAllLevels()) {
                var generator = level.getChunkSource().getGenerator();
                JsonObject dimension = new JsonObject();
                dimension.addProperty("generatorClass", generator.getClass().getName());
                dimension.addProperty("biomeSourceClass", generator.getBiomeSource().getClass().getName());
                dimension.add("generator", encode(ChunkGenerator.CODEC, generator, ops));
                archive.json("resolved/dimensions/" + resourcePath(level.dimension().location()), dimension);
            }
            var settings = server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
            for (var entry : settings.entrySet())
                archive.json("resolved/noise_settings/" + resourcePath(entry.getKey().location()),
                    encode(NoiseGeneratorSettings.DIRECT_CODEC, entry.getValue(), ops));
            var densities = server.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
            for (var entry : densities.entrySet())
                archive.json("resolved/density_functions/" + resourcePath(entry.getKey().location()),
                    encode(DensityFunction.DIRECT_CODEC, entry.getValue(), ops));
            var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
            Biome shore = biomes.get(Biomes.STONY_SHORE);
            if (shore != null) {
                archive.json("resolved/stony_shore.json", encode(Biome.DIRECT_CODEC, shore, ops));
                JsonArray features = new JsonArray();
                var stages = shore.getGenerationSettings().features();
                for (int stage = 0; stage < stages.size(); stage++) {
                    for (var holder : stages.get(stage)) {
                        JsonObject feature = new JsonObject();
                        feature.addProperty("stageIndex", stage);
                        feature.addProperty("id", holder.unwrapKey().map(key -> key.location().toString()).orElse("inline"));
                        feature.add("placed", encode(PlacedFeature.DIRECT_CODEC, holder.value(), ops));
                        feature.add("configured", encode(ConfiguredFeature.DIRECT_CODEC, holder.value().feature().value(), ops));
                        features.add(feature);
                    }
                }
                archive.json("resolved/stony_shore_features.json", features);
            }

            // Resource stack order is retained verbatim, with an explicit effective pack marker.
            JsonArray index = new JsonArray();
            var manager = server.getResourceManager();
            var stacks = new TreeMap<>(manager.listResourceStacks("", ShoreAuditCommand::worldgenResource));
            for (var entry : stacks.entrySet()) {
                JsonObject record = new JsonObject();
                record.addProperty("id", entry.getKey().toString());
                manager.getResource(entry.getKey()).ifPresent(r -> record.addProperty("effectivePack", r.sourcePackId()));
                JsonArray layers = new JsonArray();
                int layer = 0;
                for (Resource resource : entry.getValue()) {
                    String path = "resource-stacks/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath() + "/" + layer++ + ".json";
                    JsonObject item = new JsonObject();
                    item.addProperty("pack", resource.sourcePackId());
                    item.addProperty("archivePath", path);
                    try (InputStream input = resource.open()) { item.addProperty("included", archive.bytes(path, input)); }
                    catch (Exception ex) { archive.errors.add("Read failed: " + path + " (" + ex.getClass().getSimpleName() + ")"); }
                    layers.add(item);
                }
                record.add("stackInManagerOrder", layers);
                index.add(record);
            }
            archive.json("resource-index.json", index);
            configs(archive, FMLPaths.CONFIGDIR.get(), "configs/common/");
            configs(archive, server.getWorldPath(LevelResource.ROOT).resolve("serverconfig"), "configs/world/");
            archive.finishReport();
            complete = true;
        } finally {
            if (!complete) Files.deleteIfExists(output);
        }
        return output;
    }

    private static <T> JsonElement encode(Codec<T> codec, T value, RegistryOps<JsonElement> ops) {
        try {
            var result = codec.encodeStart(ops, value);
            if (result.result().isPresent()) return result.result().get();
            JsonObject error = new JsonObject();
            error.addProperty("encodingError", result.error().map(e -> e.message()).orElse("Unknown codec error"));
            return error;
        } catch (RuntimeException ex) {
            JsonObject error = new JsonObject();
            error.addProperty("encodingError", ex.getClass().getSimpleName());
            return error;
        }
    }

    private static boolean worldgenResource(ResourceLocation id) {
        String path = id.getPath();
        return path.endsWith(".json") && (path.startsWith("worldgen/density_function/")
            || path.startsWith("worldgen/noise_settings/") || path.startsWith("worldgen/noise/")
            || path.startsWith("worldgen/world_preset/") || path.startsWith("worldgen/biome/")
            || path.startsWith("dimension/")
            || path.startsWith("dimension_type/") || path.startsWith("forge/biome_modifier/")
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
        private final List<String> errors = new ArrayList<>();
        Archive(Path output) throws IOException { zip = new ZipOutputStream(Files.newOutputStream(output)); }
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
            // Always retain the completion report, even when payload limits were reached.
            put("completion.json", GSON.toJson(report).getBytes(StandardCharsets.UTF_8));
        }
        public void close() throws IOException { zip.close(); }
    }
}
