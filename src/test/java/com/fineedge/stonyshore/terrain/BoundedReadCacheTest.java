package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedReadCacheTest {
    private record Plan(long key,long check) {}
    private static long key(int x,int z) {return ((long)x<<32)^(z&0xffffffffL);}
    @Test void comparesFullKeysIncludingOldLongHashCollisionsAndExtremeCoordinates() {
        var cache=new BoundedReadCache<Plan>(8192,8,k->new Plan(k,~k));
        for(int i=-512;i<512;i++) {
            long k=key(i*16,i*16);assertEquals(0,Long.hashCode(k));
            assertEquals(new Plan(k,~k),cache.get(k));
        }
        for(int i=511;i>=-512;i--) {
            long k=key(i*16,i*16);assertEquals(new Plan(k,~k),cache.get(k));
        }
        for(long k:new long[]{Long.MIN_VALUE,Long.MAX_VALUE,0,-1,key(Integer.MIN_VALUE,Integer.MAX_VALUE),
                             key(Integer.MAX_VALUE,Integer.MIN_VALUE)})
            assertEquals(new Plan(k,~k),cache.get(k));
    }
    @Test void bucketsEvictOldestPublicationWithoutWritingAccessOrderOnHits() {
        var factories=new AtomicInteger();
        var cache=new BoundedReadCache<Long>(2,0,k->{factories.incrementAndGet();return k;});
        cache.get(1);cache.get(2);cache.get(1);cache.get(2);
        assertEquals(2,cache.hits());assertEquals(1,cache.recentHits());
        assertEquals(2,cache.sharedReadHits());assertEquals(0,cache.localHits());
        cache.get(3);assertEquals(3,factories.get());
        cache.get(1);assertEquals(4,factories.get());assertEquals(2,cache.size());
    }
    @Test void retriesFailedAndNullFactoriesWithoutPublishingPartialEntries() {
        var tries=new AtomicInteger();
        var cache=new BoundedReadCache<Long>(8,8,k->{
            if(tries.getAndIncrement()==0)throw new IllegalStateException("retry");
            return k==456?null:k;
        });
        assertThrows(IllegalStateException.class,()->cache.get(123));assertEquals(0,cache.size());
        assertEquals(123,cache.get(123));assertEquals(1,cache.size());
        assertThrows(NullPointerException.class,()->cache.get(456));assertEquals(1,cache.size());
        assertEquals(123,cache.get(123));assertEquals(1,cache.localHits());
    }
    @Test void validatesCapacityWorkerFrontAndFactory() {
        assertThrows(IllegalArgumentException.class,()->new BoundedReadCache<>(0,0,k->k));
        assertThrows(IllegalArgumentException.class,()->new BoundedReadCache<>(8,-1,k->k));
        assertThrows(IllegalArgumentException.class,()->new BoundedReadCache<>(8,3,k->k));
        assertThrows(IllegalArgumentException.class,()->new BoundedReadCache<>(8,64,k->k));
        assertThrows(NullPointerException.class,()->new BoundedReadCache<>(8,0,null));
    }
    @Test void frontsDoNotCrossCacheOwnershipAndCountEachHitOnce() {
        var a=new BoundedReadCache<Long>(1,8,k->17L);
        var b=new BoundedReadCache<Long>(1,8,k->42L);
        for(int i=0;i<100;i++) {assertEquals(17,a.get(0));assertEquals(42,b.get(0));}
        assertEquals(99,a.localHits());assertEquals(99,a.hits());assertEquals(1,a.misses());
    }
    @Test @Timeout(10) void sharedHitDoesNotWaitForThePublicationMonitor() throws Exception {
        var calls=new AtomicInteger();
        var cache=new BoundedReadCache<Plan>(8,0,k->{calls.incrementAndGet();return new Plan(k,~k);});
        Plan expected=cache.get(7);
        var field=BoundedReadCache.class.getDeclaredField("buckets");field.setAccessible(true);
        Object monitor=((Object[])field.get(cache))[0];
        var workers=Executors.newSingleThreadExecutor();
        try {
            synchronized(monitor) {
                assertSame(expected,workers.submit(()->cache.get(7)).get(2,TimeUnit.SECONDS));
            }
            assertEquals(1,calls.get());
        } finally {workers.shutdownNow();}
    }
    @Test @Timeout(10) void sharedReadersCompleteWhileBothPlacementMonitorsAreHeld() throws Exception {
        var calls=new AtomicInteger();
        var cache=new BoundedReadCache<Plan>(16,0,k->{calls.incrementAndGet();return new Plan(k,~k);});
        Plan[] expected=new Plan[8];for(int i=0;i<8;i++)expected[i]=cache.get(i);
        var field=BoundedReadCache.class.getDeclaredField("buckets");field.setAccessible(true);
        Object[] monitors=(Object[])field.get(cache);
        var workers=Executors.newSingleThreadExecutor();
        try {
            synchronized(monitors[0]) {synchronized(monitors[1]) {
                workers.submit(()->{
                    for(int i=0;i<8;i++)assertSame(expected[i],cache.get(i));
                }).get(2,TimeUnit.SECONDS);
            }}
            assertEquals(8,calls.get());assertEquals(8,cache.sharedReadHits());
        } finally {workers.shutdownNow();}
    }
    @Test @Timeout(10) void oppositeNestedMissesDoNotHoldBucketMonitorsDuringFactories() throws Exception {
        @SuppressWarnings("unchecked") BoundedReadCache<Long>[] holder=new BoundedReadCache[1];
        var gate=new CountDownLatch(2);
        // Inner keys use a non-nesting factory, guarded by caller-owned depth.
        var depth=ThreadLocal.withInitial(()->false);
        holder[0]=new BoundedReadCache<>(2,8,k->{
            if(depth.get())return k;
            gate.countDown();await(gate);depth.set(true);
            try {long inner=k+100;assertEquals(inner,holder[0].get(inner));return k;}
            finally {depth.remove();}
        });
        var workers=Executors.newFixedThreadPool(2);
        try {
            var a=workers.submit(()->holder[0].get(1));var b=workers.submit(()->holder[0].get(2));
            assertEquals(1,a.get(5,TimeUnit.SECONDS));assertEquals(2,b.get(5,TimeUnit.SECONDS));
        } finally {workers.shutdownNow();}
    }
    @Test void nestedMissRemembersTheCompleteOuterPlanAfterChangingTheWorkerFront() {
        @SuppressWarnings("unchecked") BoundedReadCache<Long>[] holder=new BoundedReadCache[1];
        holder[0]=new BoundedReadCache<>(2,8,k->{
            if(k==7)for(long inner=100;inner<200;inner++)assertEquals(inner,holder[0].get(inner));
            return k;
        });
        assertEquals(7,holder[0].get(7));long misses=holder[0].misses();
        assertEquals(7,holder[0].get(7));assertEquals(misses,holder[0].misses());
        assertEquals(1,holder[0].localHits());assertTrue(holder[0].size()<=2);
    }
    @Test @Timeout(10) void racingMissesUseOneCompletePublishedPlan() throws Exception {
        var gate=new CountDownLatch(2);
        var cache=new BoundedReadCache<Plan>(8,0,k->{gate.countDown();await(gate);return new Plan(k,~k);});
        var workers=Executors.newFixedThreadPool(2);
        try {
            var a=workers.submit(()->cache.get(7));var b=workers.submit(()->cache.get(7));
            Plan first=a.get(5,TimeUnit.SECONDS),second=b.get(5,TimeUnit.SECONDS);
            assertSame(first,second);assertEquals(new Plan(7,~7L),first);
            assertEquals(1,cache.size());assertEquals(2,cache.misses());
        } finally {workers.shutdownNow();}
    }
    @Test @Timeout(25) void concurrentEvictionNeverReturnsAnotherKeyOrExceedsSharedCapacity() throws Exception {
        for(int local:new int[]{0,8})for(int capacity:new int[]{1,2,7,8,9,127,128,129,1025,8192}) {
            var cache=new BoundedReadCache<Plan>(capacity,local,k->new Plan(k,~k));
            var workers=Executors.newFixedThreadPool(12);
            try {
                var futures=new java.util.ArrayList<Future<?>>();
                for(int worker=0;worker<12;worker++) {
                    int offset=worker;
                    futures.add(workers.submit(()->{
                        for(int i=0;i<4096;i++) {
                            long k=key(i*16,(i+offset)*16);assertEquals(new Plan(k,~k),cache.get(k));
                            if((i&255)==0)assertTrue(cache.size()<=capacity);
                        }
                    }));
                }
                for(var future:futures)future.get(15,TimeUnit.SECONDS);
                assertTrue(cache.size()<=capacity);assertEquals(capacity,cache.capacity());
            } finally {workers.shutdownNow();}
        }
    }
    private static void await(CountDownLatch latch) {
        try {assertTrue(latch.await(5,TimeUnit.SECONDS),"Factory ran while holding a publication monitor");}
        catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new AssertionError(ex);}
    }
}
