package com.fineedge.stonyshore.terrain;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded blind coastal caves. Their portals follow the physical ocean normal. */
public final class CoastalCaves {
    public enum Interior { LUSH, DRIPSTONE }
    @FunctionalInterface public interface Allowed { boolean test(int x,int y,int z); }
    public record Cave(int x,int z,double direction,double depth,double width,double height,
                       double floor,double approach,double bend,double phase,boolean flooded,Interior interior) {
        public double along(int px,int pz) { return (px-x)*Math.cos(direction)+(pz-z)*Math.sin(direction); }
        public double across(int px,int pz) { return -(px-x)*Math.sin(direction)+(pz-z)*Math.cos(direction); }
        public double opening(int px,int y,int pz) {
            double s=along(px,pz),q=Math.max(0,Math.min(1,-s/depth));
            double turn=bend*Math.sin(q*Math.PI*.85);
            double w=width*(1+.14*Math.sin(q*5+phase));
            double h=height*(1+.11*Math.sin(q*4+phase+1));
            double rise=flooded?.45*q:1.2*q;
            double center=floor+h*.5+rise;
            double cap=Math.max(0,(q-.72)/.28);
            double r=Math.sqrt(Math.pow((across(px,pz)-turn)/w,2)
                +Math.pow((y-center)/(h*.5),2)+cap*cap);
            return Math.max((r-1)*Math.min(w,h*.5),Math.max(s-approach,-s-depth));
        }
        public boolean containsColumn(int px,int pz) {
            double s=along(px,pz),q=Math.max(0,Math.min(1,-s/depth));
            return s>=-depth && s<=approach
                && Math.abs(across(px,pz)-bend*Math.sin(q*Math.PI*.85))<width*1.15;
        }
        public double centerX() { return x+Math.cos(direction)*(approach-depth)*.5; }
        public double centerZ() { return z+Math.sin(direction)*(approach-depth)*.5; }
        public double radius() { return Math.hypot((depth+approach)*.5,width*1.15+Math.abs(bend)); }
    }
    private record Site(Cave cave) {}
    private final CoastalTerrainPlanner noise;
    private final CoastalShape.GroundSampler ground;
    private final CoastalShape.Solid support;
    private final Allowed allowed;
    private final CoastalPools.Height surface;
    private final int sea;
    private final BoundedLongCache<Site> raw=new BoundedLongCache<>(256),sites=new BoundedLongCache<>(256),columns=new BoundedLongCache<>(8192,8);
    private final ConcurrentHashMap<String,LongAdder> counters=new ConcurrentHashMap<>();
    public CoastalCaves(CoastalTerrainPlanner noise,int sea,CoastalShape.GroundSampler ground,
                        CoastalPools.Height surface,CoastalShape.Solid support) {
        this(noise,sea,ground,surface,support,(x,y,z)->true);
    }
    public CoastalCaves(CoastalTerrainPlanner noise,int sea,CoastalShape.GroundSampler ground,
                        CoastalPools.Height surface,CoastalShape.Solid support,Allowed allowed) {
        this.noise=noise;this.sea=sea;this.ground=ground;this.surface=surface;this.support=support;this.allowed=allowed;
    }
    private void count(String key) { counters.computeIfAbsent(key,k->new LongAdder()).increment(); }
    public Map<String,Long> stats() {
        var result=new TreeMap<String,Long>();counters.forEach((k,v)->result.put(k,v.sum()));
        result.put("caveColumnCacheHits",columns.hits());result.put("caveColumnCacheMisses",columns.misses());
        result.put("caveColumnCacheLocalHits",columns.localHits());return result;
    }
    private Site raw(int cx,int cz) { return raw.get(CoastalShape.key(cx,cz),k->plan(cx,cz)); }
    private Site site(int cx,int cz) { return sites.get(CoastalShape.key(cx,cz),k->{
        Cave cave=raw(cx,cz).cave();if(cave==null)return new Site(null);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            if(dx>0 || dx==0 && dz>=0)continue;
            Cave other=raw(cx+dx,cz+dz).cave();
            if(other!=null && Math.hypot(cave.centerX()-other.centerX(),cave.centerZ()-other.centerZ())
                <cave.radius()+other.radius()+4)return new Site(null);
        }
        count(cave.flooded()?"seaCavesAccepted":"shelfCavesAccepted");return new Site(cave);
    }); }
    public Cave cave(int x,int z) {
        return columns.get(CoastalShape.key(x,z),k->new Site(find(x,z))).cave();
    }
    private Cave find(int x,int z) {
        var here=ground.sample(x,z);
        if(here.mask()<.35 || here.oceanDistance()< -32 || here.oceanDistance()>88)return null;
        int cx=Math.floorDiv(x,48),cz=Math.floorDiv(z,48);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            Cave cave=site(cx+dx,cz+dz).cave();
            if(cave!=null && cave.containsColumn(x,z))return cave;
        }
        return null;
    }
    private int px(int x,double angle,double along,double across) {
        return (int)Math.round(x+Math.cos(angle)*along-Math.sin(angle)*across);
    }
    private int pz(int z,double angle,double along,double across) {
        return (int)Math.round(z+Math.sin(angle)*along+Math.cos(angle)*across);
    }
    private Site plan(int cx,int cz) {
        if(noise.value(cx,cz,2301)>.43)return new Site(null);
        for(int attempt=0;attempt<14;attempt++) {
            int salt=attempt*47;
            int sx=cx*48+4+(int)(noise.value(cx,cz,2303+salt)*40);
            int sz=cz*48+4+(int)(noise.value(cx,cz,2305+salt)*40);
            var start=ground.sample(sx,sz);
            if(start.mask()<.65 || start.ocean() || start.height()<sea+24 || start.oceanDistance()>54)continue;
            double gx=ground.sample(sx+8,sz).oceanDistance()-ground.sample(sx-8,sz).oceanDistance();
            double gz=ground.sample(sx,sz+8).oceanDistance()-ground.sample(sx,sz-8).oceanDistance();
            if(Math.hypot(gx,gz)<3)continue;
            double angle=Math.atan2(-gz,-gx);
            for(int offset=-16;offset<=16;offset+=4) {
                int x=px(sx,angle,offset,0),z=pz(sz,angle,offset,0);
                if(Math.floorDiv(x,48)!=cx || Math.floorDiv(z,48)!=cz)continue;
                var root=ground.sample(x,z);var outer=ground.sample(px(x,angle,10,0),pz(z,angle,10,0));
                if(root.mask()<.7 || root.ocean() || root.height()-outer.height()<16 || root.oceanDistance()<0)continue;
                boolean flooded=outer.height()<sea+7;
                double width=3.5+6.5*noise.value(cx,cz,2311+salt);
                double height=6+10*noise.value(cx,cz,2313+salt);
                double floor=flooded?sea-3.8:surface.sample(px(x,angle,8,0),pz(z,angle,8,0))+.2;
                if(!flooded && (floor<sea+12 || floor>root.height()-height-7))continue;
                if(root.height()<floor+height+6)continue;
                if(!flooded && !shelf(x,z,angle,width,floor))continue;
                var cave=new Cave(x,z,angle,17+23*noise.value(cx,cz,2317+salt),width,height,floor,
                    flooded?12:9,(noise.value(cx,cz,2319+salt)-.5)*width*.65,
                    noise.value(cx,cz,2321+salt)*Math.PI*2,flooded,
                    noise.value(cx,cz,2323+salt)<.55?Interior.LUSH:Interior.DRIPSTONE);
                if((flooded && !openSeaMouth(cave)) || !supported(cave) || !allowedVolume(cave))continue;
                count("coastalCavesSupported");return new Site(cave);
            }
        }
        return new Site(null);
    }
    private boolean shelf(int x,int z,double angle,double width,double floor) {
        for(double a:new double[]{5,8,11})for(double t:new double[]{-width*.6,0,width*.6}) {
            int px=px(x,angle,a,t),pz=pz(z,angle,a,t);
            double h=surface.sample(px,pz);
            if(ground.sample(px,pz).mask()<.55 || h<floor-2 || h>floor+2.5)return false;
        }
        return true;
    }
    private boolean openSeaMouth(Cave cave) {
        // A low dry shelf is not an ocean connection. The complete mouth must meet
        // original open sea-level air, including both shoulders, before claiming water.
        for(double t:new double[]{-cave.width()*.6,0,cave.width()*.6}) {
            int x=px(cave.x(),cave.direction(),cave.approach(),t);
            int z=pz(cave.z(),cave.direction(),cave.approach(),t);
            var g=ground.sample(x,z);
            if(!coastal(g) || !g.ocean() || g.height()>=sea-1
                || support.test(x,sea-1,z))return false;
        }
        return true;
    }
    private boolean coastal(CoastalShape.Ground g) {
        return g.mask()>=.55 && g.oceanDistance()>=-32 && g.oceanDistance()<=88;
    }
    private boolean rock(Cave cave,double s,double t,int y) {
        int x=px(cave.x(),cave.direction(),s,t),z=pz(cave.z(),cave.direction(),s,t);
        return coastal(ground.sample(x,z)) && surface.sample(x,z)>=y+.5 && support.test(x,y,z);
    }
    private boolean supported(Cave cave) {
        // Sample the room perimeter, rather than assuming two supported horizontal
        // planes imply sealed walls. Reject existing side/back hollows; never fill them.
        for(double q:new double[]{Math.min(.18,3/cave.depth()),.25,.5,.75,.88}) {
            double s=-cave.depth()*q,turn=cave.bend()*Math.sin(q*Math.PI*.85);
            double w=cave.width()*(1+.14*Math.sin(q*5+cave.phase()));
            double h=cave.height()*(1+.11*Math.sin(q*4+cave.phase()+1));
            double floor=cave.floor()+(cave.flooded()?.45*q:1.2*q);
            double cap=Math.max(0,(q-.72)/.28);
            for(double t:new double[]{-w*.5,0,w*.5}) {
                if(!rock(cave,s,turn+t,(int)Math.floor(floor-2))
                    || !rock(cave,s,turn+t,(int)Math.ceil(floor+h+2)))return false;
            }
            for(double level:new double[]{.15,.5,.85}) {
                double vertical=2*level-1;
                double side=w*Math.sqrt(Math.max(0,1-vertical*vertical-cap*cap))+1.5;
                int y=(int)Math.round(floor+h*level);
                if(!rock(cave,s,turn-side,y) || !rock(cave,s,turn+side,y))return false;
            }
        }
        double turn=cave.bend()*Math.sin(Math.PI*.85);
        for(double t:new double[]{-cave.width()*.5,0,cave.width()*.5})
            for(double level:new double[]{.15,.5,.85}) {
                int y=(int)Math.round(cave.floor()+cave.height()*level+(cave.flooded()?.45:1.2));
                if(!rock(cave,-cave.depth()-1.5,turn+t,y))return false;
            }
        return true;
    }
    private boolean allowedVolume(Cave cave) {
        // Original biome protection vetoes a complete plan. Sample the world quart
        // lattice only after structural acceptance; never clip individual cave columns.
        double radius=cave.radius();
        int minX=Math.floorDiv((int)Math.floor(cave.centerX()-radius),4);
        int maxX=Math.floorDiv((int)Math.ceil(cave.centerX()+radius),4);
        int minZ=Math.floorDiv((int)Math.floor(cave.centerZ()-radius),4);
        int maxZ=Math.floorDiv((int)Math.ceil(cave.centerZ()+radius),4);
        int minY=Math.floorDiv((int)Math.floor(cave.floor()),4);
        int maxY=Math.floorDiv((int)Math.ceil(cave.floor()+cave.height()*1.11+1.2),4);
        for(int qx=minX;qx<=maxX;qx++)for(int qz=minZ;qz<=maxZ;qz++) {
            int x=qx*4+2,z=qz*4+2;
            if(!cave.containsColumn(x,z))continue;
            for(int qy=minY;qy<=maxY;qy++) {
                int y=qy*4+2;
                if(cave.opening(x,y,z)<0 && !allowed.test(x,y,z))return false;
            }
        }
        // Include the endpoint and shoulders even when a thin mouth falls between
        // quart centers; these are also the columns proving ocean/shelf access.
        for(double t:new double[]{-cave.width()*.6,0,cave.width()*.6})
            for(double level:new double[]{.2,.5,.8}) {
                int x=px(cave.x(),cave.direction(),cave.approach(),t);
                int z=pz(cave.z(),cave.direction(),cave.approach(),t);
                int y=(int)Math.round(cave.floor()+cave.height()*level);
                if(!allowed.test(x,y,z))return false;
            }
        return true;
    }
}
