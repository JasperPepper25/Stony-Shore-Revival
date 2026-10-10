package com.fineedge.stonyshore.terrain;

import java.util.ArrayList;
import java.util.List;

/** Immutable upstream waterline geometry. Biomes select eligible coasts, never their outline. */
public final class RegionalCoast {
    public static final int OCEAN_REACH=96;
    private static final int TILE=128, GRID=16, HALO=224;
    public record Sample(double distance,double relief,double eligibility) {}
    private record Point(int x,int z,double height,boolean ocean,boolean shore) {}
    private record Segment(double ax,double az,double bx,double bz,double relief) {
        double distance(double x,double z) {
            double dx=bx-ax,dz=bz-az;
            double t=Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz)));
            return Math.hypot(x-ax-t*dx,z-az-t*dz);
        }
    }
    private record Tile(List<Segment> segments,List<Point> shores) {}
    private final CoastalProfile.Heights heights;
    private final CoastalColumnSampler.Shore shore,ocean;
    private final int sea,inlandReach;
    private final BoundedCache<Long,Point> points=new BoundedCache<>(32768);
    private final BoundedCache<Long,Tile> tiles=new BoundedCache<>(128);
    private final BoundedCache<Long,Sample> samples=new BoundedCache<>(16384);

    public RegionalCoast(int sea,int inlandReach,CoastalProfile.Heights heights,
                         CoastalColumnSampler.Shore shore,CoastalColumnSampler.Shore ocean) {
        this.sea=sea;this.inlandReach=inlandReach;this.heights=heights;this.shore=shore;this.ocean=ocean;
    }
    private Point point(int x,int z) {
        return points.get(CoastalShape.key(x,z),k->new Point(x,z,heights.sample(x,z),ocean.contains(x,z),shore.contains(x,z)));
    }
    private boolean wet(Point p) { return p.height<sea-.5; }
    private double[] crossing(Point a,Point b) {
        double t=(sea-.5-a.height)/(b.height-a.height);
        return new double[]{a.x+t*(b.x-a.x),a.z+t*(b.z-a.z)};
    }
    private void segment(List<Segment> out,double[] a,double[] b,Point[] corners) {
        if(Math.hypot(a[0]-b[0],a[1]-b[1])<.001)return;
        double dryX=0,dryZ=0,wetX=0,wetZ=0;int dry=0,water=0;boolean open=false;
        double relief=sea;
        for(Point p:corners)if(wet(p)) {wetX+=p.x;wetZ+=p.z;water++;open|=p.ocean;}
        else {dryX+=p.x;dryZ+=p.z;dry++;relief=Math.max(relief,p.height);}
        if(dry==0 || water==0)return;
        double nx=dryX/dry-wetX/water,nz=dryZ/dry-wetZ/water,length=Math.hypot(nx,nz);
        if(!open && length>0)for(int reach:new int[]{32,64,96}) {
            int ox=(int)Math.round((a[0]+b[0])*.5-nx/length*reach);
            int oz=(int)Math.round((a[1]+b[1])*.5-nz/length*reach);
            if(ocean.contains(ox,oz) && heights.sample(ox,oz)<sea-.5) {
                boolean connected=true;
                for(int step=8;step<=reach;step+=8) {
                    int cx=(int)Math.round((a[0]+b[0])*.5-nx/length*step);
                    int cz=(int)Math.round((a[1]+b[1])*.5-nz/length*step);
                    if(heights.sample(cx,cz)>=sea-.5) {connected=false;break;}
                }
                if(connected) {open=true;break;}
            }
        }
        if(!open)return;
        if(length>0) {
            int x=(int)Math.round((a[0]+b[0])*.5+nx/length*32);
            int z=(int)Math.round((a[1]+b[1])*.5+nz/length*32);
            relief=Math.max(relief,heights.sample(x,z));
        }
        out.add(new Segment(a[0],a[1],b[0],b[1],relief));
    }
    private Tile tile(int tx,int tz) {
        return tiles.get(CoastalShape.key(tx,tz),k->{
            var segments=new ArrayList<Segment>();var shores=new ArrayList<Point>();
            int minX=tx*TILE-HALO,minZ=tz*TILE-HALO,maxX=(tx+1)*TILE+HALO,maxZ=(tz+1)*TILE+HALO;
            boolean eligible=false;
            for(int x=minX;x<=maxX && !eligible;x+=GRID)for(int z=minZ;z<=maxZ;z+=GRID)
                if(shore.contains(x,z)) {eligible=true;break;}
            if(!eligible)return new Tile(List.of(),List.of());
            // Fixed world lattice and identical halo ensure neighboring tiles see the same primitives.
            for(int x=minX;x<=maxX;x+=GRID)for(int z=minZ;z<=maxZ;z+=GRID) {
                Point a=point(x,z);if(a.shore)shores.add(a);
                if(x==maxX || z==maxZ)continue;
                Point[] p={a,point(x+GRID,z),point(x+GRID,z+GRID),point(x,z+GRID)};
                var crossings=new ArrayList<double[]>(4);
                for(int edge=0;edge<4;edge++)if(wet(p[edge])!=wet(p[(edge+1)%4]))crossings.add(crossing(p[edge],p[(edge+1)%4]));
                if(crossings.size()==2)segment(segments,crossings.get(0),crossings.get(1),p);
                else if(crossings.size()==4) {
                    // Stable center-height decider for saddle cells; no per-chunk randomness.
                    boolean middleWet=(p[0].height+p[1].height+p[2].height+p[3].height)*.25<sea-.5;
                    int offset=middleWet==wet(p[0])?0:1;
                    segment(segments,crossings.get(offset),crossings.get((offset+1)%4),p);
                    segment(segments,crossings.get((offset+2)%4),crossings.get((offset+3)%4),p);
                }
            }
            return new Tile(List.copyOf(segments),List.copyOf(shores));
        });
    }
    private Sample node(int x,int z) {
        return samples.get(CoastalShape.key(x,z),k->{
            Tile t=tile(Math.floorDiv(x,TILE),Math.floorDiv(z,TILE));
            if(t.segments.isEmpty() || t.shores.isEmpty())return new Sample(256,sea,0);
            double nearest=Double.POSITIVE_INFINITY;
            for(var s:t.segments)nearest=Math.min(nearest,s.distance(x,z));
            if(nearest>Math.max(inlandReach,OCEAN_REACH)+16)return new Sample(heights.sample(x,z)<sea-.5?-256:256,sea,0);
            double seedDistance=Double.POSITIVE_INFINITY;
            for(var p:t.shores)seedDistance=Math.min(seedDistance,Math.hypot(x-p.x,z-p.z));
            double eligibility=1-CoastalShape.smooth((seedDistance-48)/80);
            // Blend nearby shoreline relief rather than switching one discrete shore anchor.
            double sum=0,weight=0;
            for(var s:t.segments) {
                double w=1-CoastalShape.smooth((s.distance(x,z)-nearest)/48);
                sum+=w*s.relief;weight+=w;
            }
            return new Sample(heights.sample(x,z)<sea-.5?-nearest:nearest,weight>0?sum/weight:sea,eligibility);
        });
    }
    public Sample sample(int x,int z) {
        int gx=Math.floorDiv(x,8)*8,gz=Math.floorDiv(z,8)*8;
        double fx=Math.floorMod(x,8)/8.0,fz=Math.floorMod(z,8)/8.0;
        Sample a=node(gx,gz),b=node(gx+8,gz),c=node(gx,gz+8),d=node(gx+8,gz+8);
        return new Sample(blend(a.distance,b.distance,c.distance,d.distance,fx,fz),
            blend(a.relief,b.relief,c.relief,d.relief,fx,fz),blend(a.eligibility,b.eligibility,c.eligibility,d.eligibility,fx,fz));
    }
    private static double blend(double a,double b,double c,double d,double x,double z) {
        return (a+(b-a)*x)*(1-z)+(c+(d-c)*x)*z;
    }
    public java.util.Map<String,Long> stats() {
        return java.util.Map.of("physicalCoastTileHits",tiles.hits(),"physicalCoastTileMisses",tiles.misses(),
            "physicalCoastNodeHits",samples.hits(),"physicalCoastNodeMisses",samples.misses());
    }
}
