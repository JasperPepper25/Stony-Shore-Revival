package com.fineedge.stonyshore.terrain;

import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.concurrent.atomic.LongAdder;

/** Shared across workers; expensive factories run outside the monitor, with no chunk reads. */
public final class BoundedCache<K,V> {
    private final LinkedHashMap<K,V> entries = new LinkedHashMap<>(64,0.75f,true);
    private final int capacity;
    private final LongAdder hits=new LongAdder(), misses=new LongAdder();
    public BoundedCache(int capacity) { this.capacity=capacity; }
    public V get(K key, Function<K,V> factory) {
        synchronized(entries) {
            V value=entries.get(key);
            if(value!=null) { hits.increment(); return value; }
        }
        misses.increment();
        V created=factory.apply(key);
        synchronized(entries) {
            V existing=entries.get(key);
            if(existing!=null) return existing;
            entries.put(key,created);
            if(entries.size()>capacity) entries.remove(entries.keySet().iterator().next());
        }
        return created;
    }
    public long hits() { return hits.sum(); }
    public long misses() { return misses.sum(); }
    public int size() { synchronized(entries) { return entries.size(); } }
}
