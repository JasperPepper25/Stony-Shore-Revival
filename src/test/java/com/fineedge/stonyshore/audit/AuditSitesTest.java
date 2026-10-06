package com.fineedge.stonyshore.audit;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AuditSitesTest {
    @Test void keepsEvidenceAfterTravelAndReportsEviction() {
        var sites=new AuditSiteStore<Integer>(2);assertNull(sites.put("cliff",1));sites.put("cave",2);
        assertEquals("cliff",sites.put("beach",3));assertEquals(List.of("cave","beach"),List.copyOf(sites.snapshot().keySet()));
        sites.put("cave",4);assertEquals(4,sites.snapshot().get("cave"));
        sites.clear();assertTrue(sites.snapshot().isEmpty());
    }
    @Test void labelsCannotEscapeArchivePaths() {
        var sites=new AuditSiteStore<Integer>(2);
        for(String label:new String[]{"../secret","a/b","a\\b","","x".repeat(33)})assertThrows(IllegalArgumentException.class,()->sites.put(label,1));
    }
}
