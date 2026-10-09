package com.fineedge.stonyshore.terrain;

/** Attached, additive rock volumes. Their horizontal axes follow the ocean normal. */
public final class CoastalProjection {
    private CoastalProjection() {}
    public record Fin(int rootX,int rootZ,double direction,double reach,double thickness,double crest) {
        public double along(int x,int z) { return (x-rootX)*Math.cos(direction)+(z-rootZ)*Math.sin(direction); }
        public double across(int x,int z) { return -(x-rootX)*Math.sin(direction)+(z-rootZ)*Math.cos(direction); }
        private double halfWidth(double s) {
            double end=1-CoastalShape.smooth((s-(reach-10))/12);
            double root=1+0.5*(1-CoastalShape.smooth((s+5)/17));
            return thickness*end*root*(.93+.07*Math.sin(s*.19));
        }
        public double top(int x,int z) {
            double s=along(x,z),t=across(x,z);
            return crest-5*CoastalShape.smooth(s/reach)+1.3*Math.sin(s*.16)-.07*t*t;
        }
        public double edge(int x,int z) {
            double s=along(x,z);
            return Math.min(Math.min(s+10,reach+2-s),halfWidth(s)-Math.abs(across(x,z)));
        }
        public double reserve(int x,int z) { return CoastalShape.smooth((edge(x,z)+2)/4); }
        public double solid(int x,int y,int z) { return Math.min(edge(x,z),top(x,z)+.5-y); }
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
            return top(x,z)-5.5-2.5*(1-CoastalShape.smooth(Math.max(0,along(x,z))/reach))+.65*Math.sin(across(x,z)*.31);
        }
        public double reserve(int x,int z) { return CoastalShape.smooth((edge(x,z)+2)/4); }
        public double solid(int x,int y,int z) { return Math.min(edge(x,z),Math.min(top(x,z)+.5-y,y-underside(x,z))); }
        public double radius() { return Math.hypot(reach+8,width+2); }
    }
}
