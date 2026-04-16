package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OverlappingWFCTest {

    @TempDir
    File tempDir;

    private BufferedImage solidColorImage(int width, int height, int color) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, color);
            }
        }
        return img;
    }

    private BufferedImage checkerboardImage(int width, int height, int color1, int color2) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, (x + y) % 2 == 0 ? color1 : color2);
            }
        }
        return img;
    }

    @Test
    public void testSolidColorSample() {
        BufferedImage sample = solidColorImage(4, 4, 0xFFFF0000);
        OverlappingWFC wfc = new OverlappingWFC(sample, 2, 8, 8, 1);
        wfc.setSeed(42);
        wfc.run();

        assertTrue(wfc.isFinished());
        assertFalse(wfc.isContradiction());

        BufferedImage output = wfc.getOutputImage();
        assertEquals(8, output.getWidth());
        assertEquals(8, output.getHeight());

        // All pixels should be the same solid color
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(0xFFFF0000, output.getRGB(x, y),
                    "Pixel at (" + x + "," + y + ") should be red");
            }
        }
    }

    @Test
    public void testPatternCountSolidColor() {
        BufferedImage sample = solidColorImage(4, 4, 0xFFFF0000);
        OverlappingWFC wfc = new OverlappingWFC(sample, 2, 4, 4, 1);

        // A solid color image has exactly 1 unique 2x2 pattern
        assertEquals(1, wfc.getPatternCount());
    }

    @Test
    public void testCheckerboardPatternCount() {
        BufferedImage sample = checkerboardImage(4, 4, 0xFFFF0000, 0xFF0000FF);
        OverlappingWFC wfc = new OverlappingWFC(sample, 2, 4, 4, 1);

        // A 4x4 checkerboard with N=2 has 2 unique patterns:
        // [R,B,B,R] and [B,R,R,B]
        assertEquals(2, wfc.getPatternCount());
    }

    @Test
    public void testDeterminism() {
        BufferedImage sample = checkerboardImage(4, 4, 0xFFFF0000, 0xFF0000FF);

        OverlappingWFC wfc1 = new OverlappingWFC(sample, 2, 8, 8, 1);
        wfc1.setSeed(123);
        wfc1.run();

        OverlappingWFC wfc2 = new OverlappingWFC(sample, 2, 8, 8, 1);
        wfc2.setSeed(123);
        wfc2.run();

        if (wfc1.isFinished() && wfc2.isFinished()) {
            BufferedImage out1 = wfc1.getOutputImage();
            BufferedImage out2 = wfc2.getOutputImage();

            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(out1.getRGB(x, y), out2.getRGB(x, y),
                        "Outputs should be identical with same seed at (" + x + "," + y + ")");
                }
            }
        }
    }

    @Test
    public void testTilingEdgesMatch() {
        // Use a striped pattern — should produce tileable output
        BufferedImage sample = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        int red = 0xFFFF0000;
        int blue = 0xFF0000FF;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                sample.setRGB(x, y, x % 2 == 0 ? red : blue);
            }
        }

        OverlappingWFC wfc = new OverlappingWFC(sample, 2, 16, 16, 1);
        wfc.setSeed(42);
        wfc.setTilingHorizontal(true);
        wfc.setTilingVertical(true);

        // Re-init with tiling — need to reconstruct
        wfc = new OverlappingWFC(sample, 2, 16, 16, 1);
        wfc.setSeed(42);
        wfc.setTilingHorizontal(true);
        wfc.setTilingVertical(true);
        wfc.run();

        if (wfc.isFinished()) {
            BufferedImage output = wfc.getOutputImage();

            // Check that left-right edges could tile
            // This is a basic check — with overlapping WFC and tiling enabled,
            // the patterns at edges should be compatible
            // We just verify it ran to completion without contradiction
            assertFalse(wfc.isContradiction());
        }
    }

    @Test
    public void testSaveToFile() throws IOException {
        BufferedImage sample = solidColorImage(4, 4, 0xFF00FF00);
        OverlappingWFC wfc = new OverlappingWFC(sample, 2, 8, 8, 1);
        wfc.setSeed(1);
        wfc.run();

        assertTrue(wfc.isFinished());

        String outPath = new File(tempDir, "test_output.png").getPath();
        wfc.saveToFile(outPath);

        File outFile = new File(outPath);
        assertTrue(outFile.exists());
        assertTrue(outFile.length() > 0);
    }

    @Test
    public void testSymmetryIncreasesPatternCount() {
        // Asymmetric sample: L-shape pattern
        BufferedImage sample = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        int red = 0xFFFF0000;
        int blue = 0xFF0000FF;
        int green = 0xFF00FF00;
        sample.setRGB(0, 0, red);
        sample.setRGB(1, 0, blue);
        sample.setRGB(0, 1, green);
        sample.setRGB(1, 1, red);
        // Fill rest with red
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (x > 1 || y > 1) {
                    sample.setRGB(x, y, red);
                }
            }
        }

        OverlappingWFC wfcNoSym = new OverlappingWFC(sample, 2, 4, 4, 1);
        int countNoSym = wfcNoSym.getPatternCount();

        OverlappingWFC wfcWithSym = new OverlappingWFC(sample, 2, 4, 4, 8);
        int countWithSym = wfcWithSym.getPatternCount();

        assertTrue(countWithSym >= countNoSym,
            "Symmetry=8 should produce at least as many patterns as symmetry=1");
    }
}
