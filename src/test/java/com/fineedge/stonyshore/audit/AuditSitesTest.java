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
    @Test void repeatedMarksPreserveBothCapturesAndKeepLabelsBounded() {
        var sites=new AuditSiteStore<Integer>(4);
        assertEquals("cave",sites.append("cave",1).name());
        assertEquals("cave_2",sites.append("cave",2).name());
        assertEquals(1,sites.snapshot().get("cave"));assertEquals(2,sites.snapshot().get("cave_2"));
        String longName="x".repeat(32);sites.append(longName,3);
        assertEquals(32,sites.append(longName,4).name().length());
        assertEquals("cave",sites.append("cliff",5).evicted());
    }
    @Test void labelsCannotEscapeArchivePaths() {
        var sites=new AuditSiteStore<Integer>(2);
        for(String label:new String[]{"../secret","a/b","a\\b","","x".repeat(33)})assertThrows(IllegalArgumentException.class,()->sites.put(label,1));
    }
}
