package com.fineedge.stonyshore.terrain;

import java.util.Arrays;

/** Neighborhood statistics from the unmodified terrain, smoothly shared across planning cells. */
public final class CoastalProfile {
    @FunctionalInterface public interface Heights { double sample(int x,int z); }
    public record Sample(double median,double upper,double relief,double slope) {
        public double cliffWeight(int sea) {
            return CoastalShape.smooth((upper-sea-14)/24.0);
        }
        public double lowWeight(int sea) { return 1-CoastalShape.smooth((upper-sea-7)/18.0); }
        public String type(int sea) { return cliffWeight(sea)>.65?"cliff":lowWeight(sea)>.65?"low":"mixed"; }
    }
    private final Heights heights;
    private final BoundedCache<Long,Sample> nodes=new BoundedCache<>(4096);
    public CoastalProfile(Heights heights) { this.heights=heights; }
    private Sample node(int x,int z) {
        return nodes.get(CoastalShape.key(x,z),k->{
            double[] h=new double[9];int n=0;
            for(int dx=-16;dx<=16;dx+=16)for(int dz=-16;dz<=16;dz+=16)h[n++]=heights.sample(x+dx,z+dz);
            double slope=Math.hypot(h[7]-h[1],h[5]-h[3])/32;
            Arrays.sort(h);
            return new Sample(h[4],h[6],h[7]-h[1],slope);
        });
    }
    public Sample sample(int x,int z) {
        int gx=Math.floorDiv(x,32)*32,gz=Math.floorDiv(z,32)*32;
        double fx=CoastalShape.smooth(Math.floorMod(x,32)/32.0),fz=CoastalShape.smooth(Math.floorMod(z,32)/32.0);
        Sample a=node(gx,gz),b=node(gx+32,gz),c=node(gx,gz+32),d=node(gx+32,gz+32);
        return new Sample(blend(a.median,b.median,c.median,d.median,fx,fz),
            blend(a.upper,b.upper,c.upper,d.upper,fx,fz),blend(a.relief,b.relief,c.relief,d.relief,fx,fz),
            blend(a.slope,b.slope,c.slope,d.slope,fx,fz));
    }
    private static double blend(double a,double b,double c,double d,double x,double z) {
        return (a+(b-a)*x)*(1-z)+(c+(d-c)*x)*z;
    }
}
