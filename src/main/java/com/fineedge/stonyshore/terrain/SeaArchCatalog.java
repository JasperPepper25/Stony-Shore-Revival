package com.fineedge.stonyshore.terrain;

import java.util.List;

/** Stateless, equally weighted arch families; all volumes use absolute coordinates. */
public final class SeaArchCatalog {
    private SeaArchCatalog() {}
    public enum Style {
        RISING_FIN("Rising sea fin"), SWEPT_FIN("Swept sea fin"), LOW_WINDOW_FIN("Low-window sea fin"),
        ROUNDED_BRIDGE("Rounded bridge"), BROAD_SPAN("Broad-span bridge"), TALL_SPIRE("Tall spire arch"),
        POINTED_CROWN("Pointed crown"), LOW_SLOT("Low horizontal keyhole"), UPRIGHT_KEYHOLE("Upright keyhole"),
        STACK_PIER("Stack-ended arch"), SENTINEL("Arch with sentinel"), STACK_GROUP("Arch with stack group");
        private final String label;
        Style(String label) { this.label=label; }
        public String label() { return label; }
        public double weight() { return 1; }
        public boolean rising() { return this==RISING_FIN || this==SWEPT_FIN || this==LOW_WINDOW_FIN; }
    }
    private static final Style[] STYLES=Style.values();

    /** A shuffled 4x3 region deck contains every style exactly once. Never reroll on a failed fit. */
    static Style style(CoastalTerrainPlanner noise,int cx,int cz) {
        int tx=Math.floorDiv(cx,4),tz=Math.floorDiv(cz,3);
        int selected=Math.floorMod(cx,4)+4*Math.floorMod(cz,3);
        int[] deck=new int[STYLES.length];for(int i=0;i<deck.length;i++)deck[i]=i;
        for(int i=deck.length-1;i>0;i--) {
            int j=(int)(noise.value(tx,tz,19000+i)*(i+1));
            int swap=deck[i];deck[i]=deck[j];deck[j]=swap;
        }
        return STYLES[deck[selected]];
    }

