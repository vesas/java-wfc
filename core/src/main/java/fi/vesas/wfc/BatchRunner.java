package fi.vesas.wfc;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class BatchRunner {

    public static void runTiledBatch(
            int gridWidth, int gridHeight, int tileCount,
            boolean emptyAllowed, boolean rotationsAllowed,
            boolean tilingH, boolean tilingV,
            Constraints constraints,
            String tileDir, String basename, int tileW, int tileH,
            String outputDir, String outputPrefix,
            long[] seeds) throws IOException {

        new File(outputDir).mkdirs();

        for (long seed : seeds) {
            SimpleWFC wfc = new SimpleWFC(gridWidth, gridHeight, tileCount, emptyAllowed, rotationsAllowed);
            wfc.setSeed((int) seed);
            wfc.setTilingHorizontal(tilingH);
            wfc.setTilingVertical(tilingV);
            wfc.setConstraints(constraints);

            wfc.run();

            if (wfc.isContradiction()) {
                System.err.println("Warning: contradiction with seed " + seed + ", skipping");
                continue;
            }

            String outputPath = new File(outputDir, String.format("%s_seed_%d.png", outputPrefix, seed)).getPath();
            TiledImageExporter.exportToFile(wfc, tileDir, basename, tileW, tileH, outputPath);
        }
    }

    public static void runOverlappingBatch(
            BufferedImage sample, int N,
            int outputWidth, int outputHeight,
            int symmetry,
            boolean tilingH, boolean tilingV,
            String outputDir, String outputPrefix,
            long[] seeds) throws IOException {

        new File(outputDir).mkdirs();

        for (long seed : seeds) {
            OverlappingWFC wfc = new OverlappingWFC(sample, N, outputWidth, outputHeight, symmetry);
            wfc.setSeed(seed);
            wfc.setTilingHorizontal(tilingH);
            wfc.setTilingVertical(tilingV);

            wfc.run();

            if (wfc.isContradiction()) {
                System.err.println("Warning: contradiction with seed " + seed + ", skipping");
                continue;
            }

            String outputPath = new File(outputDir, String.format("%s_seed_%d.png", outputPrefix, seed)).getPath();
            wfc.saveToFile(outputPath);
        }
    }

    public static long[] generateSeeds(long baseSeed, int count) {
        long[] seeds = new long[count];
        for (int i = 0; i < count; i++) {
            seeds[i] = baseSeed + i;
        }
        return seeds;
    }
}
