package fi.vesas.wfc;

import org.junit.jupiter.api.Test;

import fi.vesas.wfc.SimpleWFC;
import fi.vesas.wfc.SimpleWFC.Constraints;
import fi.vesas.wfc.SimpleWFC.DIR;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertArrayEquals(grid0, new int[]{1});
        // position [1,0] should have only tile 2 as a possibility at this point
        assertArrayEquals(grid1, new int[]{2});

        // position [0,0] should have only rotation 8 as a possibility. (possible rotations are 1,2,4,8 and combinations)
        assertArrayEquals(rots0, new int[]{8});
        // position [1,0] should have only rotation 8 as a possibility as well. (possible rotations are 1,2,4,8 and combinations)
        assertArrayEquals(rots1, new int[]{8});

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

        // wfc.setValuesAt(0,0,2);
        // wfc.setRotationsAt(1,0,15,15);
        
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

        wfc.runOneRound();
        wfc.printGrid();
        wfc.printRotations();

        wfc.runOneRound();
        wfc.printGrid();
        wfc.printRotations();

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
    public void test2() {
        SimpleWFC wfc = new SimpleWFC(2,2, 2, true, true);
        wfc.setPos(1,1, 0);
        wfc.printGrid();
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
