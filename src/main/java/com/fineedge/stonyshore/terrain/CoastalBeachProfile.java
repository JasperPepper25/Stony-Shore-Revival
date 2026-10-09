package com.fineedge.stonyshore.terrain;

/** A continuous beach and shallow seabed profile shared by terrain and material decisions. */
public final class CoastalBeachProfile {
    public static final int OCEAN_REACH=56;
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
        double beach,surface;
        if(g.ocean()) {
            double distance=Math.max(0,-signed);
            double outer=1-smooth((distance-(OCEAN_REACH-20))/20);
            // Elevated terrain labelled as ocean is retained rather than flattened into a beach.
            double eligible=1-smooth((height-(sea+4))/8);
            double anchor=smooth((g.shoreHeight()-(sea-10))/12);
            beach=field*outer*eligible*anchor;
            double submerged=Math.max(0,distance-dryWidth);
            double shelf=sea+1.35-.11*submerged-.006*submerged*submerged;
            shelf+=.32*(noise.noise(wx,wz,37,1643)-.5);
            surface=height+beach*(shelf-height);
        } else {
            double distance=Math.max(0,signed);
            beach=field*(1-smooth((distance-landWidth)/7));
            double bench=sea+1.35+Math.min(distance,landWidth)*.045
                +.32*(noise.noise(wx,wz,37,1643)-.5);
            // A narrow irregular toe leaves the main cliff relief intact farther inland.
            double weather=(noise.noise(wx,wz,67,1649)-.5)*1.25;
            double retained=height+weather*smooth((height-sea)/6);
            surface=retained+beach*(bench-retained);
        }
        double heightGate=1-smooth((surface-(sea+3))/6);
        double depthGate=1-smooth((sea-12-surface)/16);
        double sand=beach*heightGate*depthGate;
        return new Sample(surface,Math.max(0,Math.min(1,sand)));
    }
    private static double smooth(double value) {
        double v=Math.max(0,Math.min(1,value));return v*v*v*(v*(v*6-15)+10);
    }
}
