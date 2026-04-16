package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BatchRunnerTest {

    @TempDir
    File tempDir;

    @Test
    public void testGenerateSeeds() {
        long[] seeds = BatchRunner.generateSeeds(100, 5);
        assertEquals(5, seeds.length);
        assertEquals(100, seeds[0]);
        assertEquals(104, seeds[4]);
    }

    @Test
    public void testOverlappingBatch() throws IOException {
        BufferedImage sample = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        int color = 0xFFFF0000;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                sample.setRGB(x, y, color);
            }
        }

        String outputDir = new File(tempDir, "batch_out").getPath();
        long[] seeds = BatchRunner.generateSeeds(1, 3);

        BatchRunner.runOverlappingBatch(
            sample, 2, 8, 8, 1,
            false, false,
            outputDir, "test", seeds
        );

        // Verify 3 output files were created
        File outDir = new File(outputDir);
        assertTrue(outDir.exists());

        for (long seed : seeds) {
            File f = new File(outDir, String.format("test_seed_%d.png", seed));
            assertTrue(f.exists(), "Expected output file for seed " + seed);
            assertTrue(f.length() > 0);
        }
    }

    @Test
    public void testTiledBatchWithTileImages() throws IOException {
        int tileW = 4;
        int tileH = 4;

        // Create a tile image directory
        File tileDir = new File(tempDir, "tiles");
        tileDir.mkdirs();

        BufferedImage tileImg = new BufferedImage(tileW, tileH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < tileH; y++) {
            for (int x = 0; x < tileW; x++) {
                tileImg.setRGB(x, y, 0xFF00FF00);
            }
        }
        ImageIO.write(tileImg, "PNG", new File(tileDir, "b1.png"));

        Constraints constraints = new Constraints();
        constraints.addPort(1, SimpleWFC.DIR.N, 1);
        constraints.addPort(1, SimpleWFC.DIR.S, 1);
        constraints.addPort(1, SimpleWFC.DIR.E, 1);
        constraints.addPort(1, SimpleWFC.DIR.W, 1);

        String outputDir = new File(tempDir, "tiled_out").getPath();
        long[] seeds = BatchRunner.generateSeeds(10, 3);

        BatchRunner.runTiledBatch(
            3, 3, 2, true, false,
            false, false,
            constraints,
            tileDir.getPath(), "b", tileW, tileH,
            outputDir, "tiled", seeds
        );

        File outDir = new File(outputDir);
        assertTrue(outDir.exists());

        for (long seed : seeds) {
            File f = new File(outDir, String.format("tiled_seed_%d.png", seed));
            assertTrue(f.exists(), "Expected output file for seed " + seed);
        }
    }
}
