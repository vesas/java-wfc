package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fi.vesas.wfc.SimpleWFC.DIR;

public class TileWeightsTest {

    @TempDir
    File tempDir;

    // Three tile IDs with no ports: any tile fits next to any other.
    private static SimpleWFC freeGrid(int seed, double[] weights) {
        SimpleWFC wfc = new SimpleWFC(30, 30, 3, true, false);
        wfc.setConstraints(new Constraints());
        wfc.setSeed(seed);
        wfc.setWeights(weights);
        wfc.run();
        return wfc;
    }

    private static int[] histogram(SimpleWFC wfc, int ids) {
        int[] h = new int[ids];
        for (int x = 0; x < wfc.getWidth(); x++)
            for (int y = 0; y < wfc.getHeight(); y++)
                h[wfc.getGrid(x, y)[0]]++;
        return h;
    }

    @Test
    public void weightsShiftTileFrequency() {
        SimpleWFC wfc = freeGrid(7, new double[]{0, 1, 9});
        assertTrue(wfc.isFinished());
        assertFalse(wfc.isContradiction());
        int[] h = histogram(wfc, 3);
        assertEquals(0, h[0], "zero-weight tile chosen while others were possible");
        double share = h[2] / 900.0;
        assertTrue(share > 0.85 && share < 0.95, "tile 2 share " + share);
    }

    @Test
    public void uniformWithoutWeights() {
        int[] h = histogram(freeGrid(7, null), 3);
        for (int c : h) assertTrue(c > 250 && c < 350, "uneven counts " + c);
    }

    @Test
    public void nullWeightsKeepOriginalSequence() {
        SimpleWFC a = freeGrid(11, null);
        SimpleWFC b = new SimpleWFC(30, 30, 3, true, false);
        b.setConstraints(new Constraints());
        b.setSeed(11);
        b.run();
        for (int x = 0; x < 30; x++)
            for (int y = 0; y < 30; y++)
                assertArrayEquals(b.getGrid(x, y), a.getGrid(x, y));
    }

    @Test
    public void allZeroWeightsStillSolve() {
        SimpleWFC wfc = freeGrid(3, new double[]{0, 0, 0});
        assertTrue(wfc.isFinished());
        assertFalse(wfc.isContradiction());
    }

    @Test
    public void negativeWeightRejected() {
        SimpleWFC wfc = new SimpleWFC(2, 2, 3, true, false);
        assertThrows(IllegalArgumentException.class, () -> wfc.setWeights(new double[]{1, -1, 1}));
    }

    @Test
    public void weightedCircuitSolvesWithValidPorts() {
        TilesetDef def = TilesetDef.loadFromJson(new File("../assets/circuit.json"));
        for (int seed = 1; seed <= 10; seed++) {
            SimpleWFC wfc = def.createSolver(16, 12);
            wfc.setSeed(seed);
            wfc.run();
            assertTrue(wfc.isFinished(), "seed " + seed);
            assertFalse(wfc.isContradiction(), "seed " + seed);
        }
    }

    // ---------- TilesetDef JSON ----------

    @Test
    public void circuitJsonMatchesBuiltIn() {
        TilesetDef json = TilesetDef.loadFromJson(new File("../assets/circuit.json"));
        TilesetDef builtIn = TilesetDef.circuitTileset();
        assertEquals(builtIn.tileCount, json.tileCount);
        assertEquals(builtIn.basename, json.basename);
        for (int t = 0; t < builtIn.tileCount; t++)
            for (DIR d : DIR.values())
                assertEquals(builtIn.constraints.getPort(t, d), json.constraints.getPort(t, d), "tile " + t + " " + d);
        assertEquals(3.0, json.weights[0]);
        assertEquals(0.3, json.weights[1]);
        assertEquals(1.0, json.weights[5]);
    }

    private TilesetDef loadInline(String body) throws IOException {
        File f = new File(tempDir, "t.json");
        Files.write(f.toPath(), ("{\"name\":\"T\",\"basename\":\"t\",\"tileCount\":3," + body + "}")
                .getBytes(StandardCharsets.UTF_8));
        return TilesetDef.loadFromJson(f);
    }

    @Test
    public void weightsArrayForm() throws IOException {
        TilesetDef def = loadInline("\"weights\":[0.5, 2]");
        assertArrayEquals(new double[]{0.5, 2, 1}, def.weights);
    }

    @Test
    public void weightsSizedForNoEmpty() throws IOException {
        // emptyAllowed=false: tile IDs are 1..tileCount
        TilesetDef def = loadInline("\"emptyAllowed\":false,\"weights\":{\"3\":4}");
        assertEquals(4, def.weights.length);
        assertEquals(4.0, def.weights[3]);
    }

    @Test
    public void noWeightsMeansUniform() throws IOException {
        assertNull(loadInline("\"ports\":[]").weights);
    }

    @Test
    public void badWeightsRejected() {
        assertThrows(IllegalArgumentException.class, () -> loadInline("\"weights\":{\"9\":1}"));
        assertThrows(IllegalArgumentException.class, () -> loadInline("\"weights\":{\"x\":1}"));
        assertThrows(IllegalArgumentException.class, () -> loadInline("\"weights\":{\"1\":-2}"));
    }
}
