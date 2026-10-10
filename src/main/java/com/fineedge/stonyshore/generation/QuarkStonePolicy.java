package com.fineedge.stonyshore.generation;

/** Only natural cluster blocks are excluded; crafted variants and other Quark features remain. */
public final class QuarkStonePolicy {
    private static volatile boolean observed;
    private static final java.util.concurrent.atomic.LongAdder generators=new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder rejected=new java.util.concurrent.atomic.LongAdder();
    private QuarkStonePolicy() {}
    public static void generatorConstructed() { generators.increment(); }
    public static long generatorsObserved() { return generators.sum(); }
    public static void observedHook() { if(!observed)observed=true; }
    public static boolean hookObserved() { return observed; }
    public static void rejectedPlacement() { rejected.increment(); }
    public static long rejectedPlacements() { return rejected.sum(); }
    public static boolean excluded(String id,boolean shoreAtBlock,boolean shoreAtSea) {
        return (shoreAtBlock || shoreAtSea) && (id.equals("quark:jasper")
            || id.equals("quark:shale") || id.equals("quark:limestone"));
    }
}
