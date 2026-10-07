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
            double edge=Math.hypot(u(px,pz)/(width+6),v(px,pz)/(length+7));
            return (1-smooth((edge-0.8)/0.45))*(1-smooth((Math.abs(v(px,pz))-(length-4))/4));
        }
    }
    public record Overhang(int x,int z,double angle,double width,double reach,double height,int floor) {
        public double opening(int px,int y,int pz) {
            double dx=px-x,dz=pz-z,u=dx*Math.cos(angle)+dz*Math.sin(angle),v=-dx*Math.sin(angle)+dz*Math.cos(angle);
            return Math.sqrt(u*u/(width*width)+v*v/(reach*reach)+Math.pow((y-floor-height*0.45)/(height*0.55),2))-1;
        }
        double reserve(int px,int pz) {
            double r=Math.hypot((px-x)/(reach+width),(pz-z)/(reach+width));
            return 1-smooth((r-0.4)/0.6);
        }
    }
    private record Shelf(Pool shape,double level,Pool pool) {}
    private record Site(Shelf shelf, Arch arch, Overhang overhang) {}
    private static final Site EMPTY=new Site(null,null,null);
    private final CoastalTerrainPlanner noise;
    private final Ground ground;
    private final CoastalColumnSampler.Terrain terrain;
    private final CoastalColumnSampler.Shore shore;
    private final int sea;
    private final boolean arches;
    private final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.LongAdder> counters=new java.util.concurrent.ConcurrentHashMap<>();
    private void count(String name) { counters.computeIfAbsent(name,k->new java.util.concurrent.atomic.LongAdder()).increment(); }
    public Map<String,Long> stats() {
        Map<String,Long> result=new java.util.TreeMap<>();counters.forEach((k,v)->result.put(k,v.sum()));return result;
    }
    private final ThreadLocal<Map<Long,Site>> sites=ThreadLocal.withInitial(()->new LinkedHashMap<>(64,0.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<Long,Site> e) { return size()>64; }
    });
    public CoastalLandforms(long seed,int sea,Ground ground,CoastalColumnSampler.Terrain terrain,
                            CoastalColumnSampler.Shore shore,boolean arches) {
        this.noise=new CoastalTerrainPlanner(seed);this.sea=sea;this.ground=ground;
        this.terrain=terrain;this.shore=shore;this.arches=arches;
    }
    private Site site(int x,int z) {
        int cx=Math.floorDiv(x,64),cz=Math.floorDiv(z,64);long key=((long)cx<<32)^(cz&0xffffffffL);
        return sites.get().computeIfAbsent(key,k->plan(cx,cz));
    }
    public Pool pool(int x,int z) { var shelf=site(x,z).shelf();return shelf==null?null:shelf.pool(); }
    public Arch arch(int x,int z) { return site(x,z).arch(); }
    public Overhang overhang(int x,int z) { return site(x,z).overhang(); }
    public boolean containsPool(Pool pool,int x,int z) { return pool.radius(x,z,noise)<1; }
    public double poolFloor(Pool pool,int x,int z,double surface) { return pool.floor(x,z,surface,noise); }
    public double shelfSurface(int x,int z,double surface) {
        var shelf=site(x,z).shelf();if(shelf==null)return surface;
        double r=shelf.shape().radius(x,z,noise);
        double blend=1-smooth((r-0.58)/0.42);
        return surface+blend*(Math.min(surface,shelf.level())-surface);
    }
    private Site plan(int cx,int cz) {
        count("regionsPlanned");
        int centerX=cx*64+32,centerZ=cz*64+32;
        if(arches && noise.value(cx,cz,800)<0.7) {
            for(int attempt=0;attempt<12;attempt++) {
                // A portal and its piers must fit entirely within the planning cell.
                int x=centerX+(int)(noise.value(cx,cz,801+attempt)*16)-8;
                int z=centerZ+(int)(noise.value(cx,cz,831+attempt)*16)-8;
                if(!shore.contains(x,z) || ground.sample(x,z).original()<sea+22)continue;
                double width=4+4*noise.value(cx,cz,861+attempt),length=10+5*noise.value(cx,cz,881+attempt);
                double height=12+15*noise.value(cx,cz,921+attempt),lean=2*(noise.value(cx,cz,951+attempt)-0.5);
                for(int direction=0;direction<12;direction++) {
                    count("archCandidates");
                    Arch arch=new Arch(x,z,direction*Math.PI/12,width,length,height,lean,sea);
                    if(validArch(arch)) {count("archesAccepted");return new Site(null,arch,null);}
                }
            }
        }
        // Local shelves have different elevations; untouched gaps preserve taller cliff faces.
        if(noise.value(cx,cz,1000)<0.85)for(int attempt=0;attempt<16;attempt++) {
            int salt=attempt*37;
            int x=centerX+(int)(noise.value(cx,cz,1001+salt)*28)-14;
            int z=centerZ+(int)(noise.value(cx,cz,1002+salt)*28)-14;
            if(!shore.contains(x,z) || ground.sample(x,z).surface()<sea+12)continue;
            count("shelfCandidates");
            // Smaller sites can fit broken cliff shelves; broad sites remain possible.
            double rx=6+8*noise.value(cx,cz,1003+salt),rz=6+8*noise.value(cx,cz,1004+salt);
            double angle=noise.value(cx,cz,1005+salt)*Math.PI;
            Pool shape=new Pool(x,z,rx,rz,angle,0,3,1007+salt);
            double low=Double.POSITIVE_INFINITY,highOriginal=Double.NEGATIVE_INFINITY;
            boolean valid=true;
            int extent=17;
            for(int px=x-extent;px<=x+extent && valid;px++)for(int pz=z-extent;pz<=z+extent;pz++) {
                if(shape.radius(px,pz,noise)>0.92)continue;
                Base b=ground.sample(px,pz);
                if(!shore.contains(px,pz) || b.mask()<0.3) {valid=false;break;}
                low=Math.min(low,b.surface());highOriginal=Math.max(highOriginal,b.original());
            }
            if(!valid) {count("shelvesRejectedTerrain");continue;}
            int water=(int)Math.floor(low-1.5-2.5*noise.value(cx,cz,1006+salt));
            if(water<sea+5 || highOriginal-water>88) {count("shelvesRejectedTerrain");continue;}
            int depth=2+(int)(3*noise.value(cx,cz,1008+salt));
            Pool pool=new Pool(x,z,rx*0.49,rz*0.49,angle,water,depth,1007+salt);
            for(int px=x-extent;px<=x+extent && valid;px++)for(int pz=z-extent;pz<=z+extent;pz++) {
                if(pool.radius(px,pz,noise)>1.2)continue;
                for(int y=water-depth-4;y<=water+1;y++)if(terrain.density(px,y,pz)<=0) {valid=false;break;}
            }
            if(!valid) {count("poolsRejectedSupport");continue;}
            count("shelvesAccepted");count("poolsAccepted");
            return new Site(new Shelf(shape,water+1.5,pool),null,null);
        }
        // A one-sided recess is an overhang, separate from the two-portal arch test.
        if(noise.value(cx,cz,1200)<0.8)for(int attempt=0;attempt<10;attempt++) {
            int x=centerX+(int)(noise.value(cx,cz,1201+attempt)*16)-8;
            int z=centerZ+(int)(noise.value(cx,cz,1221+attempt)*16)-8;
            Base b=ground.sample(x,z);if(!shore.contains(x,z) || b.mask()<0.6 || b.original()<sea+20)continue;
            for(int direction=0;direction<8;direction++) {
                double angle=direction*Math.PI/4,sn=Math.sin(angle),cs=Math.cos(angle);
                int px=(int)Math.round(x-sn*16),pz=(int)Math.round(z+cs*16);
                double outside=ground.sample(px,pz).original();
                int floor=(int)Math.max(sea+2,Math.min(b.surface()-3,outside+1));
                if(outside>floor+2 || b.original()<floor+16)continue;
                boolean solid=true;
                for(int u=-5;u<=5;u+=5)for(int v=-6;v<=6;v+=6)
                    if(terrain.density((int)(x+u*cs-v*sn),floor+15,(int)(z+u*sn+v*cs))<=0)solid=false;
                if(!solid)continue;
                count("overhangsAccepted");
                return new Site(null,null,new Overhang(x,z,angle,7,17,12,floor));
            }
        }
        return EMPTY;
    }
    private boolean validArch(Arch a) {
        double c=Math.cos(a.angle()),s=Math.sin(a.angle());
        // Both portals must already open towards low ground/water: do not label a blind cave an arch.
        for(int sign:new int[]{-1,1}) {
            int px=(int)Math.round(a.x()-sign*s*(a.length()+2));
            int pz=(int)Math.round(a.z()+sign*c*(a.length()+2));
            if(ground.sample(px,pz).surface()>sea+a.height()*0.43-1) {count("archesRejectedPortals");return false;}
            // Thick original piers on either side, with a solid base and roof connection.
            px=(int)Math.round(a.x()+sign*c*(a.width()+4));
            pz=(int)Math.round(a.z()+sign*s*(a.width()+4));
            if(!shore.contains(px,pz) || ground.sample(px,pz).original()<sea+a.height()+3) {count("archesRejectedPiers");return false;}
            for(int y=sea-2;y<=sea+a.height()+2;y+=2) if(terrain.density(px,y,pz)<=0) {count("archesRejectedPiers");return false;}
        }
        for(int along=-1;along<=1;along++) {
            int px=(int)Math.round(a.x()-s*a.length()*along*0.3);
            int pz=(int)Math.round(a.z()+c*a.length()*along*0.3);
            if(!shore.contains(px,pz) || ground.sample(px,pz).mask()<0.7
                || ground.sample(px,pz).original()<sea+a.height()+5
                || terrain.density(px,(int)Math.ceil(sea+a.height()+3),pz)<=0) {count("archesRejectedRoof");return false;}
        }
        return true;
    }
    static double smooth(double v) { v=Math.max(0,Math.min(1,v));return v*v*v*(v*(v*6-15)+10); }
}
