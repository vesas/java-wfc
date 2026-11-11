package fi.vesas.wfc;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

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
        System.out.println("  Tiled model:");
        System.out.println("    tiled <tileset.json> [options]   tileset definition (ports, weights, images)");
        System.out.println("    tiled <tile_dir> [options]       built-in circuit rules, images from tile_dir");
        System.out.println();
        System.out.println("    Options:");
        System.out.println("      --size WxH         Grid size in tiles (default: 28x20)");
        System.out.println("      --tilesize WxH     Tile size in pixels (default: from tileset, 32x32)");
        System.out.println("      --basename NAME    Tile file prefix (default: from tileset, circ)");
        System.out.println("      --seed S           Random seed (default: random)");
        System.out.println("      --count N          Number of outputs to generate (default: 1)");
        System.out.println("      --tiling           Enable seamless tiling");
        System.out.println("      --output PATH      Output file or directory (default: output.png)");
        System.out.println("      --format F         png, json, tmx or tmj (default: from --output extension)");
        System.out.println("                         tmx/tmj = Tiled map editor; json = plain tile grid");
        System.out.println();
        System.out.println("  Examples:");
        System.out.println("    overlapping sample.png --size 64x64 --tiling --output result.png");
        System.out.println("    overlapping sample.png --count 5 --seed 42 --output batch/");
        System.out.println("    tiled assets --basename circ --tiling --seed 229 --output circuit.png");
        System.out.println("    tiled assets/circuit.json --seed 229 --output maps/circuit.tmx");
        System.out.println("    tiled assets/circuit.json --count 10 --format tmj --output maps/");
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
            System.err.println("Error: tile directory or tileset JSON required");
            printUsage();
            return;
        }

        String source = args[1];
        int gridW = 28, gridH = 20;
        int[] tileSizeOverride = null;
        String basenameOverride = null;
        long seed = System.nanoTime();
        int count = 1;
        boolean tiling = false;
        String output = "output.png";
        String format = null;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--size":
                    int[] wh = parseSize(args[++i]);
                    gridW = wh[0]; gridH = wh[1];
                    break;
                case "--tilesize":
                    tileSizeOverride = parseSize(args[++i]);
                    break;
                case "--basename":
                    basenameOverride = args[++i];
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
                case "--format":
                    format = args[++i].toLowerCase();
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    return;
            }
        }

        if (format == null) {
            format = extensionOf(output);
            if (format == null) format = "png";
        }
        if (!format.equals("png") && !format.equals("json") && !format.equals("tmx") && !format.equals("tmj")) {
            System.err.println("Unknown format: " + format + " (use png, json, tmx or tmj)");
            return;
        }
        if (count == 1 && extensionOf(output) != null && !extensionOf(output).equals(format)) {
            System.err.println("Error: --output " + output + " does not match --format " + format);
            return;
        }

        try {
            TilesetDef def = loadTileset(source, basenameOverride, tileSizeOverride);

            System.out.println("Tileset: " + def.name + " (" + def.tileDir + "/" + def.basename + "*.png, "
                + def.tileCount + " tile IDs" + (def.weights != null ? ", weighted" : "") + ")");
            System.out.println("Grid: " + gridW + "x" + gridH + ", tile: " + def.tilePixelW + "x" + def.tilePixelH
                + ", tiling=" + tiling + ", format=" + format);

            if (count == 1) {
                if (extensionOf(output) == null) output = output + "." + format;
                generateTiledSingle(def, gridW, gridH, tiling, seed, format, output);
            } else {
                long[] seeds = BatchRunner.generateSeeds(seed, count);
                String dir = extensionOf(output) != null ? output.substring(0, output.lastIndexOf('.')) : output;
                int written = BatchRunner.runTiledBatch(def, gridW, gridH, tiling, tiling, dir, "output", format, seeds);
                System.out.println("Generated " + written + "/" + count + " files in " + dir + "/");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * A path ending in .json is a tileset definition. Anything else is a tile
     * directory used with the built-in circuit rules (original behaviour).
     */
    static TilesetDef loadTileset(String source, String basenameOverride, int[] tileSizeOverride) {
        TilesetDef base;
        File f = new File(source);
        if (source.toLowerCase().endsWith(".json")) {
            if (!f.isFile()) throw new IllegalArgumentException("Tileset not found: " + source);
            base = TilesetDef.loadFromJson(f);
        } else {
            TilesetDef c = TilesetDef.circuitTileset();
            base = c.withImages(f.getPath(), c.basename, c.tilePixelW, c.tilePixelH);
        }
        if (basenameOverride == null && tileSizeOverride == null) return base;
        return base.withImages(base.tileDir,
                basenameOverride != null ? basenameOverride : base.basename,
                tileSizeOverride != null ? tileSizeOverride[0] : base.tilePixelW,
                tileSizeOverride != null ? tileSizeOverride[1] : base.tilePixelH);
    }

    private static void generateTiledSingle(TilesetDef def, int gridW, int gridH, boolean tiling,
            long seed, String format, String output) throws Exception {

        SimpleWFC wfc = def.createSolver(gridW, gridH);
        wfc.setSeed((int) seed);
        wfc.setTilingHorizontal(tiling);
        wfc.setTilingVertical(tiling);

        System.out.print("Running... ");
        wfc.run();

        if (wfc.isContradiction()) {
            System.out.println("CONTRADICTION (try a different seed)");
            return;
        }

        System.out.println("done (backtracked " + wfc.getBacktrackCount() + " times)");

        BatchRunner.writeTiled(wfc, def, format, output);
        if (format.equals("png")) {
            System.out.println("Saved: " + output + " (" + (gridW * def.tilePixelW) + "x" + (gridH * def.tilePixelH) + " pixels)");
        } else {
            System.out.println("Saved: " + output);
        }
    }

    /** Lower-case extension without the dot, or null when the last path segment has none. */
    private static String extensionOf(String path) {
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : null;
    }

    private static int[] parseSize(String s) {
        String[] parts = s.split("x");
        return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
    }
}
