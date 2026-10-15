package com.fineedge.stonyshore.terrain;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongFunction;

/**
 * Bounded shared LRU segments for immutable, deterministic coordinate plans.
 * Keys remain primitive through lookup and factory invocation. Factories always
 * run outside monitors; nested misses never wait for another worker's factory.
 * Optional worker fronts retain a fixed number of values, including after shared
 * eviction. They contain no references to this cache, its map or world model.
 */
public final class BoundedLongCache<V> {
    private static final int MAX_STRIPES=16, MIN_STRIPE_CAPACITY=64;
    private record Entry<V>(long key,V value) {}
    private static final class Stripe<V> {
        final Long2ObjectLinkedOpenHashMap<Entry<V>> entries;
        final int capacity;
        volatile Entry<V> recent;
        Stripe(int capacity) {
            this.capacity=capacity;
            entries=new Long2ObjectLinkedOpenHashMap<>(Math.min(64,capacity));
        }
    }
    private static final class Local<V> {
        final long[] keys;
        final Object[] values;
        long lastKey;
        V lastValue;
        Local(int size) { keys=new long[size];values=new Object[size]; }
        @SuppressWarnings("unchecked") V find(long key,int hash) {
            int slot=hash&(keys.length-1);
            V value=keys[slot]==key?(V)values[slot]:null;
            if(value!=null) {lastKey=key;lastValue=value;}
            return value;
        }
        void remember(long key,int hash,V value) {
            int slot=hash&(keys.length-1);keys[slot]=key;values[slot]=value;
            lastKey=key;lastValue=value;
        }
    }
    private final Stripe<V>[] stripes;
    private final int stripeShift;
    private final ThreadLocal<Local<V>> locals;
    // One counter increment per hit; a recent/local hit need not also update a
    // second shared counter. hits() combines the independently counted paths.
    private final LongAdder sharedHits=new LongAdder(), recentHits=new LongAdder(),
        localHits=new LongAdder(), misses=new LongAdder();

    public BoundedLongCache(int capacity) { this(capacity,0); }
    @SuppressWarnings("unchecked")
    public BoundedLongCache(int capacity,int localCapacity) {
        if(capacity<=0)throw new IllegalArgumentException("Cache capacity must be positive");
        if(localCapacity<0 || localCapacity>32 || localCapacity!=0 && Integer.bitCount(localCapacity)!=1)
            throw new IllegalArgumentException("Worker front capacity must be zero or a power of two up to 32");
        int count=Integer.highestOneBit(Math.min(MAX_STRIPES,Math.max(1,capacity/MIN_STRIPE_CAPACITY)));
        stripes=(Stripe<V>[])new Stripe<?>[count];
        stripeShift=32-Integer.numberOfTrailingZeros(count);
        for(int i=0;i<count;i++)stripes[i]=new Stripe<>(capacity/count+(i<capacity%count?1:0));
        // The initializer captures only the integer limit, not the cache/world.
        locals=localCapacity==0?null:ThreadLocal.withInitial(()->new Local<>(localCapacity));
    }
    private static int hash(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        value^=value>>>31;
        return (int)(value^(value>>>32));
    }
    public V get(long key,LongFunction<V> factory) {
        Local<V> local=locals==null?null:locals.get();
        if(local!=null && local.lastValue!=null && local.lastKey==key) {
            localHits.increment();return local.lastValue;
        }
        int hash=hash(key);
        if(local!=null) {
            V value=local.find(key,hash);
            if(value!=null) {localHits.increment();return value;}
        }
        Stripe<V> stripe=stripes[stripes.length==1?0:hash>>>stripeShift];
        Entry<V> recent=stripe.recent;
        if(recent!=null && recent.key()==key) {
            recentHits.increment();
            if(local!=null)local.remember(key,hash,recent.value());
            return recent.value();
        }
        Entry<V> entry;
        synchronized(stripe.entries) {
            entry=stripe.entries.getAndMoveToLast(key);
            if(entry!=null) {stripe.recent=entry;sharedHits.increment();}
        }
        if(entry==null) {
            misses.increment();
            V created=Objects.requireNonNull(factory.apply(key),"Cache factories must return a non-null plan");
            synchronized(stripe.entries) {
                entry=stripe.entries.getAndMoveToLast(key);
                if(entry==null) {
                    entry=new Entry<>(key,created);stripe.entries.putAndMoveToLast(key,entry);
                    if(stripe.entries.size()>stripe.capacity)stripe.entries.removeFirst();
                }
                stripe.recent=entry;
            }
        }
        if(local!=null)local.remember(key,hash,entry.value());
        return entry.value();
    }
    public long hits() { return sharedHits.sum()+recentHits.sum()+localHits.sum(); }
    public long misses() { return misses.sum(); }
    public long recentHits() { return recentHits.sum(); }
    public long localHits() { return localHits.sum(); }
    /** Shared entry count; worker fronts have their separately specified fixed limits. */
    public int size() {
        int size=0;
        for(var stripe:stripes)synchronized(stripe.entries) {size+=stripe.entries.size();}
        return size;
    }
}
