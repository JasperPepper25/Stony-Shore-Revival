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
        double fx=Math.floorMod(x,32)/32.0,fz=Math.floorMod(z,32)/32.0;
        Sample[][] grid=new Sample[4][4];
        for(int i=0;i<4;i++)for(int j=0;j<4;j++)grid[i][j]=node(gx+(i-1)*32,gz+(j-1)*32);
        double median=blend(grid,fx,fz,Sample::median);
        return new Sample(median,Math.max(median,blend(grid,fx,fz,Sample::upper)),
            Math.max(0,blend(grid,fx,fz,Sample::relief)),Math.max(0,blend(grid,fx,fz,Sample::slope)));
    }
    private static double blend(Sample[][] grid,double x,double z,java.util.function.ToDoubleFunction<Sample> value) {
        double[] rows=new double[4];
        for(int j=0;j<4;j++)rows[j]=CoastalInterpolation.cubic(value.applyAsDouble(grid[0][j]),
            value.applyAsDouble(grid[1][j]),value.applyAsDouble(grid[2][j]),value.applyAsDouble(grid[3][j]),x);
        return CoastalInterpolation.cubic(rows[0],rows[1],rows[2],rows[3],z);
    }
}
