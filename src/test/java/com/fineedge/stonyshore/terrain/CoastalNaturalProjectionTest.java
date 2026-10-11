package com.fineedge.stonyshore.terrain;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalNaturalProjectionTest {
    private static int x(double angle,double along,double across) {
        return (int)Math.round(along*Math.cos(angle)-across*Math.sin(angle));
    }
    private static int z(double angle,double along,double across) {
        return (int)Math.round(along*Math.sin(angle)+across*Math.cos(angle));
    }

    @Test void naturalFinsHaveTaperedCrestsAndAnUnbrokenBasalCore() {
        for(double taper:new double[]{.12,.85})for(double phase:new double[]{.3,2.1,5.2}) {
            var fin=new CoastalProjection.Fin(0,0,0,60,8,130,40,taper,2,1.2,phase);
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(int along=0;along<=44;along++) {low=Math.min(low,fin.top(along,0));high=Math.max(high,fin.top(along,0));}
            assertTrue(high-low>1,"rounded crest is allowed to be shallow but cannot be a flat extrusion");
            if(taper>.5)assertTrue(fin.top(8,0)-fin.top(44,0)>10,"rock-fin family has a strong declining crest");
            for(int along=0;along<=42;along++) {
                for(int y=42;y<=95;y++)assertTrue(fin.solid(along,y,0)>0,"weathered faces retain a solid inner core");
                assertTrue(Math.abs(fin.top(along+1,0)-fin.top(along,0))<2,"crest variation stays coherent between voxels");
            }
            assertTrue(fin.solid(24,70,7)>fin.solid(24,115,7),"basal buttress is wider than the upper spine");
        }
    }

    @Test void seededWeatheringBreaksMirrorSymmetryWithoutMovingTheCore() {
        var a=new CoastalProjection.Fin(0,0,0,52,8,128,38,.6,2,1.4,.4);
        var b=new CoastalProjection.Fin(0,0,0,52,8,128,38,.6,2,1.4,2.4);
        boolean asymmetric=false,distinct=false;
        for(int along=4;along<34;along+=2)for(int y=50;y<116;y+=3) {
            asymmetric|=Math.abs(a.solid(along,y,7)-a.solid(along,y,-7))>.15;
            distinct|=Math.abs(a.solid(along,y,7)-b.solid(along,y,7))>.15;
            assertTrue(a.solid(along,y,0)>0);
        }
        assertTrue(asymmetric,"two fin faces must not repeat an identical extrusion");
        assertTrue(distinct,"stable per-plan phase gives different weathering to adjacent forms");
    }

    @Test void naturalFinAndLedgeBoundsContainTheirCompleteWeatheredFootprints() {
        for(int rotation=0;rotation<16;rotation++) {
            double angle=rotation*Math.PI/8;
            var fin=new CoastalProjection.Fin(0,0,angle,66,10,145,35,.9,3,1.5,2.3);
            var ledge=new CoastalProjection.Ledge(0,0,angle,24,19,125,36,4,1.5,1.7);
            for(int px=-88;px<=88;px+=2)for(int pz=-88;pz<=88;pz+=2) {
                if(fin.reserve(px,pz)>0)assertTrue(Math.hypot(px-fin.centerX(),pz-fin.centerZ())<=fin.radius());
                if(ledge.reserve(px,pz)>0)assertTrue(Math.hypot(px-ledge.centerX(),pz-ledge.centerZ())<=ledge.radius());
                for(int y:new int[]{40,65,90,115,140}) {
                    if(fin.solid(px,y,pz)>0)assertTrue(fin.reserve(px,pz)>0,"solid fin cannot escape its planning footprint");
                    if(ledge.solid(px,y,pz)>0)assertTrue(ledge.reserve(px,pz)>0,"solid ledge cannot escape its planning footprint");
                }
            }
            int tipX=x(angle,69,0),tipZ=z(angle,69,0);
            for(int y=20;y<=155;y++)assertTrue(fin.solid(tipX,y,tipZ)<0,"terminal cap closes the full body");
        }
    }

    @Test void erodedUndersideRisesFromADeepRootToAThinLipAtEveryRotation() {
        for(int rotation=0;rotation<8;rotation++) {
            double angle=rotation*Math.PI/4;
            var ledge=new CoastalProjection.Ledge(0,0,angle,20,15,125,32,3,1,.9);
            int rootX=x(angle,0,0),rootZ=z(angle,0,0),lipX=x(angle,17,0),lipZ=z(angle,17,0);
            double rootThickness=ledge.top(rootX,rootZ)-ledge.underside(rootX,rootZ);
            double lipThickness=ledge.top(lipX,lipZ)-ledge.underside(lipX,lipZ);
            assertTrue(rootThickness>30,"attachment includes an erosion buttress into the lower cliff");
            assertTrue(lipThickness<6,"outer ledge is a visibly thinner eroded lip");
            assertTrue(ledge.underside(lipX,lipZ)-ledge.underside(rootX,rootZ)>25);
            assertTrue(ledge.solid(lipX,(int)Math.floor(ledge.top(lipX,lipZ))-1,lipZ)>0);
            assertTrue(ledge.solid(lipX,(int)Math.floor(ledge.underside(lipX,lipZ))-2,lipZ)<0);
            double previous=ledge.underside(rootX,rootZ);
            for(int along=1;along<=17;along++) {
                double next=ledge.underside(x(angle,along,0),z(angle,along,0));
                assertTrue(Math.abs(next-previous)<5,"no abrupt bottom plate at the attachment");previous=next;
            }
        }
    }

    @Test void aSmallLipCanKeepRockAboveItsOpenExteriorSpace() {
        var ledge=new CoastalProjection.Ledge(0,0,0,5,8,120,9,1.8,.4,1.1);
        assertTrue(ledge.top(4,0)-ledge.underside(4,0)<3);
        assertTrue(ledge.solid(4,118,0)>0);
        assertTrue(ledge.solid(4,114,0)<0);
        assertTrue(ledge.solid(0,112,0)>0);
    }

    @Test void irregularArchPassageStaysOpenThroughTheFin() {
        var fin=new CoastalProjection.Fin(0,0,0,60,9,132,38,.7,2,1.2,2.1);
        var arch=new CoastalLandforms.Arch(25,0,0,10,12,31,1,63,fin);
        int openingY=(int)Math.round(63+arch.height()*.43);
        for(int across=-12;across<=12;across++)assertTrue(arch.opening(25,openingY,across)<=0);
        for(int across=-3;across<=3;across++) {
            assertTrue(arch.opening(25,95,across)>0,"roof is above the bounded opening");
            assertTrue(fin.solid(25,98,across)>0,"the fin retains a thick roof across the passage core");
        }
    }

    @Test void weatheredTerminalCapJoinsItsPierWithoutASuddenShoulder() {
        for(double phase:new double[]{.2,2.2,4.6}) {
            var fin=new CoastalProjection.Fin(0,0,0,54,9,132,35,.8,2,1.2,phase);
            // Natural taper begins its terminal closure at X42; shoulders must
            // not suddenly collapse to the middle of the full-height pier.
            for(int across=-6;across<=6;across+=2) {
                assertTrue(fin.edge(41,across)>0 && fin.edge(43,across)>0);
                assertTrue(Math.abs(fin.top(43,across)-fin.top(42,across))<3);
                assertTrue(Math.abs(fin.bottom(43,across)-fin.bottom(42,across))<3);
            }
            assertTrue(fin.top(54,0)<fin.crest()-25);
            assertTrue(fin.bottom(53,0)>fin.floor()+15);
        }
    }

    @Test void productionPlannerAcceptsBothArchFamiliesWithSolidVariedRoofs() {
        boolean rounded=false,rockFin=false;
        double shortest=Double.POSITIVE_INFINITY,longest=0,lowest=Double.POSITIVE_INFINITY,tallest=0;
        int accepted=0;
        for(long seed:new long[]{42,123,456}) {
            var coast=CoastalProjectionTest.coast(seed,false,true,false);
            Set<CoastalLandforms.Arch> found=new HashSet<>();
            for(int px=-48;px<=48;px+=4)for(int pz=-640;pz<=640;pz+=4) {
                var arch=coast.column(px,pz).arch();if(arch!=null)found.add(arch);
            }
            for(var arch:found) {
                accepted++;var fin=arch.fin();rounded|=fin.taper()<.5;rockFin|=fin.taper()>.5;
                shortest=Math.min(shortest,fin.reach());longest=Math.max(longest,fin.reach());
                lowest=Math.min(lowest,arch.height());tallest=Math.max(tallest,arch.height());
                assertTrue(fin.reach()>=34 && arch.height()>=14,"large baseline arches survive support validation");
                assertTrue(fin.top(arch.x(),arch.z())-(63+arch.height())>6,"passage retains substantial crown rock");
                for(double along:new double[]{-arch.width()*.3,0,arch.width()*.3})
                    for(double across:new double[]{-fin.thickness()*.25,0,fin.thickness()*.25}) {
                        int px=(int)Math.round(arch.x()+along*Math.cos(arch.angle())-across*Math.sin(arch.angle()));
                        int pz=(int)Math.round(arch.z()+along*Math.sin(arch.angle())+across*Math.cos(arch.angle()));
                        int roof=(int)Math.floor(fin.top(px,pz))-2;
                        assertTrue(arch.opening(px,roof,pz)>0,"sample is above the complete irregular opening");
                        assertTrue(fin.solid(px,roof,pz)>0,"roof probe stays inside the analytic solid");
                        assertTrue(coast.density(((px<0?45:110)-roof)*.15,px,roof,pz,.15)>0,
                            "accepted roof stays solid after planner and density integration");
                    }
            }
        }
        assertTrue(accepted>=4);
        assertTrue(rounded && rockFin,"both silhouettes survive production acceptance checks");
        assertTrue(longest-shortest>8 && tallest-lowest>5,"accepted plans vary in length and opening height");
    }

    @Test void productionPlannerAcceptsTinyLipsAndDeeplyRootedOverhangs() {
        boolean small=false,substantial=false;
        for(long seed:new long[]{42,123,456}) {
            var coast=CoastalProjectionTest.coast(seed,false,false,false);
            Set<CoastalLandforms.Overhang> found=new HashSet<>();
            for(int px=-32;px<=32;px+=2)for(int pz=-640;pz<=640;pz+=4) {
                var ledge=coast.column(px,pz).overhang();if(ledge!=null)found.add(ledge);
            }
            for(var overhang:found) {
                var ledge=overhang.ledge();boolean tiny=ledge.reach()<8;small|=tiny;substantial|=!tiny;
                int px=overhang.x(),pz=overhang.z();
                int roof=(int)Math.floor(ledge.top(px,pz))-1,below=(int)Math.floor(ledge.underside(px,pz))-2;
                assertTrue(coast.density(((px<0?45:110)-roof)*.15,px,roof,pz,.15)>0);
                assertTrue(coast.density(((px<0?45:110)-below)*.15,px,below,pz,.15)<0);
                double rootThickness=ledge.top(ledge.rootX(),ledge.rootZ())-ledge.underside(ledge.rootX(),ledge.rootZ());
                double lipThickness=ledge.top(px,pz)-ledge.underside(px,pz);
                assertTrue(rootThickness>lipThickness+4,"validated overhang retains an eased cliff buttress");
                if(tiny)assertTrue(lipThickness<4,"tiny accepted ledges remain thin at their exterior lips");
            }
        }
        assertTrue(small && substantial,"tiny lips and broad overhangs both survive cliff support validation");
    }
}
