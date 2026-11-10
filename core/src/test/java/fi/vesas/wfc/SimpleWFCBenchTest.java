package fi.vesas.wfc;

import org.junit.jupiter.api.Test;

/**
 * Performance benchmark for SimpleWFC (tiled mode). Prints timing and a checksum
 * of the generated grids so output can be compared across optimizations.
 */
public class SimpleWFCBenchTest {

    private long runOnce(TilesetDef ts, int w, int h, int seed, boolean tile) {
        SimpleWFC wfc = new SimpleWFC(w, h, ts.tileCount, ts.emptyAllowed, ts.rotationsAllowed);
        wfc.setSeed(seed);
        wfc.setTilingHorizontal(tile);
        wfc.setTilingVertical(tile);
        wfc.setConstraints(ts.constraints);
        // Step like the GUI does
        while (!wfc.isFinished() && !wfc.isContradiction()) wfc.runOneRound();
        long sum = wfc.getBacktrackCount();
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) {
                int[] g = wfc.getGrid(x, y), r = wfc.getRots(x, y);
                for (int i = 0; i < g.length; i++) sum = sum * 31 + g[i] * 17 + r[i];
            }
        return sum;
    }

    @Test
    public void benchTilesets() {
        int[][] sizes = { {28, 20}, {40, 40}, {80, 80} };
        for (TilesetDef ts : TilesetDef.allTilesets()) {
            for (int[] s : sizes) {
                for (int i = 0; i < 2; i++) runOnce(ts, s[0], s[1], i, true); // warmup
                int runs = 5;
                long checksum = 0;
                long t0 = System.nanoTime();
                for (int seed = 0; seed < runs; seed++) {
                    checksum = checksum * 7 + runOnce(ts, s[0], s[1], seed + 100, seed % 2 == 0);
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.println("BENCH " + ts.name + " " + s[0] + "x" + s[1]
                        + ": avg " + (ms / runs) + " ms/run, checksum " + checksum);
            }
        }
    }
}
