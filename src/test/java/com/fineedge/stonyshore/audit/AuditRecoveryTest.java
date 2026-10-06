package com.fineedge.stonyshore.audit;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditRecoveryTest {
    @Test void neverQueriesEmptyResourceRoot() {
        List<String> queried = new ArrayList<>();
        var result = AuditResourceScanner.collect(root -> {
            if (root.isEmpty()) throw new ArrayIndexOutOfBoundsException("Provider rejects empty root");
            queried.add(root);
            return Map.of(root + "/sample.json", "data");
        }, (stage, failure) -> fail(stage, failure));
        assertEquals(13, queried.size());
        assertTrue(queried.containsAll(List.of("worldgen/density_function", "worldgen/noise_settings", "forge/biome_modifier", "lithostitched/worldgen_modifier", "lithostitched/biome_injector")));
        assertEquals(13, result.size());
    }
    @Test void brokenResourceDirectoryRetainsOtherDirectoriesAndReportsCause() {
        Map<String, RuntimeException> failures = new LinkedHashMap<>();
        var original = new ArrayIndexOutOfBoundsException("injected provider failure");
        var result = AuditResourceScanner.collect(root -> {
            if (root.equals("worldgen/biome")) throw original;
            return Map.of(root, "data");
        }, failures::put);
        assertEquals(12, result.size());
        assertTrue(result.containsKey("tags/worldgen"));
        assertSame(original, failures.get("resource listing: worldgen/biome"));
    }
    @Test void sectionFailureDoesNotPreventLaterEvidence() throws IOException {
        List<String> stages = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        AuditSections.run("broken", () -> { throw new ArrayIndexOutOfBoundsException("injected"); },
            (name, ex) -> { stages.add(name); assertInstanceOf(ArrayIndexOutOfBoundsException.class, ex); });
        AuditSections.run("next", () -> evidence.add("retained"), (name, ex) -> fail(name, ex));
        assertEquals(List.of("broken"), stages);
        assertEquals(List.of("retained"), evidence);
    }
    @Test void archiveWriteFailuresStillPropagate() {
        var original = new IOException("disk full");
        assertSame(original, assertThrows(IOException.class, () ->
            AuditSections.run("write", () -> { throw original; }, (name, ex) -> fail(name, ex))));
    }
}