    static CoastalLandforms.Arch create(Style style,CoastalTerrainPlanner noise,int cx,int cz,int salt,
            int x,int z,double direction,int sea,CoastalShape.Ground root,CoastalShape.GroundSampler ground) {
        double r=noise.value(cx,cz,19301+salt),q=noise.value(cx,cz,19302+salt);
        double reach,thickness,height,width;
        switch(style) {
            case RISING_FIN -> {reach=52+14*r;thickness=6+2*q;height=16+5*q;width=12+3*r;}
            case SWEPT_FIN -> {reach=50+14*r;thickness=5.5+2*q;height=18+7*q;width=11+3*r;}
            case LOW_WINDOW_FIN -> {reach=52+14*r;thickness=6+2*q;height=8+3*q;width=13+3*r;}
            case ROUNDED_BRIDGE -> {reach=38+16*r;thickness=7+3*q;height=17+8*q;width=10+3*r;}
            case BROAD_SPAN -> {reach=48+14*r;thickness=6+3*q;height=15+6*q;width=14+3*r;}
            case TALL_SPIRE -> {reach=34+8*r;thickness=5+2*q;height=34+9*q;width=4.5+1.3*r;}
            case POINTED_CROWN -> {reach=36+10*r;thickness=5.5+2*q;height=29+9*q;width=6+2*r;}
            case LOW_SLOT -> {reach=46+12*r;thickness=8+2*q;height=6+3*q;width=13+3*r;}
            case UPRIGHT_KEYHOLE -> {reach=38+12*r;thickness=7+2*q;height=29+8*q;width=4.2+1.3*r;}
            case STACK_PIER -> {reach=40+14*r;thickness=6+2*q;height=21+8*q;width=8+2*r;}
            case SENTINEL -> {reach=38+14*r;thickness=6+2*q;height=21+9*q;width=8+2*r;}
            default -> {reach=38+12*r;thickness=6+2*q;height=18+8*q;width=8+2*r;}
        }
        double phase=noise.value(cx,cz,19303+salt)*Math.PI*2;
        double bend=(noise.value(cx,cz,19304+salt)-.5)*(style==Style.SWEPT_FIN?8:4);
        double rootTop=Math.max(sea+25,Math.min(root.height()+1,sea+75));
        double rise=style.rising()?12+10*q:style==Style.TALL_SPIRE?34+12*q:
            style==Style.POINTED_CROWN?28+12*q:style==Style.UPRIGHT_KEYHOLE?10:0;
        double position=reach*(style.rising()?.43:.48)+(r-.5)*3;
        // Keep both piers substantial, including the broadest low portals.
        width=Math.min(width,Math.min(position-7,reach-position-10));
        double cs=Math.cos(direction),sn=Math.sin(direction);
        CoastalShape.Ground toe=ground.sample((int)Math.round(x+cs*(reach-7)),(int)Math.round(z+sn*(reach-7)));
        double floor=Math.min(sea-10,toe.height()-4);
        List<Stack> stacks=List.of();
        if(style==Style.STACK_PIER)stacks=List.of(stack(x,z,direction,reach-7,bend*.4,6+q,5+q,
            Math.max(rootTop+12,sea+height+24),sea,phase,false,ground));
        else if(style==Style.SENTINEL)stacks=List.of(stack(x,z,direction,reach+11+2*q,2+3*r,4+q,3.5+q,
            sea+25+19*q,sea,phase+.7,true,ground));
        else if(style==Style.STACK_GROUP)stacks=List.of(
            stack(x,z,direction,reach+9,-9-2*q,3.5+r,3+q,sea+17+13*r,sea,phase+1,false,ground),
            stack(x,z,direction,reach+6,10+2*r,2.8+q,2.8+r,sea+11+12*q,sea,phase+3,true,ground));
        var body=new Body(style,rise,phase,stacks);
        var fin=new CoastalProjection.Fin(x,z,direction,reach,thickness,rootTop,floor,
            style.rising()?.75:.18,bend,1.1+.5*r,phase,body);
        // The portal follows the bent spine instead of testing a roof beside the actual fin.
        double spine=body.center(fin,position);
        int ax=(int)Math.round(x+cs*position-sn*spine),az=(int)Math.round(z+sn*position+cs*spine);
        // Fit the complete opening under its roof, rather than clipping the chosen style to another family.
        double roof=Double.POSITIVE_INFINITY;
        for(double u:new double[]{-.85,0,.85})for(double v:new double[]{-.45,0,.45}) {
            int px=(int)Math.round(ax+cs*u*width-sn*v*thickness);
            int pz=(int)Math.round(az+sn*u*width+cs*v*thickness);
            roof=Math.min(roof,fin.top(px,pz));
        }
        height=Math.min(height,(roof-sea-7)/1.12);
        if(height<5)return null;
        double tilt=(noise.value(cx,cz,19305+salt)-.5)*(style==Style.SWEPT_FIN?.20:.10);
        double exponent=style==Style.POINTED_CROWN?1.65:style==Style.LOW_SLOT?2.5:2;
        var opening=new Opening(tilt,exponent,.06+.035*q,phase);
        return new CoastalLandforms.Arch(ax,az,direction,width,thickness*1.6+Math.abs(bend)+5,
            height,(noise.value(cx,cz,19306+salt)-.5)*2,sea,fin,opening);
    }

    private static Stack stack(int x,int z,double direction,double along,double across,double rx,double rz,
            double top,int sea,double phase,boolean pointed,CoastalShape.GroundSampler ground) {
        double cs=Math.cos(direction),sn=Math.sin(direction);
        int px=(int)Math.round(x+cs*along-sn*across),pz=(int)Math.round(z+sn*along+cs*across);
        double floor=Math.min(sea-10,ground.sample(px,pz).height()-4);
        for(int side:new int[]{-1,1}) {
            floor=Math.min(floor,ground.sample((int)Math.round(px+cs*rx*.7*side),(int)Math.round(pz+sn*rx*.7*side)).height()-4);
            floor=Math.min(floor,ground.sample((int)Math.round(px-sn*rz*.7*side),(int)Math.round(pz+cs*rz*.7*side)).height()-4);
        }
        return new Stack(px,pz,direction,rx,rz,floor,top,1.3*Math.sin(phase),phase,pointed);
    }

