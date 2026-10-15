package com.fineedge.stonyshore.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Query concrete data directories: some generated pack providers reject an empty root. */
final class AuditResourceScanner {
    private static final List<String> ROOTS = List.of(
        "worldgen/density_function", "worldgen/noise_settings", "worldgen/noise",
        "worldgen/world_preset", "worldgen/multi_noise_biome_source_parameter_list", "worldgen/biome", "dimension", "dimension_type",
        "forge/biome_modifier", "forge/structure_modifier", "lithostitched/worldgen_modifier", "lithostitched/biome_injector", "tags/worldgen");
    private AuditResourceScanner() {}

    static <K, V> Map<K, V> collect(Function<String, Map<K, V>> query,
                                   BiConsumer<String, RuntimeException> onFailure) {
        Map<K, V> result = new LinkedHashMap<>();
        for (String root : ROOTS) {
            try { result.putAll(query.apply(root)); }
            catch (RuntimeException ex) { onFailure.accept("resource listing: " + root, ex); }
        }
        return result;
    }
}
