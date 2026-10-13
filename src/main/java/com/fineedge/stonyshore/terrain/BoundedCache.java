package com.fineedge.stonyshore.terrain;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * Bounded memoization of immutable, deterministic plans shared across workers.
 * Factories run outside every cache monitor and may reenter this or another cache.
 * Concurrent misses may compute the same plan; they never wait for another factory.
 */
public final class BoundedCache<K,V> {
    private static final int MAX_STRIPES=16, MIN_STRIPE_CAPACITY=64;

    /** Mix the full coordinate pair, rather than Long.hashCode's x XOR z. */
    private static int hash(Object key) {
        long value=key instanceof Long coordinate?coordinate:Objects.hashCode(key);
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        value^=value>>>31;
        return (int)(value^(value>>>32));
    }
    private static final class HashedKey<K> {
        final K key;
        final int hash;
        HashedKey(K key,int hash) { this.key=key;this.hash=hash; }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof HashedKey<?> that && Objects.equals(key,that.key);
        }
    }
    private record Entry<K,V>(K key,V value) {}
    private static final class Stripe<K,V> {
        final LinkedHashMap<HashedKey<K>,Entry<K,V>> entries;
        final int capacity;
        volatile Entry<K,V> recent;
        Stripe(int capacity) {
            this.capacity=capacity;
            entries=new LinkedHashMap<>(Math.min(64,capacity),0.75f,true);
        }
    }
    private final Stripe<K,V>[] stripes;
    private final int stripeShift;
    private final LongAdder hits=new LongAdder(), misses=new LongAdder(), recentHits=new LongAdder();

    @SuppressWarnings("unchecked")
    public BoundedCache(int capacity) {
        if(capacity<=0)throw new IllegalArgumentException("Cache capacity must be positive");
        int count=Integer.highestOneBit(Math.min(MAX_STRIPES,Math.max(1,capacity/MIN_STRIPE_CAPACITY)));
        stripes=(Stripe<K,V>[])new Stripe<?,?>[count];
        stripeShift=32-Integer.numberOfTrailingZeros(count);
        for(int i=0;i<count;i++)
            stripes[i]=new Stripe<>(capacity/count+(i<capacity%count?1:0));
    }
    public V get(K key,Function<K,V> factory) {
        int hash=hash(key);
        // A Java shift by 32 is a shift by zero; handle the single-stripe case explicitly.
        Stripe<K,V> stripe=stripes[stripes.length==1?0:hash>>>stripeShift];
        Entry<K,V> recent=stripe.recent;
        // The recent entry is already most recently used. Repeated vertical samples
        // need neither a monitor nor another access-order update or lookup wrapper.
        if(recent!=null && Objects.equals(key,recent.key())) {
            hits.increment();recentHits.increment();return recent.value();
        }
        HashedKey<K> lookup=new HashedKey<>(key,hash);
        synchronized(stripe.entries) {
            Entry<K,V> entry=stripe.entries.get(lookup);
            if(entry!=null) {
                stripe.recent=entry;hits.increment();return entry.value();
            }
        }
        misses.increment();
        V created=Objects.requireNonNull(factory.apply(key),"Cache factories must return a non-null plan");
        synchronized(stripe.entries) {
            Entry<K,V> entry=stripe.entries.get(lookup);
            if(entry==null) {
                entry=new Entry<>(key,created);
                stripe.entries.put(lookup,entry);
                if(stripe.entries.size()>stripe.capacity)
                    stripe.entries.remove(stripe.entries.keySet().iterator().next());
            }
            stripe.recent=entry;
            return entry.value();
        }
    }
    public long hits() { return hits.sum(); }
    public long misses() { return misses.sum(); }
    public long recentHits() { return recentHits.sum(); }
    /** Shared entries only; recent references point at these same immutable entries. */
    public int size() {
        int size=0;
        for(var stripe:stripes)synchronized(stripe.entries) { size+=stripe.entries.size(); }
        return size;
    }
}
