package fi.vesas.wfc.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import fi.vesas.wfc.Constraints;

public class PassesTest {

    // ---------- PerlinNoisePass ----------

    @Test
    public void perlinProducesValuesInApproximateRange() {

        GenContext ctx = new GenContext(32, 32, 1L);
        new PerlinNoisePass("n", 0.1f).apply(ctx);

        FloatLayer n = ctx.floatLayer("n");
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                float v = n.get(x, y);
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }

        // Perlin is bounded by ~[-0.71, 0.71] in 2D, generous slack:
        assertTrue(min >= -1.5f && max <= 1.5f, "out of range: " + min + ".." + max);
        // And not degenerate (not all zeros)
        assertTrue(max - min > 0.1f, "no variation: range " + (max - min));
    }

    @Test
    public void perlinIsDeterministicForSameSeed() {

        GenContext a = new GenContext(16, 16, 42L);
        GenContext b = new GenContext(16, 16, 42L);
        new PerlinNoisePass("n", 0.2f).apply(a);
        new PerlinNoisePass("n", 0.2f).apply(b);

        for (int x = 0; x < 16; x++) {
            assertArrayEquals(a.floatLayer("n").raw()[x], b.floatLayer("n").raw()[x]);
        }
    }

    // ---------- ThresholdPass ----------

    @Test
    public void thresholdMarksCellsAtOrAboveCutoff() {

        GenContext ctx = new GenContext(3, 3, 1L);
        FloatLayer src = ctx.floatLayer("f");
        src.set(0, 0, -0.5f);
        src.set(1, 1, 0.3f);
        src.set(2, 2, 0.7f);

        new ThresholdPass("f", "b", 0.3f).apply(ctx);

        BoolLayer dst = ctx.boolLayer("b");
        assertFalse(dst.get(0, 0));
        assertTrue(dst.get(1, 1));
        assertTrue(dst.get(2, 2));
    }

    // ---------- CellularAutomataPass ----------

    @Test
    public void cellularAutomataPreservesAllLivePatternWithTypicalThreshold() {

        GenContext ctx = new GenContext(5, 5, 1L);
        BoolLayer layer = ctx.boolLayer("cave");
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                layer.set(x, y, true);
            }
        }

        new CellularAutomataPass("cave", 3, 5).apply(ctx);

        // With OOB counted as live, interior cells have 8 live neighbors, edges 8 too.
        // All cells stay alive under threshold 5.
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                assertTrue(layer.get(x, y), "cell " + x + "," + y + " died");
            }
        }
    }

    @Test
    public void cellularAutomataSmoothsIsolatedPoint() {

        GenContext ctx = new GenContext(5, 5, 1L);
        BoolLayer layer = ctx.boolLayer("cave");
        // Start with only the center alive, everything else dead
        layer.set(2, 2, true);

        new CellularAutomataPass("cave", 1, 5).apply(ctx);

        // The single live cell has 0 live neighbors (interior); becomes dead under threshold 5.
        // Corner/edge dead cells have high OOB neighbor count; they may become alive.
        // Assertion: the isolated point is gone.
        assertFalse(layer.get(2, 2));
    }

    // ---------- VoronoiRegionsPass ----------

    @Test
    public void voronoiAssignsValidRegionIds() {

        GenContext ctx = new GenContext(16, 16, 123L);
        new VoronoiRegionsPass("region", 4).apply(ctx);

        IntLayer region = ctx.intLayer("region");
        Set<Integer> seen = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int r = region.get(x, y);
                assertTrue(r >= 0 && r < 4, "region id out of range: " + r);
                seen.add(r);
            }
        }
        assertTrue(seen.size() >= 2, "expected multiple regions, got " + seen.size());
    }

    @Test
    public void voronoiIsDeterministicForSameSeed() {

        GenContext a = new GenContext(8, 8, 7L);
        GenContext b = new GenContext(8, 8, 7L);
        new VoronoiRegionsPass("r", 3).apply(a);
        new VoronoiRegionsPass("r", 3).apply(b);

        for (int x = 0; x < 8; x++) {
            assertArrayEquals(a.intLayer("r").raw()[x], b.intLayer("r").raw()[x]);
        }
    }

    // ---------- PinnedTilesPass ----------

    @Test
    public void pinnedTilesPassWritesIdsAndFeedsWfcPass() {

        GenContext ctx = new GenContext(4, 4, 1L);

        PinnedTilesPass pins = new PinnedTilesPass("terrain")
                .pin(1, 1, 2)
                .pin(2, 2, 3);

        WfcPass wfc = new WfcPass("terrain", 4, new Constraints())
                .withAllowedTileSource(pins.asSource());

        new GenPipeline().add(pins).add(wfc).run(ctx);

        IntLayer terrain = ctx.intLayer("terrain");
        assertEquals(2, terrain.get(1, 1));
        assertEquals(3, terrain.get(2, 2));
    }

    // ---------- ReachabilityCheckPass ----------

    @Test
    public void reachabilityPassesOnConnectedMask() {

        GenContext ctx = new GenContext(4, 4, 1L);
        BoolLayer walk = ctx.boolLayer("walk");
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                walk.set(x, y, true);
            }
        }

        new ReachabilityCheckPass("walk", 0, 0).apply(ctx);  // must not throw
    }

    @Test
    public void reachabilityFailsOnDisconnectedMask() {

        GenContext ctx = new GenContext(4, 4, 1L);
        BoolLayer walk = ctx.boolLayer("walk");
        // Two disconnected walkable cells; a wall runs through the middle
        walk.set(0, 0, true);
        walk.set(3, 3, true);

        assertThrows(GenerationFailedException.class,
                () -> new ReachabilityCheckPass("walk", 0, 0).apply(ctx));
    }

    @Test
    public void reachabilityFailsWhenStartIsNotWalkable() {

        GenContext ctx = new GenContext(4, 4, 1L);
        // walk layer all false by default

        assertThrows(GenerationFailedException.class,
                () -> new ReachabilityCheckPass("walk", 1, 1).apply(ctx));
    }
}
