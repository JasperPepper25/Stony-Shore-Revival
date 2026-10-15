package com.fineedge.stonyshore.terrain;

/** Coherent benches and pocket coves, measured from the immutable physical waterline. */
public final class CoastalMorphology {
    private final CoastalTerrainPlanner noise;
    private final int sea;
    public CoastalMorphology(CoastalTerrainPlanner noise,int sea) { this.noise=noise;this.sea=sea; }
    public double retreat(int x,int z) {
        double wx=x+24*(noise.noise(x,z,173,2101)-.5);
        double wz=z+24*(noise.noise(x,z,157,2103)-.5);
        // Broad individual scallops have a feathered zero boundary, not repeated region edges.
        double bay=CoastalShape.smooth((noise.noise(wx,wz,113,2107)-.62)/.22);
        return bay*(12+17*noise.noise(wx,wz,67,2111));
    }
    public double surface(int x,int z,CoastalShape.Ground g) {
        if(g.ocean() || g.height()<sea+22 || g.mask()<=0)return g.height();
        double d=g.oceanDistance();
        double zone=CoastalShape.smooth((d-18)/15)
            *(1-CoastalShape.smooth((d-70)/24));
        double wx=x+18*(noise.noise(x,z,139,2121)-.5);
        double wz=z+18*(noise.noise(x,z,151,2123)-.5);
        double reach=48+23*noise.noise(wx,wz,131,2127);
        double t=Math.max(0,Math.min(1,(d-16)/reach));
        double count=2+2*noise.noise(wx,wz,191,2131);
        double bands=t*count,whole=Math.floor(bands),fraction=bands-whole;
        // Most of each tier is a bench; the riser occupies a variable, rounded interval.
        double riser=.22+.28*noise.noise(wx,wz,103,2137);
        double tiers=(whole+CoastalShape.smooth(fraction/riser))/count;
        double height=g.height()-sea;
        double retention=.62+.38*CoastalShape.smooth((noise.noise(wx,wz,227,2141)-.25)/.55);
        double target=sea+Math.max(14,height*retention*(.64+.36*tiers));
        double strength=zone*(.45+.45*noise.noise(wx,wz,89,2147));
        return g.height()+strength*(Math.min(g.height(),target)-g.height());
    }
}
