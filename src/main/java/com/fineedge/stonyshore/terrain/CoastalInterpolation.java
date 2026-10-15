package com.fineedge.stonyshore.terrain;

/** Monotone cubic reconstruction: preserves linear slopes without overshoot or grid-edge plateaus. */
final class CoastalInterpolation {
    private CoastalInterpolation() {}
    private static double tangent(double a,double b) {
        if(a*b<=0)return 0;
        return Math.copySign(Math.min(Math.abs((a+b)*.5),2*Math.min(Math.abs(a),Math.abs(b))),a);
    }
    static double cubic(double a,double b,double c,double d,double t) {
        double m=tangent(b-a,c-b),n=tangent(c-b,d-c),t2=t*t,t3=t2*t;
        return (2*t3-3*t2+1)*b+(t3-2*t2+t)*m+(-2*t3+3*t2)*c+(t3-t2)*n;
    }
}
