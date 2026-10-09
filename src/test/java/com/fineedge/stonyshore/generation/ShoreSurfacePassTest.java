package com.fineedge.stonyshore.generation;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoreSurfacePassTest {
    @BeforeAll static void setup() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void designatedBeachesCanReplaceNaturalSoilAndMossButPreserveConstruction() {
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.GRASS_BLOCK,Blocks.DIRT,
            Blocks.MOSS_BLOCK,Blocks.PODZOL,Blocks.COARSE_DIRT,Blocks.STONE,Blocks.SAND})
            assertTrue(ShoreSurfacePass.isSandSubstrate(block.defaultBlockState()));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.CHEST,Blocks.OAK_PLANKS,
            Blocks.GLASS,Blocks.STONE_BRICKS,Blocks.OAK_LOG})
            assertFalse(ShoreSurfacePass.isSandSubstrate(block.defaultBlockState()));
    }
    @Test void beachMaterialsAreSandDominantWithConnectedStonePatches() {
        int sand=0,total=0,agree=0;
        for(int x=-256;x<256;x++)for(int z=-256;z<256;z++) {
            boolean material=ShoreSurfacePass.beachSandAt(x,z,19);
            if(material)sand++;total++;
            if(material==ShoreSurfacePass.beachSandAt(x+1,z,19))agree++;
            assertEquals(material,ShoreSurfacePass.beachSandAt(x,z,19));
        }
        assertTrue(sand/(double)total>.8 && sand/(double)total<.995);
        assertTrue(agree/(double)total>.97,"stone occurs in coherent outcrops, not block-by-block speckling");
    }
}
