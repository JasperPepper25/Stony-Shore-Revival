package com.fineedge.stonyshore.audit;

import java.util.*;

/** Bounded labeled evidence: repeated labels receive suffixes; old sites are explicitly evicted. */
final class AuditSiteStore<T> {
    private final LinkedHashMap<String,T> sites=new LinkedHashMap<>();
    private final int limit;
    AuditSiteStore(int limit) { this.limit=limit; }
    record Saved(String name,String evicted) {}
    synchronized Saved append(String requested,T value) {
        if(!requested.matches("[a-zA-Z0-9_-]{1,32}"))throw new IllegalArgumentException("Use 1-32 letters, numbers, hyphens or underscores.");
        String name=requested;int suffix=2;
        while(sites.containsKey(name)) {
            String tail="_"+suffix++;name=requested.substring(0,Math.min(requested.length(),32-tail.length()))+tail;
        }
        return new Saved(name,put(name,value));
    }
    synchronized String put(String name,T value) {
        if(!name.matches("[a-zA-Z0-9_-]{1,32}"))throw new IllegalArgumentException("Use 1–32 letters, numbers, hyphens or underscores.");
        sites.remove(name);sites.put(name,value);
        if(sites.size()>limit){String oldest=sites.keySet().iterator().next();sites.remove(oldest);return oldest;}
        return null;
    }
    synchronized Map<String,T> snapshot() { return new LinkedHashMap<>(sites); }
    synchronized void clear() { sites.clear(); }
}
