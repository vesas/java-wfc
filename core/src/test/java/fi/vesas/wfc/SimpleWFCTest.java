package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fi.vesas.wfc.SimpleWFC.DIR;

public class SimpleWFCTest {
    
    @Test
    public void testRotation1() {

        // 
        // Setup the test case
        // 
        SimpleWFC wfc = new SimpleWFC(2,1, 3, true, true);
        
        Constraints constraints = new Constraints();
        wfc.setSeed(999);
        
        // tile 0 is the empty tile
        constraints.addPort(1, DIR.S, 1);
        constraints.addPort(2, DIR.N, 1);

        wfc.setValuesAt(0,0,1);
        wfc.setRotationsAt(0,0,8);

        wfc.setValuesAt(1,0,0,2);
        wfc.setRotationsAt(1,0,15,15);
        
        wfc.setConstraints(constraints);
        wfc.printConstraints();
        wfc.printGrid();
        wfc.printRotations();
        
        // 
        // Run just one round
        // 
        wfc.runOneRound();
        wfc.printGrid();
        wfc.printRotations();


        // 
        // Examine and assert results
        // 
        int [] grid0 = wfc.getGrid(0, 0);
        int [] rots0 = wfc.getRots(0, 0);
        
        int [] grid1 = wfc.getGrid(1, 0);
        int [] rots1 = wfc.getRots(1, 0);

        // position [0,0] should have only tile 1 as a possibility at this point
        assertArrayEquals(new int[]{1}, grid0);
        // position [1,0] should have only tile 2 as a possibility at this point
        assertArrayEquals(new int[]{2}, grid1);

        // position [0,0] should have only rotation 8 as a possibility. (possible rotations are 1,2,4,8 and combinations)
        assertArrayEquals(new int[]{8}, rots0);
        // position [1,0] should have only rotation 8 as a possibility as well. (possible rotations are 1,2,4,8 and combinations)
        assertArrayEquals(new int[]{8}, rots1);

    }

    @Test
    public void testRotation2() {
        SimpleWFC wfc = new SimpleWFC(2,2, 3, true, true);
        
        Constraints constraints = new Constraints();
        wfc.setSeed(3);
        
        // tile 0 is the empty tile
        // tile 1 has one port facing south
        constraints.addPort(1, DIR.S, 1);
        // tile 2 has two ports, south and west
        constraints.addPort(2, DIR.S, 1);
        constraints.addPort(2, DIR.W, 1);

        wfc.setConstraints(constraints);
        wfc.printConstraints();

        wfc.printGrid();
        wfc.printRotations();

        wfc.runOneRound();

        wfc.printGrid();
        wfc.printRotations();

        int [] grid0 = wfc.getGrid(0, 0);
        int [] rots0 = wfc.getRots(0, 0);
        
        // position [0,0] should have only tile 2 as a possibility at this point
        assertArrayEquals(new int[]{2}, grid0);

        // position [0,0] should have only rotation 4 as a possibility. (possible rotations are 1,2,4,8 and combinations)
        assertArrayEquals(new int[]{4}, rots0);

    }

    @Test
    public void testRotation() {
        SimpleWFC wfc = new SimpleWFC(2,1, 3, true, true);
        
        Constraints constraints = new Constraints();
        wfc.setSeed(999);
        
        // tile 0 is the empty tile
        constraints.addPort(1, DIR.S, 1);
        constraints.addPort(2, DIR.N, 1);
        
        wfc.setConstraints(constraints);
        wfc.printConstraints();
        wfc.printGrid();
        wfc.printRotations();

        wfc.runOneRound();
        wfc.printGrid();
        wfc.printRotations();

        wfc.runOneRound();
        wfc.printGrid();
        wfc.printRotations();
    }

    @Test
    public void test1() {
        

        SimpleWFC wfc = new SimpleWFC(1,2, 3, true, true);
        wfc.setSeed(5115);

        Constraints constraints = new Constraints();
        
        constraints.addPort(1, DIR.S, 2);


        wfc.setConstraints(constraints);
        wfc.printConstraints();
        wfc.printGrid();

        // ############
        // ROUNDS
        // ############
        wfc.runOneRound();
        wfc.printGrid();

        wfc.runOneRound();
        wfc.printGrid();

        wfc.runOneRound();
        wfc.printGrid();


    }

