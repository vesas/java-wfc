package fi.vesas.wfc;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Rigorous tests for OverlappingWFC — focused on finding first-step
 * contradictions and verifying backtracking recovery.
 */
public class OverlappingDebugTest {

    // ================================================================
    // Helpers
    // ================================================================

    private BufferedImage makeImage(int w, int h, int[][] pixels) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, pixels[y][x]);
        return img;
    }

    private int[][][] getPropagator(OverlappingWFC wfc) throws Exception {
        Field f = OverlappingWFC.class.getDeclaredField("propagator");
        f.setAccessible(true);
        return (int[][][]) f.get(wfc);
    }

    private int getPatternCountReflect(OverlappingWFC wfc) throws Exception {
        Field f = OverlappingWFC.class.getDeclaredField("patternCount");
        f.setAccessible(true);
        return (int) f.get(wfc);
    }

    private boolean[][] getWave(OverlappingWFC wfc) throws Exception {
        Field f = OverlappingWFC.class.getDeclaredField("wave");
        f.setAccessible(true);
        return (boolean[][]) f.get(wfc);
    }

    private int[] getPossibleCount(OverlappingWFC wfc) throws Exception {
        Field f = OverlappingWFC.class.getDeclaredField("possibleCount");
        f.setAccessible(true);
        return (int[]) f.get(wfc);
    }

    // ================================================================
    // Test: propagator bidirectionality invariant
    // ================================================================

    @Test
    public void testPropagatorBidirectional() throws Exception {
        // For every (p1, p2, dir): p2 in propagator[dir][p1] iff p1 in propagator[oppDir][p2]
        BufferedImage sample = makeGradient(16, 16);
        OverlappingWFC wfc = new OverlappingWFC(sample, 3, 8, 8, 4);
        int[][][] prop = getPropagator(wfc);
        int pc = getPatternCountReflect(wfc);

        int[] oppDirs = {2, 3, 0, 1}; // N->S, E->W, S->N, W->E

        for (int dir = 0; dir < 4; dir++) {
            int opp = oppDirs[dir];
            for (int p1 = 0; p1 < pc; p1++) {
                for (int p2 : prop[dir][p1]) {
                    boolean found = false;
                    for (int q : prop[opp][p2]) {
                        if (q == p1) { found = true; break; }
                    }
                    assertTrue(found,
                        "Bidirectionality violated: p2=" + p2 + " in prop[" + dir + "][" + p1
                        + "] but p1 not in prop[" + opp + "][" + p2 + "]");
                }
            }
        }
    }

    // ================================================================
    // Test: every pattern has at least one compatible neighbor in each dir
    // ================================================================

    @Test
    public void testNoPropagatorDeadEnds() throws Exception {
        // If a pattern has 0 compatible neighbors in some direction, it can NEVER
        // be placed at an interior cell — it will always cause a contradiction.
        // This is a sign of a broken propagator or a sample that's too small.
        BufferedImage sample = makeGradient(16, 16);
        for (int sym : new int[]{1, 2, 4, 8}) {
            OverlappingWFC wfc = new OverlappingWFC(sample, 3, 8, 8, sym);
            int[][][] prop = getPropagator(wfc);
            int pc = getPatternCountReflect(wfc);

            for (int dir = 0; dir < 4; dir++) {
                for (int p = 0; p < pc; p++) {
                    assertTrue(prop[dir][p].length > 0,
                        "Pattern " + p + " has no neighbors in dir " + dir + " (sym=" + sym + ")");
                }
            }
        }
    }

    // ================================================================
    // Test: wave integrity after each step (no cell drops to 0 silently)
    // ================================================================

    @Test
    public void testWaveIntegrityDuringStepByStep() throws Exception {
        BufferedImage sample = makeGradient(16, 16);
        OverlappingWFC wfc = new OverlappingWFC(sample, 3, 16, 16, 4);
        wfc.setSeed(42);

        for (int step = 0; step < 300; step++) {
            wfc.runOneRound();
            if (wfc.isFinished() || wfc.isContradiction()) break;

            int[] pc = getPossibleCount(wfc);
            boolean[][] wave = getWave(wfc);
            for (int cell = 0; cell < pc.length; cell++) {
                assertTrue(pc[cell] > 0,
                    "Cell " + cell + " has 0 possibilities after step " + step
                    + " without contradiction flag");

                // Verify possibleCount matches wave
                int actual = 0;
                for (boolean b : wave[cell]) if (b) actual++;
                assertEquals(pc[cell], actual,
                    "possibleCount mismatch at cell " + cell + " step " + step);
            }
        }
    }

    // ================================================================
    // Test: step-by-step matches run-to-completion
    // ================================================================

    @Test
    public void testStepByStepMatchesFullRun() {
        BufferedImage sample = makeGradient(16, 16);

        // Run to completion
        OverlappingWFC wfc1 = new OverlappingWFC(sample, 3, 12, 12, 4);
        wfc1.setSeed(100);
        wfc1.run();

        // Step-by-step with same seed
        OverlappingWFC wfc2 = new OverlappingWFC(sample, 3, 12, 12, 4);
        wfc2.setSeed(100);
        int steps = 0;
        while (!wfc2.isFinished() && !wfc2.isContradiction() && steps < 2000) {
            wfc2.runOneRound();
            steps++;
        }

        assertEquals(wfc1.isFinished(), wfc2.isFinished(),
            "Finished state should match");

        if (wfc1.isFinished() && wfc2.isFinished()) {
            BufferedImage out1 = wfc1.getOutputImage();
            BufferedImage out2 = wfc2.getOutputImage();
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    assertEquals(out1.getRGB(x, y), out2.getRGB(x, y),
                        "Output mismatch at (" + x + "," + y + ")");
                }
            }
        }
    }

    // ================================================================
    // Test: many seeds, various sample types, step-by-step
    // Must never silently corrupt state — either finish or flag contradiction
    // ================================================================

    @Test
    public void testManySeedsStepByStep_gradient() {
        runManySeedsStepByStep(makeGradient(16, 16), "gradient", 3, 16, 16, 4, 50);
    }

    @Test
    public void testManySeedsStepByStep_checker() {
        runManySeedsStepByStep(makeChecker(16), "checker", 3, 16, 16, 8, 50);
    }

    @Test
    public void testManySeedsStepByStep_noise() {
        runManySeedsStepByStep(makeNoise(16, 16, 3, 42), "noise3col", 2, 12, 12, 1, 50);
    }

    @Test
    public void testManySeedsStepByStep_noiseHighColor() {
        runManySeedsStepByStep(makeNoise(16, 16, 8, 99), "noise8col", 2, 12, 12, 1, 50);
    }

    @Test
    public void testManySeedsStepByStep_sparse() {
        // Sparse image with mostly one color and rare features — likely to create
        // dead-end patterns that force contradictions
        runManySeedsStepByStep(makeSparse(16, 16), "sparse", 3, 16, 16, 1, 50);
    }

    @Test
    public void testManySeedsStepByStep_tiling() {
        // With tiling enabled — edge constraints are tighter
        BufferedImage sample = makeGradient(16, 16);
        int finished = 0, contradictions = 0, totalBacktracks = 0;

        for (int seed = 0; seed < 50; seed++) {
            OverlappingWFC wfc = new OverlappingWFC(sample, 3, 16, 16, 4);
            wfc.setSeed(seed);
            wfc.setTilingHorizontal(true);
            wfc.setTilingVertical(true);

            int steps = 0;
            while (!wfc.isFinished() && !wfc.isContradiction() && steps < 5000) {
                wfc.runOneRound();
                steps++;
            }

            if (wfc.isFinished()) finished++;
            if (wfc.isContradiction()) contradictions++;
            totalBacktracks += wfc.getBacktrackCount();
        }

        System.out.println("tiling: " + finished + "/50 finished, "
            + contradictions + " contradictions, " + totalBacktracks + " backtracks");
        assertTrue(finished > 0, "At least some seeds should finish with tiling");
    }

    @Test
    public void testManySeedsStepByStep_smallSample() {
        // Very small sample — extreme constraint pressure
        runManySeedsStepByStep(makeNoise(4, 4, 4, 7), "small4x4", 2, 8, 8, 1, 50);
    }

    @Test
    public void testManySeedsStepByStep_N2() {
        runManySeedsStepByStep(makeNoise(16, 16, 5, 77), "n2noise", 2, 20, 20, 1, 50);
    }

    private void runManySeedsStepByStep(BufferedImage sample, String name,
            int N, int outW, int outH, int symmetry, int numSeeds) {
        int finished = 0, contradictions = 0, totalBacktracks = 0;

        for (int seed = 0; seed < numSeeds; seed++) {
            OverlappingWFC wfc = new OverlappingWFC(sample, N, outW, outH, symmetry);
            wfc.setSeed(seed);

            int steps = 0;
            while (!wfc.isFinished() && !wfc.isContradiction() && steps < 5000) {
                wfc.runOneRound();
                steps++;
            }

            if (wfc.isFinished()) finished++;
            if (wfc.isContradiction()) contradictions++;
            totalBacktracks += wfc.getBacktrackCount();
        }

        System.out.println(name + ": " + finished + "/" + numSeeds + " finished, "
            + contradictions + " contradictions, " + totalBacktracks + " backtracks");
        assertTrue(finished > 0,
            name + ": expected at least 1/" + numSeeds + " seeds to finish");
    }

    // ================================================================
    // Test: tile-sliced mode
    // ================================================================

    @Test
    public void testTileSlicedStepByStep() {
        // 4x4 grid of 8x8 tiles, 4 unique tile types in a checker layout
        int T = 8;
        BufferedImage sample = new BufferedImage(4 * T, 4 * T, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00};
        int[] layout = {0,1,0,1, 2,3,2,3, 0,1,0,1, 2,3,2,3};
        for (int ty = 0; ty < 4; ty++)
            for (int tx = 0; tx < 4; tx++)
                for (int dy = 0; dy < T; dy++)
                    for (int dx = 0; dx < T; dx++)
                        sample.setRGB(tx*T+dx, ty*T+dy, colors[layout[ty*4+tx]]);

        OverlappingWFC wfc = new OverlappingWFC(sample, 2, 8, 8, 1, T, T);
        wfc.setSeed(42);

        int steps = 0;
        while (!wfc.isFinished() && !wfc.isContradiction() && steps < 500) {
            wfc.runOneRound();
            steps++;
        }

        assertTrue(wfc.isFinished(), "Tile-sliced should finish");
        assertFalse(wfc.isContradiction());

        BufferedImage out = wfc.getOutputImage();
        assertEquals(8 * T, out.getWidth());
        assertEquals(8 * T, out.getHeight());
    }

    // ================================================================
    // Test: immediate re-run after contradiction clears state properly
    // ================================================================

    @Test
    public void testRunAfterContradictionResets() {
        // Use a very constrained setup that often contradicts
        BufferedImage sample = makeNoise(4, 4, 6, 1);
        OverlappingWFC wfc = new OverlappingWFC(sample, 3, 20, 20, 1);
        wfc.setSeed(0);

        wfc.run(20); // With retries

        // Whether it finished or not, the state should be consistent
        if (!wfc.isFinished()) {
            assertTrue(wfc.isContradiction(),
                "If not finished, must be in contradiction state");
        }
    }

    // ================================================================
    // Sample image generators
    // ================================================================

    private BufferedImage makeGradient(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int r = (x * 255 / (w - 1)) & 0xFF;
                int g = (y * 255 / (h - 1)) & 0xFF;
                int b = ((x + y) * 128 / (w + h - 2)) & 0xFF;
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        return img;
    }

    private BufferedImage makeChecker(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                img.setRGB(x, y, ((x / 2 + y / 2) % 2 == 0) ? 0xFF4488CC : 0xFFFFDD44);
        return img;
    }

    private BufferedImage makeNoise(int w, int h, int numColors, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Random rng = new java.util.Random(seed);
        int[] palette = new int[numColors];
        for (int i = 0; i < numColors; i++) {
            palette[i] = 0xFF000000 | rng.nextInt(0xFFFFFF);
        }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, palette[rng.nextInt(numColors)]);
        return img;
    }

    private BufferedImage makeSparse(int w, int h) {
        // Mostly white with a few colored pixels — creates patterns that only
        // appear in specific contexts, making contradictions more likely
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int bg = 0xFFFFFFFF;
        int fg1 = 0xFFFF0000;
        int fg2 = 0xFF0000FF;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, bg);

        // Place a few features
        img.setRGB(3, 3, fg1); img.setRGB(4, 3, fg1);
        img.setRGB(3, 4, fg2);
        img.setRGB(10, 7, fg1);
        img.setRGB(10, 8, fg2); img.setRGB(11, 8, fg2);
        img.setRGB(5, 12, fg1); img.setRGB(6, 12, fg1); img.setRGB(5, 13, fg1);
        return img;
    }
}
