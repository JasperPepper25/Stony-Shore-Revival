package com.fineedge.stonyshore.terrain;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongFunction;

/**
 * Bounded coordinate plans with monitor-free reads of immutable bucket snapshots.
 * Factories run outside all monitors; only miss publication locks its two small
 * candidate buckets, in a fixed order. Two placements reduce collision eviction.
 * Buckets retain their most recently published plans, without access-order writes.
 * Values must be immutable and deterministic: eviction can cause recomputation.
 */
public final class BoundedReadCache<V> {
    private static final int MAX_BUCKET_CAPACITY=8;
    private record Entry<V>(long key,V value,long published) {}
    private static final class Bucket<V> {
        final int capacity;
        // A published array is never changed. The volatile reference publishes
        // every complete entry together, so readers need no map/entry monitor.
        volatile Entry<V>[] entries;
        @SuppressWarnings("unchecked") Bucket(int capacity) {
            this.capacity=capacity;entries=(Entry<V>[])new Entry<?>[0];
        }
    }
    private static final class Local<V> {
        final long[] keys;
        final Object[] values;
        long lastKey;
        V lastValue;
        Local(int size) {keys=new long[size];values=new Object[size];}
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
    private final Bucket<V>[] buckets;
    private final int bucketShift,capacity;
    private final LongFunction<V> factory;
    private final ThreadLocal<Local<V>> locals;
    private final LongAdder sharedHits=new LongAdder(),recentHits=new LongAdder(),
        localHits=new LongAdder(),misses=new LongAdder();

    @SuppressWarnings("unchecked")
    public BoundedReadCache(int capacity,int localCapacity,LongFunction<V> factory) {
        if(capacity<=0)throw new IllegalArgumentException("Cache capacity must be positive");
        if(localCapacity<0 || localCapacity>32 || localCapacity!=0 && Integer.bitCount(localCapacity)!=1)
            throw new IllegalArgumentException("Worker front capacity must be zero or a power of two up to 32");
        this.factory=Objects.requireNonNull(factory,"Cache factory must be present");
        this.capacity=capacity;
        int requested=(capacity-1)/MAX_BUCKET_CAPACITY+1,count=1;
        while(count<requested)count<<=1;
        buckets=(Bucket<V>[])new Bucket<?>[count];
        bucketShift=32-Integer.numberOfTrailingZeros(count);
        for(int i=0;i<count;i++)buckets[i]=new Bucket<>(capacity/count+(i<capacity%count?1:0));
        // The initializer captures only the fixed limit, not the cache/factory/world.
        locals=localCapacity==0?null:ThreadLocal.withInitial(()->new Local<>(localCapacity));
    }
    private static int hash(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        value^=value>>>31;
        return (int)(value^(value>>>32));
    }
    public V get(long key) {
        Local<V> local=locals==null?null:locals.get();
        if(local!=null && local.lastValue!=null && local.lastKey==key) {
            localHits.increment();return local.lastValue;
        }
        int hash=hash(key);
        if(local!=null) {
            V value=local.find(key,hash);
            if(value!=null) {localHits.increment();return value;}
        }
        int first=buckets.length==1?0:hash>>>bucketShift;
        int second=buckets.length==1?0:Integer.rotateLeft(hash,16)>>>bucketShift;
        V value=read(buckets[first],key,hash,local);
        if(value!=null)return value;
        if(first!=second) {
            value=read(buckets[second],key,hash,local);
            if(value!=null)return value;
        }
        misses.increment();
        V created=Objects.requireNonNull(factory.apply(key),"Cache factories must return a non-null plan");
        Entry<V> published;
        // All publishers use the same lock order, even when their preferred
        // placements are opposite. Nested planning has already finished here.
        Bucket<V> lower=buckets[Math.min(first,second)],upper=buckets[Math.max(first,second)];
        synchronized(lower) {
            if(lower==upper)published=publish(key,created,lower,upper);
            else synchronized(upper) {published=publish(key,created,buckets[first],buckets[second]);}
        }
        if(local!=null)local.remember(key,hash,published.value());
        return published.value();
    }
    private V read(Bucket<V> bucket,long key,int hash,Local<V> local) {
        Entry<V>[] snapshot=bucket.entries;
        for(int i=0;i<snapshot.length;i++) {
            Entry<V> entry=snapshot[i];
            if(entry.key()==key) {
                if(i==0)recentHits.increment();else sharedHits.increment();
                if(local!=null)local.remember(key,hash,entry.value());
                return entry.value();
            }
        }
        return null;
    }
    /** Called only while both candidate bucket monitors are held. */
    private Entry<V> publish(long key,V value,Bucket<V> first,Bucket<V> second) {
        // Recheck both placements after another worker may have published this key.
        for(Entry<V> entry:first.entries)if(entry.key()==key)return entry;
        if(first!=second)for(Entry<V> entry:second.entries)if(entry.key()==key)return entry;
        Bucket<V> target;
        if(first==second)target=first;
        else if(first.entries.length<first.capacity || second.entries.length<second.capacity) {
            // Use spare slots before evicting any plan. Balance equally sized buckets.
            target=first.entries.length*second.capacity<=second.entries.length*first.capacity?first:second;
        } else {
            // Prefer the older publication across the two full candidates. Hits
            // never write recency state or take either publication monitor.
            target=first.entries[first.entries.length-1].published()-second.entries[second.entries.length-1].published()<=0?first:second;
        }
        Entry<V> published=new Entry<>(key,value,System.nanoTime());
        Entry<V>[] snapshot=target.entries;
        int size=Math.min(target.capacity,snapshot.length+1);
        Entry<V>[] next=java.util.Arrays.copyOf(snapshot,size);
        System.arraycopy(snapshot,0,next,1,size-1);next[0]=published;target.entries=next;
        return published;
    }
    public long hits() {return sharedHits.sum()+recentHits.sum()+localHits.sum();}
    public long misses() {return misses.sum();}
    /** Hits in the most recently published slot; reads do not reorder buckets. */
    public long recentHits() {return recentHits.sum();}
    public long localHits() {return localHits.sum();}
    public long sharedReadHits() {return sharedHits.sum()+recentHits.sum();}
    public int capacity() {return capacity;}
    public int bucketCount() {return buckets.length;}
    /** Current shared entries only; worker fronts have their separate fixed bounds. */
    public int size() {
        int size=0;
        for(Bucket<V> bucket:buckets)size+=bucket.entries.length;
        return size;
    }
}