    /** Opening geometry is independent of the exterior silhouette. */
    public record Opening(double tilt,double exponent,double irregularity,double phase) {
        double distance(CoastalLandforms.Arch a,double u,double v,int y) {
            return distance(a,u,v,y,Math.cos(tilt),Math.sin(tilt));
        }
        double distance(CoastalLandforms.Arch a,double u,double v,int y,double c,double s) {
            double yy=y-a.openingCenterY();
            double uu=(u*c+yy*s-a.lean()*(yy/Math.max(1,a.height())+.4))/a.width();
            double vv=(-u*s+yy*c)/(a.height()*.57);
            double through=v/a.length();
            uu-=.035*Math.sin(vv*2.1+phase)+.018*Math.sin(through*2-phase);
            double side=uu<0?1+.055*Math.sin(phase+vv):1-.05*Math.cos(phase*.7-vv);
            double edge=1+irregularity*Math.sin(vv*3.1+through*.7+phase)
                +.018*Math.sin(vv*5-through-phase*.4);
            double norm=exponent==2?Math.hypot(uu/side,vv):
                Math.pow(Math.pow(Math.abs(uu/side),exponent)+Math.pow(Math.abs(vv),exponent),1/exponent);
            return Math.max(norm-edge,Math.abs(through)-1);
        }
    }

    /** Immutable broad shape and, for three families, at most two finite sea-stack volumes. */
    public record Body(Style style,double rise,double phase,List<Stack> stacks) {
        public Body { stacks=List.copyOf(stacks); }
        private double center(CoastalProjection.Fin f,double s) {
            double n=clamp(s/f.reach());
            return f.bend()*(style==Style.SWEPT_FIN?Math.sin(n*Math.PI*.7):Math.sin(n*Math.PI))
                +.35*Math.sin(s*.105+phase)*CoastalShape.smooth((s+7)/15);
        }
        private double width(CoastalProjection.Fin f,double s,double t) {
            double n=clamp(s/f.reach());
            return f.thickness()*(1+.38*(1-CoastalShape.smooth((s+5)/17)))
                *(1-(style.rising()?.25:.08)*CoastalShape.smooth(n))
                *(1+.09*Math.sin(s*.095+phase)+.045*Math.sin(s*.21+t*.07-phase));
        }
        private double crown(CoastalProjection.Fin f,double s,double t) {
            double n=clamp(s/f.reach());
            double profile=style.rising()?rise*CoastalShape.smooth(n/.87):
                style==Style.TALL_SPIRE?rise*Math.pow(Math.max(0,Math.sin(n*Math.PI)),.65):
                style==Style.POINTED_CROWN?rise*Math.pow(Math.max(0,1-Math.abs(n-.51)/.51),1.35):
                rise*Math.sin(n*Math.PI)-5*CoastalShape.smooth(n);
            double ridge=(style==Style.TALL_SPIRE || style==Style.POINTED_CROWN)?Math.abs(t)*.65:.045*t*t;
            // Two coherent scales, independent phase on each face; no voxel-wise random cavities.
            return f.crest()+profile-ridge+2.6*Math.sin(s*.12+t*.045+phase)
                +1.1*Math.sin(s*.255-t*.13-phase*.6);
        }
        public double edge(CoastalProjection.Fin f,int x,int z) {
            double s=f.along(x,z),t=f.across(x,z)-center(f,s);
            double cap=Math.min(11,f.reach()*.20),u=Math.max(0,(s-f.reach()+cap)/cap);
            double w=width(f,s,t)*1.24+f.roughness()+2;
            double edge=Math.min(s+10,(1-Math.hypot(u,t/w))*Math.min(w,cap));
            for(var stack:stacks)edge=Math.max(edge,stack.edge(x,z));
            return edge;
        }
        public double top(CoastalProjection.Fin f,int x,int z) {
            return new Section(f,x,z).top();
        }
        public double bottom(CoastalProjection.Fin f,int x,int z) {
            return new Section(f,x,z).bottom;
        }
        public double solid(CoastalProjection.Fin f,int x,int y,int z) {
            return new Section(f,x,z).solid(y);
        }
    }