    @Test
    public void testFindLowEntropy() {
        SimpleWFC wfc = new SimpleWFC(2, 2, 3, true, true);
    
        wfc.setValuesAt(0, 0, 0, 1, 2);     // entropy = 3
        wfc.setValuesAt(1, 0, 1);           // entropy = 1
        wfc.setValuesAt(0, 1, 0, 1);        // entropy = 2
        wfc.setValuesAt(1, 1, 1, 2);        // entropy = 2
        
        wfc.printGrid();
        // Position [0,1] (2) has low entropy of 2x
        // This method ignores positions with only one possibility
        int lowEntropyPos = wfc.findLowEntropy();
        
        assertEquals(2, lowEntropyPos, "Index with low entropy should be 2");
    }

    @Test
    public void testPropagationReachesDistantCells() {
        // 1x4 vertical grid, 3 tiles, no empty, no rotations, no tiling
        // Constraints form a chain: tile1 --N/port10--> tile2 --N/port20--> tile3
        SimpleWFC wfc = new SimpleWFC(1, 4, 3, false, false);
        wfc.setSeed(42);

        Constraints constraints = new Constraints();
        constraints.addPort(1, DIR.N, 10);  // tile 1 connects north via port 10
        constraints.addPort(2, DIR.S, 10);  // tile 2 receives from south via port 10
        constraints.addPort(2, DIR.N, 20);  // tile 2 connects north via port 20
        constraints.addPort(3, DIR.S, 20);  // tile 3 receives from south via port 20

        wfc.setConstraints(constraints);

        // First observe: picks (0,0) — bottom edge forces tile 1 (only tile without south port).
        // Propagation should chain: (0,0)->tile1 narrows (0,1)->tile2 narrows (0,2)->tile3 narrows (0,3)->tile1
        wfc.runOneRound();

        wfc.printGrid();

        // (0,0) collapsed to tile 1 (only valid at bottom edge — tiles 2,3 have south ports)
        assertArrayEquals(new int[]{1}, wfc.getGrid(0, 0));
        // (0,1) narrowed to tile 2 (direct neighbor of collapse point, port 10 match)
        assertArrayEquals(new int[]{2}, wfc.getGrid(0, 1));
        // (0,2) must be narrowed to tile 3 via propagation THROUGH (0,1)
        // If propagation doesn't chain past direct neighbors, this stays {1,2,3}
        assertArrayEquals(new int[]{3}, wfc.getGrid(0, 2));
        // (0,3) must be narrowed to tile 1 (tile 3 has no north port, so only tile 1 fits)
        assertArrayEquals(new int[]{1}, wfc.getGrid(0, 3));
    }

    @Test
    public void testBacktrackingFindsValidSolution() {
        // 2x2 grid, 3 tiles, no empty, no rotations, no tiling
        // Constraints are tight enough that some random choices will fail,
        // requiring backtracking to find a valid assignment.
        //
        // Tile 1: port 10 on N, port 10 on E
        // Tile 2: port 10 on S, port 10 on W
        // Tile 3: (no ports — filler)
        //
        // Valid 2x2 layouts require tile 1 bottom-left and tile 2 top-right
        // (or rotated equivalents). A random first pick of tile 3 in the wrong
        // spot can create a contradiction.
        //
        // We test via run() which loops observe+propagate+backtrack until done.

        // Try multiple seeds — at least some will require backtracking
        int successCount = 0;
        for(int seed = 0; seed < 20; seed++) {
            SimpleWFC wfc = new SimpleWFC(2, 2, 3, false, false);
            wfc.setSeed(seed);

            Constraints constraints = new Constraints();
            constraints.addPort(1, DIR.N, 10);
            constraints.addPort(1, DIR.E, 10);
            constraints.addPort(2, DIR.S, 10);
            constraints.addPort(2, DIR.W, 10);

            wfc.setConstraints(constraints);
            wfc.run();

            assertTrue(wfc.isFinished(), "seed " + seed + " should finish");
            assertFalse(wfc.isContradiction(), "seed " + seed + " should not end in contradiction");

            // Every cell must have exactly 1 tile
            for(int x = 0; x < 2; x++) {
                for(int y = 0; y < 2; y++) {
                    assertEquals(1, wfc.getGrid(x, y).length,
                        "seed " + seed + " cell (" + x + "," + y + ") should be collapsed");
                }
            }
            successCount++;
        }

        assertEquals(20, successCount, "All seeds should produce a valid solution");
    }

