package com.fineedge.stonyshore.terrain;

/** A continuous beach and shallow seabed profile shared by terrain and material decisions. */
public final class CoastalBeachProfile {
    public static final int OCEAN_REACH=RegionalCoast.OCEAN_REACH;
    public record Sample(double surface,double sand) {}
    private final int sea;
    private final CoastalTerrainPlanner noise;
    private final CoastalShape.Options options;

    public CoastalBeachProfile(int sea,CoastalTerrainPlanner noise,CoastalShape.Options options) {
        this.sea=sea;this.noise=noise;this.options=options;
    }
    private double warpedX(int x,int z) { return x+14*(noise.noise(x,z,96,1621)-.5); }
    private double warpedZ(int x,int z) { return z+14*(noise.noise(x,z,89,1627)-.5); }
    public double field(int x,int z) {
        if(!options.beaches())return 0;
        double wx=warpedX(x,z),wz=warpedZ(x,z);
        double value=.72*noise.noise(wx,wz,176,611)+.28*noise.noise(wx,wz,91,613);
        return smooth((value-(.72-options.beachFrequency()*.48))/.22);
    }
    public Sample sample(int x,int z,CoastalShape.Ground g) {
        if(g.mask()<=0)return new Sample(g.height(),0);
        double wx=warpedX(x,z),wz=warpedZ(x,z);
        double height=g.height(),field=field(x,z);
        double signed=g.oceanDistance()+2.8*(noise.noise(wx,wz,54,1633)-.5);
        double landWidth=8+10*noise.noise(wx,wz,83,1637);
        double dryWidth=4+6*noise.noise(wx,wz,97,1639);
        // One profile spans dry land, the waterline and seabed. A biome label or a
        // nearest-anchor height cannot abruptly enable or disable any part of it.
        double land=1-smooth((signed-landWidth)/14);
        double outer=1-smooth((-signed-(OCEAN_REACH-32))/32);
        double offshore=smooth(-g.oceanDistance()/8);
        double elevated=1-offshore*smooth((height-(sea+4))/8);
        double beach=field*land*outer*elevated;
        double submerged=Math.max(0,-signed-dryWidth);
        double bench=sea+1.35+Math.min(Math.max(0,signed),landWidth)*.045
            -.11*submerged-.004*submerged*submerged
            +.32*(noise.noise(wx,wz,37,1643)-.5);
        // The cliff body remains behind a narrow toe. The offshore apron approaches
        // the original floor over its outer third with zero transition slope.
        double weather=(noise.noise(wx,wz,67,1649)-.5)*1.25*(1-offshore);
        double retained=height+weather*smooth((height-sea)/6);
        double surface=retained+beach*(bench-retained);
        double heightGate=1-smooth((surface-(sea+3))/6);
        double depthGate=1-smooth((sea-12-surface)/16);
        double stonePatch=smooth((noise.noise(wx,wz,43,1651)-.70)/.30);
        double sand=beach*heightGate*depthGate*(1-.65*stonePatch);
        return new Sample(surface,Math.max(0,Math.min(1,sand)));
    }
    private static double smooth(double value) {
        double v=Math.max(0,Math.min(1,value));return v*v*v*(v*(v*6-15)+10);
    }
}
