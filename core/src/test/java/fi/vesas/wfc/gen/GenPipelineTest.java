package fi.vesas.wfc.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fi.vesas.wfc.Constraints;

public class GenPipelineTest {

    /**
     * Two-pass pipeline: one pass writes a checkerboard biome layer, the WfcPass
     * uses it as a per-cell constraint. Verifies the biome constraint actually
     * flowed through — each output tile ID falls in the set allowed for its biome.
     */
    @Test
    public void wfcPassRespectsConstrainingBiomeLayer() {

        int w = 6;
        int h = 6;
        GenContext ctx = new GenContext(w, h, 1234L);

        // Biome 0 allows tile IDs 0, 1; biome 1 allows tile IDs 2, 3.
        GenPass biomePass = c -> {
            IntLayer biome = c.intLayer("biome");
            for (int x = 0; x < c.getWidth(); x++) {
                for (int y = 0; y < c.getHeight(); y++) {
                    biome.set(x, y, (x + y) % 2);
                }
            }
        };

        WfcPass wfcPass = new WfcPass("terrain", 4, new Constraints())
                .constrainedBy("biome", b -> b == 0 ? new int[]{0, 1} : new int[]{2, 3});

        new GenPipeline().add(biomePass).add(wfcPass).run(ctx);

        IntLayer biome = ctx.intLayer("biome");
        IntLayer terrain = ctx.intLayer("terrain");

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int b = biome.get(x, y);
                int t = terrain.get(x, y);
                if (b == 0) {
                    assertTrue(t == 0 || t == 1,
                            "biome 0 cell should hold tile 0 or 1, got " + t + " at " + x + "," + y);
                } else {
                    assertTrue(t == 2 || t == 3,
                            "biome 1 cell should hold tile 2 or 3, got " + t + " at " + x + "," + y);
                }
            }
        }
    }

    /**
     * Same context seed → identical pipeline output. Critical for save/load
     * flows that replay a world from its seed.
     */
    @Test
    public void sameSeedProducesIdenticalOutput() {

        GenPass biome = c -> {
            IntLayer l = c.intLayer("biome");
            for (int x = 0; x < c.getWidth(); x++) {
                for (int y = 0; y < c.getHeight(); y++) {
                    l.set(x, y, (x * 3 + y) % 2);
                }
            }
        };
        WfcPass wfc = new WfcPass("terrain", 4, new Constraints())
                .constrainedBy("biome", b -> b == 0 ? new int[]{0, 1} : new int[]{2, 3});

        GenContext a = new GenContext(8, 8, 99L);
        new GenPipeline().add(biome).add(wfc).run(a);

        GenContext b = new GenContext(8, 8, 99L);
        new GenPipeline().add(biome).add(wfc).run(b);

        for (int x = 0; x < 8; x++) {
            assertArrayEquals(a.intLayer("terrain").raw()[x], b.intLayer("terrain").raw()[x],
                    "column " + x + " must match");
        }
    }

    /**
     * Cancellation signalled mid-pipeline stops the remaining passes from running.
     */
    @Test
    public void cancellationStopsSubsequentPasses() {

        GenContext ctx = new GenContext(4, 4, 1L);

        GenPipeline pipeline = new GenPipeline()
                .add(c -> c.intLayer("first").fill(7))
                .add(c -> c.cancel())
                .add(c -> c.intLayer("third").fill(99));

        pipeline.run(ctx);

        assertTrue(ctx.hasLayer("first"));
        assertFalse(ctx.hasLayer("third"), "third pass must be skipped after cancellation");
    }

    /**
     * Layer name reused with a different type is a programmer error — we fail fast
     * instead of corrupting state.
     */
    @Test
    public void layerTypeCollisionThrows() {

        GenContext ctx = new GenContext(2, 2, 1L);
        ctx.intLayer("x");

        assertThrows(IllegalStateException.class, () -> ctx.floatLayer("x"));
        assertThrows(IllegalStateException.class, () -> ctx.boolLayer("x"));
    }

    /**
     * WfcPass writes rotation bitmasks when asked. Each solved cell's rotation
     * is one of 1, 2, 4, 8 (a single bit) once the solver has collapsed it.
     */
    @Test
    public void wfcPassWritesRotationsWhenRequested() {

        GenContext ctx = new GenContext(4, 4, 42L);

        WfcPass wfc = new WfcPass("terrain", 3, new Constraints())
                .writingRotationsTo("terrainRot");

        new GenPipeline().add(wfc).run(ctx);

        IntLayer rot = ctx.intLayer("terrainRot");
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                int r = rot.get(x, y);
                assertTrue(r == 1 || r == 2 || r == 4 || r == 8,
                        "rotation must be a single bit, got " + r + " at " + x + "," + y);
            }
        }
    }

    /**
     * Default layer accessor creates on first call and returns the same instance on subsequent calls.
     */
    @Test
    public void layerAccessorReturnsSameInstance() {

        GenContext ctx = new GenContext(3, 3, 1L);
        IntLayer a = ctx.intLayer("foo");
        IntLayer b = ctx.intLayer("foo");
        assertEquals(a, b);
    }
}
