package com.fineedge.stonyshore.terrain;

/** Attached, additive rock volumes. Their horizontal axes follow the ocean normal. */
public final class CoastalProjection {
    private CoastalProjection() {}
    public record Fin(int rootX,int rootZ,double direction,double reach,double thickness,double crest,double floor) {
        public Fin(int rootX,int rootZ,double direction,double reach,double thickness,double crest) {
            this(rootX,rootZ,direction,reach,thickness,crest,crest-78);
        }
        public double along(int x,int z) { return (x-rootX)*Math.cos(direction)+(z-rootZ)*Math.sin(direction); }
        public double across(int x,int z) { return -(x-rootX)*Math.sin(direction)+(z-rootZ)*Math.cos(direction); }
        private double halfWidth(double s) {
            double root=1+0.5*(1-CoastalShape.smooth((s+5)/17));
            return thickness*root*(.97+.03*Math.sin(s*.13));
        }
        private double capLength() { return Math.min(12,reach*.26); }
        private double crown(double s,double t) {
            return crest-3.5*CoastalShape.smooth(s/reach)+.9*Math.sin(s*.12)-.025*t*t;
        }
        private double capSection(double s,double t) {
            double u=Math.max(0,(s-(reach-capLength()))/capLength());
            double v=t/halfWidth(s);
            double rounded=Math.sqrt(Math.max(0,1-u*u-v*v));
            // Meet the full-height pier continuously, including off-center columns.
            // The outer half retains the complete ellipsoidal closure.
            return 1+CoastalShape.smooth(u*2)*(rounded-1);
        }
        public double top(int x,int z) {
            double s=along(x,z),t=across(x,z);
            double crown=crown(s,t);
            if(s<=reach-capLength())return crown;
            // The end closes in height and width together: no high vertical blade at the tip.
            return (crown+floor)/2+(crown-floor)*.5*capSection(s,t);
        }
        public double bottom(int x,int z) {
            double s=along(x,z),t=across(x,z);
            if(s<=reach-capLength())return floor;
            double crown=crown(s,t);
            return (crown+floor)/2-(crown-floor)*.5*capSection(s,t);
        }
        public double edge(int x,int z) {
            double s=along(x,z),t=across(x,z),width=halfWidth(s);
            double u=Math.max(0,(s-(reach-capLength()))/capLength());
            double cap=(1-Math.hypot(u,t/width))*Math.min(width,capLength());
            return Math.min(s+10,cap);
        }
        public double reserve(int x,int z) { return CoastalShape.smooth((edge(x,z)+2)/4); }
        public double solid(int x,int y,int z) {
            return Math.min(edge(x,z),Math.min(top(x,z)+.5-y,y-bottom(x,z)+.5));
        }
        public double centerX() { return rootX+Math.cos(direction)*(reach-10)/2; }
        public double centerZ() { return rootZ+Math.sin(direction)*(reach-10)/2; }
        public double radius() { return Math.hypot((reach+14)/2,thickness*1.6+2); }
    }
    public record Ledge(int rootX,int rootZ,double direction,double reach,double width,double crest) {
        public double along(int x,int z) { return (x-rootX)*Math.cos(direction)+(z-rootZ)*Math.sin(direction); }
        private double across(int x,int z) { return -(x-rootX)*Math.sin(direction)+(z-rootZ)*Math.cos(direction); }
        public double edge(int x,int z) {
            double s=along(x,z),t=across(x,z);
            // Elliptical ends and a varying lip avoid long straight horizontal shelves.
            double tip=reach*(.86+.14*Math.cos(t/width*2));
            return Math.min(Math.min(s+8,tip-s),width*Math.sqrt(Math.max(0,1-Math.pow((s-reach*.35)/(reach+9),2)))-Math.abs(t));
        }
        public double top(int x,int z) {
            return crest-1.6*CoastalShape.smooth(Math.max(0,along(x,z))/reach)+.6*Math.sin(across(x,z)*.2);
        }
        public double underside(int x,int z) {
            double lip=CoastalShape.smooth(Math.max(0,along(x,z))/reach);
            return top(x,z)-6.5-3.5*(1-lip)+.4*Math.sin(across(x,z)*.19);
        }
        public double reserve(int x,int z) { return CoastalShape.smooth((edge(x,z)+2)/4); }
        public double solid(int x,int y,int z) { return Math.min(edge(x,z),Math.min(top(x,z)+.5-y,y-underside(x,z))); }
        public double centerX() { return rootX+Math.cos(direction)*(reach-8)/2; }
        public double centerZ() { return rootZ+Math.sin(direction)*(reach-8)/2; }
        public double radius() { return Math.hypot((reach+12)/2,width+2); }
    }
}