    public record Stack(int x,int z,double angle,double rx,double rz,double floor,double top,double lean,double phase,boolean pointed) {
        private double edgeAt(double u,double v) {
            return (1-Math.hypot(u/(rx*1.25+Math.abs(lean)+1),
                v/(rz*1.25+Math.abs(lean)+1)))*Math.min(rx,rz);
        }
        double edge(int px,int pz) {
            double c=Math.cos(angle),s=Math.sin(angle),dx=px-x,dz=pz-z;
            return edgeAt(dx*c+dz*s,-dx*s+dz*c);
        }
        public double solid(int px,int y,int pz) { return new StackColumn(this,px,pz).solid(y); }
    }
    private static final class StackColumn {
        private final Stack stack;
        private final double u,v,wear,roof;
        StackColumn(Stack s,int x,int z) {
            stack=s;double c=Math.cos(s.angle),sn=Math.sin(s.angle);
            u=(x-s.x)*c+(z-s.z)*sn;v=-(x-s.x)*sn+(z-s.z)*c;
            wear=1+.06*Math.sin(u*.4+v*.19+s.phase)+.04*Math.sin(u*.21-v*.5-s.phase);
            roof=s.top-(s.pointed?0:1.3*(1+Math.sin(u*.45+v*.28+s.phase)));
        }
        double solid(int y) {
            double h=clamp((y-stack.floor)/Math.max(1,stack.top-stack.floor));
            double taper=(1.17-(stack.pointed?.91:.52)*Math.pow(h,1.45))*wear
                *(1+.035*Math.sin(h*12+stack.phase));
            double radial=Math.hypot((u-stack.lean*h)/(stack.rx*taper),
                (v-stack.lean*.35*h)/(stack.rz*taper));
            return Math.min((1-radial)*Math.min(stack.rx,stack.rz),
                Math.min(y-stack.floor+.5,roof+.5-y));
        }
    }

    /** Horizontal work is cached with the shore column; density sampling allocates nothing. */
    public static final class Section {
        private final CoastalProjection.Fin fin;
        private final double t,width,capU,cap,roof,bottom,wear,edge;
        private final StackColumn first,second;
        private final CoastalLandforms.Arch arch;
        private final double portalU,portalV,portalCos,portalSin;
        public Section(CoastalProjection.Fin f,int x,int z) {
            this(f,null,x,z);
        }
        public Section(CoastalLandforms.Arch a,int x,int z) {
            this(a.fin(),a,x,z);
        }
        private Section(CoastalProjection.Fin f,CoastalLandforms.Arch a,int x,int z) {
            arch=a;
            double c=Math.cos(f.direction()),sn=Math.sin(f.direction());
            portalU=a==null?0:(x-a.x())*c+(z-a.z())*sn;
            portalV=a==null?0:-(x-a.x())*sn+(z-a.z())*c;
            portalCos=a==null?1:Math.cos(a.portal().tilt());portalSin=a==null?0:Math.sin(a.portal().tilt());
            fin=f;Body b=f.catalog();double s=f.along(x,z);t=f.across(x,z)-b.center(f,s);
            width=b.width(f,s,t);cap=Math.min(11,f.reach()*.20);capU=Math.max(0,(s-f.reach()+cap)/cap);
            double w=width*1.24+f.roughness()+2;
            edge=Math.min(s+10,(1-Math.hypot(capU,t/w))*Math.min(w,cap));
            double crown=b.crown(f,s,t),closure=1+CoastalShape.smooth(capU*2)*
                (Math.sqrt(Math.max(0,1-capU*capU-t*t/(w*w)))-1);
            roof=capU==0?crown:(crown+f.floor())*.5+(crown-f.floor())*.5*closure;
            bottom=capU==0?f.floor():(crown+f.floor())*.5-(crown-f.floor())*.5*closure;
            wear=f.roughness()*(.58*Math.sin(s*.17+t*.24+b.phase)+.32*Math.sin(s*.33-t*.19-b.phase));
            first=b.stacks.isEmpty()?null:new StackColumn(b.stacks.get(0),x,z);
            second=b.stacks.size()<2?null:new StackColumn(b.stacks.get(1),x,z);
        }
        public double top() {
            double value=edge>0?roof:Double.NEGATIVE_INFINITY;
            if(first!=null && first.stack.edgeAt(first.u,first.v)>0)value=Math.max(value,first.stack.top);
            if(second!=null && second.stack.edgeAt(second.u,second.v)>0)value=Math.max(value,second.stack.top);
            return Double.isFinite(value)?value:roof;
        }
        public double solid(int y) {
            double fraction=clamp((y-fin.floor())/Math.max(1,roof-fin.floor()));
            double w=Math.max(1,width*(1.18-.30*CoastalShape.smooth(fraction))+wear);
            double side=(1-Math.hypot(capU,t/w))*Math.min(w,cap);
            double result=Math.min(Math.min(edge,side),Math.min(roof+.5-y,y-bottom+.5));
            if(first!=null)result=Math.max(result,first.solid(y));
            if(second!=null)result=Math.max(result,second.solid(y));
            return result;
        }
        public double opening(int y) { return arch.portal().distance(arch,portalU,portalV,y,portalCos,portalSin); }
    }
    private static double clamp(double x) { return Math.max(0,Math.min(1,x)); }
}