    @Test
    public void testBacktrackingWithLinearChain() {
        // 1x6 grid with vertical tiling and a strict cyclic chain: 1->2->3->1->2->3
        // Every tile has ports on both N and S, so random picks that break the
        // cycle will cause contradictions — backtracking must recover.
        SimpleWFC wfc = new SimpleWFC(1, 6, 3, false, false);
        wfc.setSeed(7);
        wfc.setTilingVertical(true);

        Constraints constraints = new Constraints();
        constraints.addPort(1, DIR.N, 10);
        constraints.addPort(1, DIR.S, 30);
        constraints.addPort(2, DIR.S, 10);
        constraints.addPort(2, DIR.N, 20);
        constraints.addPort(3, DIR.S, 20);
        constraints.addPort(3, DIR.N, 30);

        wfc.setConstraints(constraints);
        wfc.run();

        assertTrue(wfc.isFinished(), "should finish");
        assertFalse(wfc.isContradiction(), "should not end in contradiction");

        // Verify every cell is collapsed
        for(int y = 0; y < 6; y++) {
            int[] cell = wfc.getGrid(0, y);
            assertEquals(1, cell.length, "cell (0," + y + ") should be collapsed");
        }

        // Verify the chain: each cell's north neighbor must follow the pattern
        for(int y = 0; y < 6; y++) {
            int tile = wfc.getGrid(0, y)[0];
            int northY = (y + 1) % 6;
            int northTile = wfc.getGrid(0, northY)[0];
            int expectedNorth = (tile % 3) + 1;  // 1->2, 2->3, 3->1
            assertEquals(expectedNorth, northTile,
                "tile " + tile + " at y=" + y + " should have tile " + expectedNorth + " to the north");
        }
    }

    @Test
    public void testBacktrackingExhaustedOnImpossibleConstraints() {
        // 1x4 grid with vertical tiling and a 3-cycle: 1->2->3->1
        // 4 is not divisible by 3, so the cycle can never close — no valid solution.
        // Backtracking should exhaust all possibilities and report failure.
        SimpleWFC wfc = new SimpleWFC(1, 4, 3, false, false);
        wfc.setSeed(0);
        wfc.setTilingVertical(true);

        Constraints constraints = new Constraints();
        constraints.addPort(1, DIR.N, 10);
        constraints.addPort(1, DIR.S, 30);
        constraints.addPort(2, DIR.S, 10);
        constraints.addPort(2, DIR.N, 20);
        constraints.addPort(3, DIR.S, 20);
        constraints.addPort(3, DIR.N, 30);

        wfc.setConstraints(constraints);
        wfc.run();

        assertTrue(wfc.isContradiction(), "should end in contradiction (impossible constraints)");
        assertTrue(wfc.getBacktrackCount() > 0,
            "should have attempted backtracking before giving up (count=" + wfc.getBacktrackCount() + ")");
    }

    @Test
    public void testDirRotations() {
        // Test CCW rotations
        assertEquals(DIR.W, DIR.N.rotateCCW(1));
        assertEquals(DIR.S, DIR.N.rotateCCW(2));
        assertEquals(DIR.E, DIR.N.rotateCCW(3));
        assertEquals(DIR.N, DIR.N.rotateCCW(4));
        
        // Test CW rotations
        assertEquals(DIR.E, DIR.N.rotateCW(1));
        assertEquals(DIR.S, DIR.N.rotateCW(2));
        assertEquals(DIR.W, DIR.N.rotateCW(3));
        assertEquals(DIR.N, DIR.N.rotateCW(4));
        
    }

    
}
