package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalMorphologyTest {
    @Test void tieredCoastVariesAlongShoreAndNeverRaisesExistingRock() {
        var shape=new CoastalMorphology(new CoastalTerrainPlanner(99),63);
        double min=200,max=0;
        for(int z=-512;z<=512;z+=8) {
            var g=new CoastalShape.Ground(150,1,40,56,150,false);
            double h=shape.surface(40,z,g);min=Math.min(min,h);max=Math.max(max,h);
            assertTrue(h<=150 && h>=77);
            assertEquals(150,shape.surface(150,z,new CoastalShape.Ground(150,1,150,0,150,false)));
            assertEquals(45,shape.surface(-20,z,new CoastalShape.Ground(45,1,-20,96,150,true)));
        }
        assertTrue(max-min>8,"regional height variety should be visible");
    }
    @Test void covesAreOccasionalContinuousAndBounded() {
        var shape=new CoastalMorphology(new CoastalTerrainPlanner(99),63);int absent=0,present=0;
        double previous=shape.retreat(0,-1024);
        for(int z=-1023;z<=1024;z++) {
            double retreat=shape.retreat(0,z);
            assertTrue(retreat>=0 && retreat<=29);
            assertTrue(Math.abs(retreat-previous)<2,"avoid abrupt cut walls");previous=retreat;
            if(retreat==0)absent++;if(retreat>10)present++;
        }
        assertTrue(absent>0);assertTrue(present>0);
    }
}
