package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fi.vesas.wfc.SimpleWFC.DIR;

public class TiledImageExporterTest {

    @TempDir
    File tempDir;

    private void createTileImage(File dir, String basename, int index, int tileW, int tileH, int color) throws IOException {
        BufferedImage img = new BufferedImage(tileW, tileH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < tileH; y++) {
            for (int x = 0; x < tileW; x++) {
                img.setRGB(x, y, color);
            }
        }
        ImageIO.write(img, "PNG", new File(dir, String.format("%s%d.png", basename, index)));
    }

    @Test
    public void testExportDimensions() throws IOException {
        int tileW = 8;
        int tileH = 8;
        int gridW = 3;
        int gridH = 2;

        // Create tile images: tile 1 = red, tile 2 = blue
        createTileImage(tempDir, "t", 1, tileW, tileH, 0xFFFF0000);
        createTileImage(tempDir, "t", 2, tileW, tileH, 0xFF0000FF);

        SimpleWFC wfc = new SimpleWFC(gridW, gridH, 3, true, false);
        Constraints constraints = new Constraints();
        constraints.addPort(1, DIR.N, 1);
        constraints.addPort(1, DIR.S, 1);
        constraints.addPort(1, DIR.E, 1);
        constraints.addPort(1, DIR.W, 1);
        constraints.addPort(2, DIR.N, 1);
        constraints.addPort(2, DIR.S, 1);
        constraints.addPort(2, DIR.E, 1);
        constraints.addPort(2, DIR.W, 1);
        wfc.setConstraints(constraints);
        wfc.setSeed(42);
        wfc.run();

        assertTrue(wfc.isFinished());

        BufferedImage result = TiledImageExporter.export(wfc, tempDir.getPath(), "t", tileW, tileH);

        assertEquals(gridW * tileW, result.getWidth());
        assertEquals(gridH * tileH, result.getHeight());
    }

    @Test
    public void testExportToFile() throws IOException {
        int tileW = 4;
        int tileH = 4;

        createTileImage(tempDir, "s", 1, tileW, tileH, 0xFFFF0000);

        SimpleWFC wfc = new SimpleWFC(2, 2, 2, true, false);
        Constraints constraints = new Constraints();
        constraints.addPort(1, DIR.N, 1);
        constraints.addPort(1, DIR.S, 1);
        constraints.addPort(1, DIR.E, 1);
        constraints.addPort(1, DIR.W, 1);
        wfc.setConstraints(constraints);
        wfc.setSeed(1);
        wfc.run();

        String outPath = new File(tempDir, "output.png").getPath();
        TiledImageExporter.exportToFile(wfc, tempDir.getPath(), "s", tileW, tileH, outPath);

        File outFile = new File(outPath);
        assertTrue(outFile.exists());
        assertTrue(outFile.length() > 0);

        BufferedImage readBack = ImageIO.read(outFile);
        assertEquals(2 * tileW, readBack.getWidth());
        assertEquals(2 * tileH, readBack.getHeight());
    }

    @Test
    public void testEmptyTilesAreTransparent() throws IOException {
        int tileW = 4;
        int tileH = 4;

        createTileImage(tempDir, "e", 1, tileW, tileH, 0xFFFF0000);

        // 1x1 grid with only tile 0 (empty)
        SimpleWFC wfc = new SimpleWFC(1, 1, 2, true, false);
        Constraints constraints = new Constraints();
        wfc.setConstraints(constraints);
        // Manually set the grid to have only tile 0
        wfc.setValuesAt(0, 0, 0);
        wfc.setRotationsAt(0, 0, 1);

        BufferedImage result = TiledImageExporter.export(wfc, tempDir.getPath(), "e", tileW, tileH);

        // All pixels should be transparent (alpha = 0)
        int pixel = result.getRGB(0, 0);
        int alpha = (pixel >> 24) & 0xFF;
        assertEquals(0, alpha);
    }
}
