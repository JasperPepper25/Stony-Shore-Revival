package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedLongCacheTest {
    @Test void keepsOriginalCoordinatesDistinctEvenWhenTheirLongHashesCollide() {
        var cache=new BoundedLongCache<Long>(4096);
        for(int i=-512;i<512;i++) {
            long key=CoastalShape.key(i*16,i*16);
            assertEquals(0,Long.hashCode(key));
            assertEquals(key,cache.get(key,k->k).longValue());
        }
        long misses=cache.misses();
        for(int i=511;i>=-512;i--) {
            long key=CoastalShape.key(i*16,i*16);
            assertEquals(key,cache.get(key,k->{fail("Coordinate was lost or altered");return 0L;}).longValue());
        }
        assertEquals(misses,cache.misses());
    }
    @Test void recentEntryShortcutKeepsSingleStripeLruOrderAndCounters() {
        var cache=new BoundedLongCache<Integer>(2);
        cache.get(1,k->1);cache.get(2,k->2);
        cache.get(1,k->1);cache.get(1,k->1);
        assertEquals(2,cache.hits());assertEquals(1,cache.recentHits());
        cache.get(3,k->3);
        var rebuilt=new AtomicInteger();
        assertEquals(2,cache.get(2,k->{rebuilt.incrementAndGet();return 2;}));
        assertEquals(1,rebuilt.get());assertEquals(2,cache.size());
    }
    @Test void handlesExtremeCoordinatesAndRetriesFailedFactories() {
        var cache=new BoundedLongCache<Long>(64);
        for(long key:new long[]{Long.MIN_VALUE,Long.MAX_VALUE,0,-1,CoastalShape.key(Integer.MIN_VALUE,Integer.MAX_VALUE)})
            assertEquals(key,cache.get(key,k->k).longValue());
        assertThrows(IllegalStateException.class,()->cache.get(123L,k->{throw new IllegalStateException("retry");}));
        assertEquals(123,cache.get(123L,k->k).longValue());
        assertThrows(NullPointerException.class,()->cache.get(456L,k->null));
        assertEquals(456,cache.get(456L,k->k).longValue());
        assertThrows(IllegalArgumentException.class,()->new BoundedLongCache<>(0));
    }
    @Test @Timeout(10) void oppositeNestedMissesDoNotWaitForOneAnothersFactories() throws Exception {
        var cache=new BoundedLongCache<Integer>(2);
        var both=new CountDownLatch(2);var workers=Executors.newFixedThreadPool(2);
        try {
            var a=workers.submit(()->cache.get(1,k->{
                both.countDown();await(both);assertEquals(2,cache.get(2,n->2));return 1;
            }));
            var b=workers.submit(()->cache.get(2,k->{
                both.countDown();await(both);assertEquals(1,cache.get(1,n->1));return 2;
            }));
            assertEquals(1,a.get(5,TimeUnit.SECONDS));assertEquals(2,b.get(5,TimeUnit.SECONDS));
        } finally { workers.shutdownNow(); }
    }
    @Test @Timeout(10) void racingMissesReturnTheSamePublishedPlan() throws Exception {
        var cache=new BoundedLongCache<Object>(128);
        var both=new CountDownLatch(2);var workers=Executors.newFixedThreadPool(2);
        try {
            Callable<Object> lookup=()->cache.get(7L,k->{both.countDown();await(both);return new Object();});
            var a=workers.submit(lookup);var b=workers.submit(lookup);
            assertSame(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));
            assertEquals(1,cache.size());assertEquals(2,cache.misses());
        } finally { workers.shutdownNow(); }
    }
    @Test @Timeout(20) void remainsBoundedDuringConcurrentEvictionWithAndWithoutWorkerFronts() throws Exception {
        for(int localCapacity:new int[]{0,8})for(int capacity:new int[]{1,2,127,128,129,1025}) {
            var cache=new BoundedLongCache<Long>(capacity,localCapacity);var workers=Executors.newFixedThreadPool(8);
            try {
                var futures=new java.util.ArrayList<Future<?>>();
                for(int worker=0;worker<8;worker++) {
                    int offset=worker;
                    futures.add(workers.submit(()->{
                        for(int i=0;i<4096;i++) {
                            long key=CoastalShape.key(i*16,(i+offset)*16);
                            assertEquals(key,cache.get(key,k->k).longValue());
                            if((i&127)==0)assertTrue(cache.size()<=capacity);
                        }
                    }));
                }
                for(var future:futures)future.get(10,TimeUnit.SECONDS);
                assertTrue(cache.size()<=capacity);
            } finally { workers.shutdownNow(); }
        }
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5,TimeUnit.SECONDS),"Factory ran while holding a monitor"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new AssertionError(ex); }
    }
    @Test void workerReuseCountsEveryHitOnceAndDoesNotCrossCacheInstances() {
        var first=new BoundedLongCache<Integer>(128,8);
        var second=new BoundedLongCache<Integer>(128,8);
        assertEquals(17,first.get(0,k->17));assertEquals(42,second.get(0,k->42));
        for(int i=0;i<100;i++) {
            assertEquals(17,first.get(0,k->{fail("Worker plan was not reused");return 0;}));
            assertEquals(42,second.get(0,k->{fail("Worker cache crossed world ownership");return 0;}));
        }
        assertEquals(100,first.hits());assertEquals(100,first.localHits());assertEquals(0,first.recentHits());
        assertEquals(1,first.misses());assertEquals(1,first.size());
        assertThrows(IllegalArgumentException.class,()->new BoundedLongCache<>(128,3));
        assertThrows(IllegalArgumentException.class,()->new BoundedLongCache<>(128,64));
    }
    @Test void nestedFactoryPublishesItsOwnCompletePlanAfterTheWorkerFrontChanges() {
        var cache=new BoundedLongCache<Long>(2,8);
        assertEquals(7,cache.get(7,k->{
            for(long inner=100;inner<200;inner++)assertEquals(inner,cache.get(inner,n->n).longValue());
            return k;
        }).longValue());
        assertEquals(7,cache.get(7,k->{fail("Nested miss corrupted the worker front");return 0L;}).longValue());
        assertEquals(1,cache.localHits());assertTrue(cache.size()<=2);
    }
}
