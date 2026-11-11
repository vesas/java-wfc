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

    /**
     * Tiled batch driven by a tileset definition (constraints, weights, images).
     * Format is png, json, tmx or tmj. Returns how many files were written;
     * seeds that contradict are skipped.
     */
    public static int runTiledBatch(TilesetDef def, int gridWidth, int gridHeight,
            boolean tilingH, boolean tilingV,
            String outputDir, String outputPrefix, String format,
            long[] seeds) throws IOException {

        new File(outputDir).mkdirs();
        int written = 0;

        for (long seed : seeds) {
            SimpleWFC wfc = def.createSolver(gridWidth, gridHeight);
            wfc.setSeed((int) seed);
            wfc.setTilingHorizontal(tilingH);
            wfc.setTilingVertical(tilingV);

            wfc.run();

            if (wfc.isContradiction()) {
                System.err.println("Warning: contradiction with seed " + seed + ", skipping");
                continue;
            }

            String outputPath = new File(outputDir,
                    String.format("%s_seed_%d.%s", outputPrefix, seed, format)).getPath();
            writeTiled(wfc, def, format, outputPath);
            written++;
        }
        return written;
    }

    /** Write a solved tiled grid as png (image) or json / tmx / tmj (map data). */
    public static void writeTiled(SimpleWFC wfc, TilesetDef def, String format, String outputPath) throws IOException {
        if (format.equals("png")) {
            String tileDir = def.tileDir != null ? def.tileDir : ".";
            TiledImageExporter.exportToFile(wfc, tileDir, def.basename, def.tilePixelW, def.tilePixelH, outputPath);
        } else {
            String path = outputPath;
            if (!path.toLowerCase().endsWith("." + format)) {
                throw new IllegalArgumentException("Output " + outputPath + " does not match format " + format);
            }
            TileMapExporter.exportToFile(wfc, def, path);
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
