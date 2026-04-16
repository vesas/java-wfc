package fi.vesas.wfc;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import fi.vesas.wfc.SimpleWFC.DIR;

public class WfcCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String mode = args[0];
        switch (mode) {
            case "overlapping":
                runOverlapping(args);
                break;
            case "tiled":
                runTiled(args);
                break;
            default:
                System.err.println("Unknown mode: " + mode);
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println();
        System.out.println("  Overlapping model (generate texture from sample image):");
        System.out.println("    overlapping <sample.png> [options]");
        System.out.println();
        System.out.println("    Options:");
        System.out.println("      --size WxH        Output size in pixels (default: 48x48)");
        System.out.println("      --pattern N        Pattern size, 2 or 3 (default: 3)");
        System.out.println("      --symmetry S       Symmetry: 1, 2, 4, or 8 (default: 8)");
        System.out.println("      --inputtile WxH    Input tile size in pixels (default: 1x1 = pixel mode)");
        System.out.println("      --seed S           Random seed (default: random)");
        System.out.println("      --count N          Number of images to generate (default: 1)");
        System.out.println("      --tiling           Enable seamless tiling");
        System.out.println("      --output PATH      Output file or directory (default: output.png)");
        System.out.println();
        System.out.println("  Tiled model (built-in circuit tileset):");
        System.out.println("    tiled <tile_dir> [options]");
        System.out.println();
        System.out.println("    Options:");
        System.out.println("      --size WxH         Grid size in tiles (default: 28x20)");
        System.out.println("      --tilesize WxH     Tile size in pixels (default: 32x32)");
        System.out.println("      --basename NAME    Tile file prefix (default: circ)");
        System.out.println("      --seed S           Random seed (default: random)");
        System.out.println("      --count N          Number of images to generate (default: 1)");
        System.out.println("      --tiling           Enable seamless tiling");
        System.out.println("      --output PATH      Output file or directory (default: output.png)");
        System.out.println();
        System.out.println("  Examples:");
        System.out.println("    overlapping sample.png --size 64x64 --tiling --output result.png");
        System.out.println("    overlapping sample.png --count 5 --seed 42 --output batch/");
        System.out.println("    tiled assets --basename circ --tiling --seed 229 --output circuit.png");
    }

    // --- Overlapping mode ---

    private static void runOverlapping(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: sample image path required");
            printUsage();
            return;
        }

        String samplePath = args[1];
        int outW = 48, outH = 48;
        int patternSize = 3;
        int symmetry = 8;
        int inTileW = 1, inTileH = 1;
        long seed = System.nanoTime();
        int count = 1;
        boolean tiling = false;
        String output = "output.png";

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--size":
                    int[] wh = parseSize(args[++i]);
                    outW = wh[0]; outH = wh[1];
                    break;
                case "--pattern":
                    patternSize = Integer.parseInt(args[++i]);
                    break;
                case "--symmetry":
                    symmetry = Integer.parseInt(args[++i]);
                    break;
                case "--inputtile":
                    int[] ts = parseSize(args[++i]);
                    inTileW = ts[0]; inTileH = ts[1];
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--count":
                    count = Integer.parseInt(args[++i]);
                    break;
                case "--tiling":
                    tiling = true;
                    break;
                case "--output":
                    output = args[++i];
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    return;
            }
        }

        try {
            BufferedImage sample = ImageIO.read(new File(samplePath));
            if (sample == null) {
                System.err.println("Error: could not read image: " + samplePath);
                return;
            }

            System.out.println("Sample: " + samplePath + " (" + sample.getWidth() + "x" + sample.getHeight() + ")");
            System.out.println("Output: " + outW + "x" + outH + ", pattern=" + patternSize
                + ", symmetry=" + symmetry + ", tiling=" + tiling);

            if (count == 1) {
                generateOverlappingSingle(sample, patternSize, outW, outH, symmetry, inTileW, inTileH, tiling, seed, output);
            } else {
                long[] seeds = BatchRunner.generateSeeds(seed, count);
                String dir = output.endsWith(".png") ? output.replace(".png", "") : output;
                BatchRunner.runOverlappingBatch(sample, patternSize, outW, outH, symmetry,
                    tiling, tiling, dir, "output", seeds);
                System.out.println("Generated " + count + " images in " + dir + "/");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateOverlappingSingle(BufferedImage sample, int N, int outW, int outH,
            int symmetry, int inTileW, int inTileH, boolean tiling, long seed, String output) throws Exception {

        OverlappingWFC wfc = new OverlappingWFC(sample, N, outW, outH, symmetry, inTileW, inTileH);
        wfc.setSeed(seed);
        wfc.setTilingHorizontal(tiling);
        wfc.setTilingVertical(tiling);

        System.out.println("Patterns extracted: " + wfc.getPatternCount());
        System.out.print("Running... ");

        wfc.run();

        if (wfc.isContradiction()) {
            System.out.println("CONTRADICTION (try a different seed or larger pattern)");
            return;
        }

        System.out.println("done (backtracked " + wfc.getBacktrackCount() + " times)");

        wfc.saveToFile(output);
        System.out.println("Saved: " + output);
    }

    // --- Tiled mode ---

    private static void runTiled(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: tile directory required");
            printUsage();
            return;
        }

        String tileDir = args[1];
        int gridW = 28, gridH = 20;
        int tileW = 32, tileH = 32;
        String basename = "circ";
        long seed = System.nanoTime();
        int count = 1;
        boolean tiling = false;
        String output = "output.png";

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--size":
                    int[] wh = parseSize(args[++i]);
                    gridW = wh[0]; gridH = wh[1];
                    break;
                case "--tilesize":
                    int[] ts = parseSize(args[++i]);
                    tileW = ts[0]; tileH = ts[1];
                    break;
                case "--basename":
                    basename = args[++i];
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--count":
                    count = Integer.parseInt(args[++i]);
                    break;
                case "--tiling":
                    tiling = true;
                    break;
                case "--output":
                    output = args[++i];
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    return;
            }
        }

        try {
            // Build circuit constraints (the built-in tileset)
            Constraints constraints = buildCircuitConstraints();
            int tileCount = 9;

            System.out.println("Tiles: " + tileDir + "/" + basename + "*.png");
            System.out.println("Grid: " + gridW + "x" + gridH + ", tile: " + tileW + "x" + tileH
                + ", tiling=" + tiling);

            if (count == 1) {
                generateTiledSingle(gridW, gridH, tileCount, constraints, tiling,
                    seed, tileDir, basename, tileW, tileH, output);
            } else {
                long[] seeds = BatchRunner.generateSeeds(seed, count);
                String dir = output.endsWith(".png") ? output.replace(".png", "") : output;
                BatchRunner.runTiledBatch(gridW, gridH, tileCount, true, true,
                    tiling, tiling, constraints, tileDir, basename, tileW, tileH,
                    dir, "output", seeds);
                System.out.println("Generated " + count + " images in " + dir + "/");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateTiledSingle(int gridW, int gridH, int tileCount,
            Constraints constraints, boolean tiling, long seed,
            String tileDir, String basename, int tileW, int tileH,
            String output) throws Exception {

        SimpleWFC wfc = new SimpleWFC(gridW, gridH, tileCount, true, true);
        wfc.setSeed((int) seed);
        wfc.setTilingHorizontal(tiling);
        wfc.setTilingVertical(tiling);
        wfc.setConstraints(constraints);

        System.out.print("Running... ");
        wfc.run();

        if (wfc.isContradiction()) {
            System.out.println("CONTRADICTION (try a different seed)");
            return;
        }

        System.out.println("done (backtracked " + wfc.getBacktrackCount() + " times)");

        TiledImageExporter.exportToFile(wfc, tileDir, basename, tileW, tileH, output);
        System.out.println("Saved: " + output + " (" + (gridW * tileW) + "x" + (gridH * tileH) + " pixels)");
    }

    private static Constraints buildCircuitConstraints() {
        Constraints constraints = new Constraints();

        constraints.addPort(1, DIR.S, 1);
        constraints.addPort(1, DIR.E, 1);
        constraints.addPort(1, DIR.N, 1);
        constraints.addPort(1, DIR.W, 1);

        constraints.addPort(2, DIR.S, 1);
        constraints.addPort(2, DIR.N, 2);

        constraints.addPort(3, DIR.N, 2);

        constraints.addPort(4, DIR.N, 2);
        constraints.addPort(4, DIR.W, 2);

        constraints.addPort(5, DIR.S, 1);

        constraints.addPort(6, DIR.N, 2);
        constraints.addPort(6, DIR.W, 2);
        constraints.addPort(6, DIR.E, 2);

        constraints.addPort(7, DIR.N, 2);
        constraints.addPort(7, DIR.S, 2);

        constraints.addPort(8, DIR.N, 2);
        constraints.addPort(8, DIR.S, 2);

        return constraints;
    }

    private static int[] parseSize(String s) {
        String[] parts = s.split("x");
        return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }
}
