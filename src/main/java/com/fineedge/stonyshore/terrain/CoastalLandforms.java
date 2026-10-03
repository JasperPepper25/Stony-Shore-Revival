package com.fineedge.stonyshore.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Regional plans in world coordinates. Validation never reads or generates a chunk. */
public final class CoastalLandforms {
    public record Base(double surface, double original, double mask, double sand) {}
    @FunctionalInterface public interface Ground { Base sample(int x, int z); }
    public record Pool(int x, int z, double rx, double rz, double angle, int water, int depth, long salt) {
        double radius(int px, int pz, CoastalTerrainPlanner noise) {
            double dx=px-x, dz=pz-z, c=Math.cos(angle), s=Math.sin(angle);
            double u=(dx*c+dz*s)/rx, v=(-dx*s+dz*c)/rz;
            double edge=0.87+0.23*noise.noise(px,pz,17,salt);
            return Math.hypot(u,v)/edge;
        }
        double floor(int px,int pz, double base, CoastalTerrainPlanner noise) {
            double r=radius(px,pz,noise);
            double bowl=1-smooth((r-0.3)/0.7);
            return base+bowl*(water-depth-0.5-base);
        }
    }
    public record Arch(int x,int z,double angle,double width,double length,double height,double lean,int sea) {
        private double u(int px,int pz) { return (px-x)*Math.cos(angle)+(pz-z)*Math.sin(angle); }
        private double v(int px,int pz) { return -(px-x)*Math.sin(angle)+(pz-z)*Math.cos(angle); }
        public double opening(int px,int y,int pz) {
            double yy=(y-(sea+height*0.43))/(height*0.57);
            double uu=(u(px,pz)-lean*(yy+0.4))/width;
            double irregular=1+0.08*Math.sin(v(px,pz)*0.41+yy*2.7);
            return Math.max(Math.hypot(uu,yy)-irregular,Math.abs(v(px,pz))/length-1);
        }
        double reserve(int px,int pz) {
            double edge=Math.max(Math.abs(u(px,pz))/(width+6),Math.abs(v(px,pz))/(length+3));
            return 1-smooth((edge-0.8)/0.45);
        }
    }
    private record Site(Pool pool, Arch arch) {}
    private static final Site EMPTY=new Site(null,null);
    private final CoastalTerrainPlanner noise;
    private final Ground ground;
    private final CoastalColumnSampler.Terrain terrain;
    private final CoastalColumnSampler.Shore shore;
    private final int sea;
    private final boolean arches;
    private final ThreadLocal<Map<Long,Site>> sites=ThreadLocal.withInitial(()->new LinkedHashMap<>(64,0.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<Long,Site> e) { return size()>64; }
    });
    public CoastalLandforms(long seed,int sea,Ground ground,CoastalColumnSampler.Terrain terrain,
                            CoastalColumnSampler.Shore shore,boolean arches) {
        this.noise=new CoastalTerrainPlanner(seed);this.sea=sea;this.ground=ground;
        this.terrain=terrain;this.shore=shore;this.arches=arches;
    }
    private Site site(int x,int z) {
        int cx=Math.floorDiv(x,96),cz=Math.floorDiv(z,96);long key=((long)cx<<32)^(cz&0xffffffffL);
        return sites.get().computeIfAbsent(key,k->plan(cx,cz));
    }
    public Pool pool(int x,int z) { return site(x,z).pool(); }
    public Arch arch(int x,int z) { return site(x,z).arch(); }
    public double poolFloor(Pool pool,int x,int z,double surface) { return pool.floor(x,z,surface,noise); }
    private Site plan(int cx,int cz) {
        int centerX=cx*96+48,centerZ=cz*96+48;
        if(arches && noise.value(cx,cz,800)<0.55) {
            for(int attempt=0;attempt<4;attempt++) {
                int x=centerX+(int)(noise.value(cx,cz,801+attempt)*24)-12;
                int z=centerZ+(int)(noise.value(cx,cz,811+attempt)*24)-12;
                if(!shore.contains(x,z) || ground.sample(x,z).original()<sea+22) continue;
                double width=4+5*noise.value(cx,cz,821+attempt), length=13+8*noise.value(cx,cz,831+attempt);
                double height=14+14*noise.value(cx,cz,841+attempt),lean=2*(noise.value(cx,cz,851+attempt)-0.5);
                for(int direction=0;direction<8;direction++) {
                    Arch arch=new Arch(x,z,direction*Math.PI/8,width,length,height,lean,sea);
                    if(validArch(arch)) return new Site(null,arch);
                }
            }
        }
        if(noise.value(cx,cz,900)>0.8) return EMPTY;
        for(int attempt=0;attempt<3;attempt++) {
            Site candidate=planPool(cx,cz,attempt);
            if(candidate.pool()!=null) return candidate;
        }
        return EMPTY;
    }
    private Site planPool(int cx,int cz,int attempt) {
        int centerX=cx*96+48,centerZ=cz*96+48,salt=attempt*37;
        int x=centerX+(int)(noise.value(cx,cz,901+salt)*52)-26;
        int z=centerZ+(int)(noise.value(cx,cz,902+salt)*52)-26;
        if(!shore.contains(x,z) || ground.sample(x,z).surface()<sea+7) return EMPTY;
        double rx=6+(attempt==2?4:10)*noise.value(cx,cz,903+salt),rz=5+(attempt==2?4:9)*noise.value(cx,cz,904+salt);
        double angle=noise.value(cx,cz,905+salt)*Math.PI;
        int depth=2+(int)(noise.value(cx,cz,906+salt)*3);
        Pool shape=new Pool(x,z,rx,rz,angle,0,depth,907+salt);
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        int extent=(int)Math.ceil(Math.max(rx,rz)*1.12)+2;
        // Every block of footprint/rim participates, including across chunk boundaries.
        for(int px=x-extent;px<=x+extent;px++) for(int pz=z-extent;pz<=z+extent;pz++) {
            if(shape.radius(px,pz,noise)>1.16) continue;
            if(!shore.contains(px,pz)) return EMPTY;
            Base b=ground.sample(px,pz);
            if(b.mask()<0.75) return EMPTY;
            low=Math.min(low,b.surface()); high=Math.max(high,b.surface());
        }
        int water=(int)Math.floor(low+0.5);
        if(water<sea+5 || high-low>6 || !Double.isFinite(low)) return EMPTY;
        Pool pool=new Pool(x,z,rx,rz,angle,water,depth,907+salt);
        // Require solid original rock beneath the floor, throughout the water volume, and
        // in the rim. An unsupported or cave-punctured site rejects the WHOLE basin.
        for(int px=x-extent;px<=x+extent;px++) for(int pz=z-extent;pz<=z+extent;pz++) {
            if(pool.radius(px,pz,noise)>1.16) continue;
            for(int y=water-depth-3;y<=water;y++) if(terrain.density(px,y,pz)<=0) return EMPTY;
        }
        return new Site(pool,null);
    }
    private boolean validArch(Arch a) {
        double c=Math.cos(a.angle()),s=Math.sin(a.angle());
        // Both portals must already open towards low ground/water: do not label a blind cave an arch.
        for(int sign:new int[]{-1,1}) {
            int px=(int)Math.round(a.x()-sign*s*(a.length()+2));
            int pz=(int)Math.round(a.z()+sign*c*(a.length()+2));
            if(ground.sample(px,pz).original()>sea+3) return false;
            // Thick original piers on either side, with a solid base and roof connection.
            px=(int)Math.round(a.x()+sign*c*(a.width()+4));
            pz=(int)Math.round(a.z()+sign*s*(a.width()+4));
            if(!shore.contains(px,pz) || ground.sample(px,pz).original()<sea+a.height()+3) return false;
            for(int y=sea-2;y<=sea+a.height()+2;y+=2) if(terrain.density(px,y,pz)<=0) return false;
        }
        for(int along=-1;along<=1;along++) {
            int px=(int)Math.round(a.x()-s*a.length()*along*0.3);
            int pz=(int)Math.round(a.z()+c*a.length()*along*0.3);
            if(!shore.contains(px,pz) || ground.sample(px,pz).mask()<0.7
                || ground.sample(px,pz).original()<sea+a.height()+5
                || terrain.density(px,(int)Math.ceil(sea+a.height()+3),pz)<=0) return false;
        }
        return true;
    }
    static double smooth(double v) { v=Math.max(0,Math.min(1,v));return v*v*v*(v*(v*6-15)+10); }
}
