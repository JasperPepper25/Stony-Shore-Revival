package com.fineedge.stonyshore.terrain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeaArchCatalogTest {
    static CoastalLandforms.Arch plan(SeaArchCatalog.Style style,long seed,double angle) {
        double c=Math.cos(angle),s=Math.sin(angle);
        CoastalShape.GroundSampler ground=(x,z)->new CoastalShape.Ground(x*c+z*s>2?45:110,1,-x*c-z*s,150,110,x*c+z*s>2);
        return SeaArchCatalog.create(style,new CoastalTerrainPlanner(seed),0,0,0,0,0,angle,63,ground.sample(0,0),ground);
    }
    @Test void everyShuffledDeckHasOneOfEachEquallyWeightedStyleAtNegativeAndPositiveCoordinates() {
        for(long seed:new long[]{0,42,-456,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var noise=new CoastalTerrainPlanner(seed);
            for(int tx=-6;tx<=6;tx++)for(int tz=-4;tz<=4;tz++) {
                var seen=EnumSet.noneOf(SeaArchCatalog.Style.class);
                for(int dx=0;dx<4;dx++)for(int dz=0;dz<3;dz++) {
                    var style=SeaArchCatalog.style(noise,tx*4+dx,tz*3+dz);
                    assertTrue(seen.add(style));assertEquals(1,style.weight());
                }
                assertEquals(12,seen.size());
            }
        }
    }
    @Test void selectingAStyleIsStableAndDifferentSeedsShuffleTheCatalog() {
        int changed=0;
        var a=new CoastalTerrainPlanner(42);var b=new CoastalTerrainPlanner(99);
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++) {
            assertEquals(SeaArchCatalog.style(a,x,z),SeaArchCatalog.style(a,x,z));
            if(SeaArchCatalog.style(a,x,z)!=SeaArchCatalog.style(b,x,z))changed++;
        }
        assertTrue(changed>1000);
    }
    @Test void familiesChangeSilhouettesOpeningAspectAndStackArrangements() {
        for(var style:SeaArchCatalog.Style.values()) {
            var a=plan(style,42,0);assertNotNull(a);var f=a.fin();
            if(style.rising())assertTrue(f.top((int)(f.reach()*.76),0)>f.top(5,0)+6,"fin climbs toward its seaward nose");
            if(style==SeaArchCatalog.Style.TALL_SPIRE || style==SeaArchCatalog.Style.UPRIGHT_KEYHOLE)
                assertTrue(a.height()*.57/a.width()>2,"upright opening remains visibly narrow");
            if(style==SeaArchCatalog.Style.LOW_SLOT || style==SeaArchCatalog.Style.LOW_WINDOW_FIN)
                assertTrue(a.width()/(a.height()*.57)>2,"low opening remains a horizontal ellipse");
            int expected=style==SeaArchCatalog.Style.STACK_GROUP?2:
                style==SeaArchCatalog.Style.STACK_PIER || style==SeaArchCatalog.Style.SENTINEL?1:0;
            assertEquals(expected,f.catalog().stacks().size());
            assertEquals(58,f.radius(),"all catalog families use the same arbitration radius");
        }
    }
    @Test void cachedColumnsExactlyMatchTheDirectVolumeAndOpeningAtAllRotations() {
        for(var style:SeaArchCatalog.Style.values())for(int rotation=0;rotation<4;rotation++) {
            var a=plan(style,42,rotation*Math.PI/2);var f=a.fin();
            for(int x=-84;x<=84;x+=7)for(int z=-84;z<=84;z+=7) {
                var section=new SeaArchCatalog.Section(a,x,z);
                for(int y:new int[]{41,62,70,89,110,139,165}) {
                    assertEquals(f.solid(x,y,z),section.solid(y),0);
                    assertEquals(a.opening(x,y,z),section.opening(y),0);
                    if(section.solid(y)>0)assertTrue(f.reserve(x,z)>0);
                }
                if(f.reserve(x,z)>0)assertTrue(Math.hypot(x-f.centerX(),z-f.centerZ())<=f.radius());
            }
        }
    }
    @Test void allFamiliesHaveATwoSidedPassageAndAnIntactRoof() {
        for(var style:SeaArchCatalog.Style.values())for(long seed:new long[]{42,99,456}) {
            var a=plan(style,seed,0);assertNotNull(a);var f=a.fin();
            int y=(int)Math.round(a.openingCenterY());
            for(int z=-(int)Math.floor(a.length());z<=(int)Math.floor(a.length());z++)
                assertTrue(a.opening(a.x(),y,z)<0,style+" open through both faces");
            for(int u=-2;u<=2;u++)for(int v=-2;v<=2;v++) {
                int x=a.x()+u,z=a.z()+v,roof=(int)Math.floor(f.top(x,z))-2;
                assertTrue(a.opening(x,roof,z)>0,style+" keeps its roof above the aperture");
                assertTrue(f.solid(x,roof,z)>0,style+" roof is inside the complete solid");
            }
        }
    }
    @Test void detachedStacksHaveARealGapAndAttachedPiersIntersectTheFin() {
        for(long seed:new long[]{42,99,456}) {
            var attached=plan(SeaArchCatalog.Style.STACK_PIER,seed,0);
            var stack=attached.fin().catalog().stacks().get(0);
            assertTrue(attached.fin().solid(stack.x(),75,stack.z())>0);
            var detached=plan(SeaArchCatalog.Style.SENTINEL,seed,0);var f=detached.fin();
            stack=f.catalog().stacks().get(0);
            assertTrue(stack.x()-stack.rx()*1.25-Math.abs(stack.lean())>f.reach()+1);
            assertTrue(stack.solid(stack.x(),75,stack.z())>0,"stack stands above water");
            int gap=(int)Math.ceil(f.reach()+2);
            for(int y=64;y<=90;y++)assertTrue(f.solid(gap,y,0)<0,"sentinel is separated by water/air");
        }
    }
    @Test void productionPlannerKeepsItsRegionStyleRatherThanRerollingForAnEasierFit() {
        for(long seed:new long[]{42,123,456}) {
            var coast=CoastalProjectionTest.coast(seed,false,true,false);
            var arches=CoastalProjectionTest.arches(coast);assertFalse(arches.isEmpty());
            for(var arch:arches) {
                var f=arch.fin();
                assertEquals(SeaArchCatalog.style(new CoastalTerrainPlanner(seed),Math.floorDiv(f.rootX(),48),Math.floorDiv(f.rootZ(),48)),
                    f.catalog().style());
                assertEquals(arch,coast.column(arch.x(),arch.z()).arch());
                assertNotNull(coast.column(arch.x(),arch.z()).archSection());
            }
        }
    }
    @Test void seaStacksRequireExistingSeabedSupport() {
        CoastalShape.GroundSampler ground=(x,z)->new CoastalShape.Ground(x<0?45:110,1,x,Math.max(0,96-x),110,x<0);
        var coast=new CoastalShape(123,63,ground,new CoastalShape.Options(true,false,true,false,0),
            (x,z)->x<0?45:110,(x,y,z)->x>=0);
        var arches=CoastalProjectionTest.arches(coast);assertFalse(arches.isEmpty());
        for(var a:arches)assertTrue(a.fin().catalog().stacks().isEmpty(),"a detached stack cannot float above unsupported ocean columns");
    }
}
