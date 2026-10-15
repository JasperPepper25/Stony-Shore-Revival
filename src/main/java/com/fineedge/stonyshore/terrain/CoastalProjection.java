package com.fineedge.stonyshore.terrain;

/** Attached, additive rock volumes. Their horizontal axes follow the ocean normal. */
public final class CoastalProjection {
    private CoastalProjection() {}
    public record Fin(int rootX,int rootZ,double direction,double reach,double thickness,double crest,double floor,
                      double taper,double bend,double roughness,double phase,SeaArchCatalog.Body catalog) {
        public Fin(int rootX,int rootZ,double direction,double reach,double thickness,double crest,double floor,
                   double taper,double bend,double roughness,double phase) {
            this(rootX,rootZ,direction,reach,thickness,crest,floor,taper,bend,roughness,phase,null);
        }
        public Fin(int rootX,int rootZ,double direction,double reach,double thickness,double crest,double floor) {
            this(rootX,rootZ,direction,reach,thickness,crest,floor,0,0,0,0);
        }
        public Fin(int rootX,int rootZ,double direction,double reach,double thickness,double crest) {
            this(rootX,rootZ,direction,reach,thickness,crest,crest-78);
        }
        public double along(int x,int z) { return (x-rootX)*Math.cos(direction)+(z-rootZ)*Math.sin(direction); }
        public double across(int x,int z) { return -(x-rootX)*Math.sin(direction)+(z-rootZ)*Math.cos(direction); }
        private double halfWidth(double s) {
            double root=1+0.5*(1-CoastalShape.smooth((s+5)/17));
            if(natural())return thickness*root*(1-.18*taper*CoastalShape.smooth(s/reach))
                *(1+.08*Math.sin(s*.083+phase)+.035*Math.sin(s*.177-phase*.73));
            return thickness*root*(.97+.03*Math.sin(s*.13));
        }
        private boolean natural() { return taper!=0 || bend!=0 || roughness!=0; }
        private double center(double s) {
            return natural()?bend*Math.sin(Math.PI*CoastalShape.smooth(s/reach))
                *(1+.15*Math.sin(s*.087+phase)):0;
        }
        private double wave(double s,double t,double y) {
            return .55*Math.sin(s*.139+t*.173+y*.071+phase)
                +.3*Math.sin(s*.071-t*.219+y*.137-phase*.63)
                +.15*Math.sin(s*.311+t*.097-y*.193+phase*.37);
        }
        private double capLength() { return Math.min(12,reach*.26); }
        private double crown(double s,double t) {
            if(natural())return crest-(5+19*taper)*CoastalShape.smooth(s/reach)
                +(2.2+2.5*taper)*Math.sin(s*.073+phase)*CoastalShape.smooth((s+8)/14)
                +roughness*.45*wave(s,t,crest)-.04*t*t;
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
            if(catalog!=null)return catalog.top(this,x,z);
            double s=along(x,z),t=across(x,z)-center(s);
            double crown=crown(s,t);
            if(s<=reach-capLength())return crown;
            // The end closes in height and width together: no high vertical blade at the tip.
            return (crown+floor)/2+(crown-floor)*.5*capSection(s,t);
        }
        public double bottom(int x,int z) {
            if(catalog!=null)return catalog.bottom(this,x,z);
            double s=along(x,z),t=across(x,z)-center(s);
            if(s<=reach-capLength())return floor;
            double crown=crown(s,t);
            return (crown+floor)/2-(crown-floor)*.5*capSection(s,t);
        }
        public double edge(int x,int z) {
            if(catalog!=null)return catalog.edge(this,x,z);
            double s=along(x,z),t=across(x,z)-center(s),width=halfWidth(s);
            if(natural())width=width*1.2+roughness;
            double u=Math.max(0,(s-(reach-capLength()))/capLength());
            double cap=(1-Math.hypot(u,t/width))*Math.min(width,capLength());
            return Math.min(s+10,cap);
        }
        public double reserve(int x,int z) { return CoastalShape.smooth((edge(x,z)+2)/4); }
        public double solid(int x,int y,int z) {
            if(catalog!=null)return catalog.solid(this,x,y,z);
            if(natural()) {
                double s=along(x,z),t=across(x,z)-center(s);
                double crown=crown(s,t),fraction=Math.max(0,Math.min(1,(y-floor)/Math.max(1,crown-floor)));
                // A broad basal buttress and tapered upper spine share a solid core.
                // Weathering moves only the outer face; it never subtracts a hidden cavity.
                double width=halfWidth(s)*(1.18-.30*CoastalShape.smooth(fraction))
                    +roughness*wave(s,t,y);
                double u=Math.max(0,(s-(reach-capLength()))/capLength());
                double side=(1-Math.hypot(u,t/Math.max(1,width)))*Math.min(width,capLength());
                double roof=crown,foot=floor;
                if(s>reach-capLength()) {
                    double section=capSection(s,t),middle=(crown+floor)/2,half=(crown-floor)*.5;
                    roof=middle+half*section;foot=middle-half*section;
                }
                return Math.min(Math.min(s+10,side),Math.min(roof+.5-y,y-foot+.5));
            }
            return Math.min(edge(x,z),Math.min(top(x,z)+.5-y,y-bottom(x,z)+.5));
        }
        public double centerX() { return rootX+Math.cos(direction)*(catalog==null?(reach-10)/2:30); }
        public double centerZ() { return rootZ+Math.sin(direction)*(catalog==null?(reach-10)/2:30); }
        // A common envelope prevents long fins and stack styles receiving a larger collision penalty.
        public double radius() { return catalog==null?Math.hypot((reach+14)/2,natural()?thickness*2+Math.abs(bend)*1.2+roughness+3:thickness*1.6+2):58; }
    }
    public record Ledge(int rootX,int rootZ,double direction,double reach,double width,double crest,
                        double rootDepth,double tipThickness,double roughness,double phase) {
        public Ledge(int rootX,int rootZ,double direction,double reach,double width,double crest) {
            this(rootX,rootZ,direction,reach,width,crest,-1,-1,0,0);
        }
        private boolean natural() { return rootDepth>=0 && tipThickness>=0; }
        private double wave(double s,double t) {
            return .65*Math.sin(s*.123+t*.137+phase)+.35*Math.sin(s*.211-t*.071-phase*.53);
        }
        public double along(int x,int z) { return (x-rootX)*Math.cos(direction)+(z-rootZ)*Math.sin(direction); }
        private double across(int x,int z) { return -(x-rootX)*Math.sin(direction)+(z-rootZ)*Math.cos(direction); }
        public double edge(int x,int z) {
            double s=along(x,z),t=across(x,z);
            // Elliptical ends and a varying lip avoid long straight horizontal shelves.
            double tip=reach*(.86+.14*Math.cos(t/width*2));
            double localWidth=width*Math.sqrt(Math.max(0,1-Math.pow((s-reach*.35)/(reach+9),2)));
            if(natural()) {tip+=roughness*.4*wave(s,t);localWidth+=roughness*.5*wave(s+13,t-7);}
            return Math.min(Math.min(s+8,tip-s),localWidth-Math.abs(t));
        }
        public double top(int x,int z) {
            if(natural())return crest-1.2*CoastalShape.smooth(Math.max(0,along(x,z))/reach)
                +roughness*.5*wave(along(x,z),across(x,z));
            return crest-1.6*CoastalShape.smooth(Math.max(0,along(x,z))/reach)+.6*Math.sin(across(x,z)*.2);
        }
        public double underside(int x,int z) {
            double lip=CoastalShape.smooth(Math.max(0,along(x,z))/reach);
            if(natural()) {
                double shoulders=1-.42*CoastalShape.smooth(Math.abs(across(x,z))/width);
                // Erosion rises gently toward the thin lip and sinks into the cliff below the root.
                double depth=tipThickness+rootDepth*Math.pow(1-lip,1.35)*shoulders;
                return top(x,z)-depth+roughness*.3*wave(along(x,z)+9,across(x,z)-3);
            }
            return top(x,z)-6.5-3.5*(1-lip)+.4*Math.sin(across(x,z)*.19);
        }
        public double reserve(int x,int z) { return CoastalShape.smooth((edge(x,z)+2)/4); }
        public double solid(int x,int y,int z) { return Math.min(edge(x,z),Math.min(top(x,z)+.5-y,y-underside(x,z))); }
        public double centerX() { return rootX+Math.cos(direction)*(reach-8)/2; }
        public double centerZ() { return rootZ+Math.sin(direction)*(reach-8)/2; }
        public double radius() { return Math.hypot((reach+12)/2,width+roughness+2); }
    }
}
