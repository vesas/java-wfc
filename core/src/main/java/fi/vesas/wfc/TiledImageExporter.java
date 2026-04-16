package fi.vesas.wfc;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class TiledImageExporter {

    public static BufferedImage export(SimpleWFC wfc, String tileDir, String basename, int tileW, int tileH) throws IOException {

        int gridW = wfc.getWidth();
        int gridH = wfc.getHeight();
        boolean emptyAllowed = wfc.isEmptyAllowed();
        int[][][] grid = wfc.getGrid();
        int[][][] rots = wfc.getRots();

        // Load tile images
        // Count how many tiles exist by checking grid contents
        int maxTile = 0;
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                for (int t : grid[x][y]) {
                    if (t > maxTile) maxTile = t;
                }
            }
        }

        BufferedImage[] tileImages = new BufferedImage[maxTile];
        for (int i = 0; i < maxTile; i++) {
            File f = new File(tileDir, String.format("%s%d.png", basename, i + 1));
            tileImages[i] = ImageIO.read(f);
        }

        BufferedImage output = new BufferedImage(gridW * tileW, gridH * tileH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();

        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {

                int[] possibilities = grid[x][y];
                int[] rotations = rots[x][y];

                for (int i = 0; i < possibilities.length; i++) {

                    int tile = possibilities[i];
                    int rot = rotations[i];

                    if (tile > 0) {
                        for (int j = 0; j < 4; j++) {
                            if ((rot & (1 << j)) > 0) {

                                BufferedImage tileImg = tileImages[tile - 1];

                                // Flip y: LibGDX y-up, PNG y-down
                                int pixelX = x * tileW;
                                int pixelY = (gridH - 1 - y) * tileH;

                                double centerX = pixelX + tileW / 2.0;
                                double centerY = pixelY + tileH / 2.0;

                                // LibGDX does setRotation(-j * 90) which is j*90 CW.
                                // AffineTransform.getQuadrantRotateInstance(-j) rotates CCW by -j*90 = CW by j*90.
                                AffineTransform transform = new AffineTransform();
                                transform.translate(pixelX, pixelY);
                                transform.rotate(-j * Math.PI / 2.0, tileW / 2.0, tileH / 2.0);

                                g.drawImage(tileImg, transform, null);
                            }
                        }
                    }
                }
            }
        }

        g.dispose();
        return output;
    }

    public static void exportToFile(SimpleWFC wfc, String tileDir, String basename, int tileW, int tileH, String outputPath) throws IOException {
        BufferedImage image = export(wfc, tileDir, basename, tileW, tileH);
        ImageIO.write(image, "PNG", new File(outputPath));
    }
}
