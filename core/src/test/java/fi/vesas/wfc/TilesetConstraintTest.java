package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import fi.vesas.wfc.SimpleWFC.DIR;

/**
 * End-to-end check for the built-in tilesets: the solver must produce a grid
 * where every shared edge has matching ports, and the exported image must
 * show pipe pixels on both sides of an edge or on neither.
 */
public class TilesetConstraintTest {

    private static final File ASSETS = new File("../assets");

    private static TilesetDef byName(String name) {
        for (TilesetDef def : TilesetDef.allTilesets()) {
            if (def.name.equals(name)) return def;
        }
        throw new IllegalArgumentException(name);
    }

    private static SimpleWFC solve(TilesetDef def, int seed, boolean tiling) {
        SimpleWFC wfc = new SimpleWFC(10, 8, def.tileCount, def.emptyAllowed, def.rotationsAllowed);
        wfc.setSeed(seed);
        wfc.setTilingHorizontal(tiling);
        wfc.setTilingVertical(tiling);
        wfc.setConstraints(def.constraints);
        wfc.run();
        return wfc;
    }

    // Port facing world direction d for a tile whose rotation bit index is j.
    private static int worldPort(Constraints c, int tile, int j, DIR d) {
        if (tile == 0) return -1;
        return c.getPort(tile, d.rotateCCW(j));
    }

    @Test public void pipePorts() { checkPorts("Pipe"); }
    @Test public void crossPorts() { checkPorts("Cross"); }
    @Test public void circuitPorts() { checkPorts("Circuit"); }

    private void checkPorts(String name) {
        TilesetDef def = byName(name);
        for (boolean tiling : new boolean[] {false, true}) {
            for (int seed = 1; seed <= 20; seed++) {
                SimpleWFC wfc = solve(def, seed, tiling);
                if (wfc.isContradiction()) continue;
                int w = wfc.getWidth(), h = wfc.getHeight();
                int[][] tile = new int[w][h], rot = new int[w][h];
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        int[] t = wfc.getGrid(x, y), r = wfc.getRots(x, y);
                        assertEquals(1, t.length, "cell not collapsed");
                        tile[x][y] = t[0];
                        rot[x][y] = Integer.numberOfTrailingZeros(r[0]);
                        // Several rotation bits are fine only if they all give the same ports.
                        for (int j = 0; j < 4; j++) {
                            if ((r[0] & (1 << j)) == 0) continue;
                            for (DIR d : DIR.values()) {
                                if (worldPort(def.constraints, t[0], j, d) != worldPort(def.constraints, t[0], rot[x][y], d))
                                    fail(name + " seed " + seed + ": tile " + t[0] + " at " + x + "," + y
                                            + " left with distinct rotations, mask " + r[0]);
                            }
                        }
                    }
                }
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        // East neighbour
                        if (x + 1 < w || tiling) {
                            int nx = (x + 1) % w;
                            int a = worldPort(def.constraints, tile[x][y], rot[x][y], DIR.E);
                            int b = worldPort(def.constraints, tile[nx][y], rot[nx][y], DIR.W);
                            if (a != b) fail(name + " seed " + seed + " tiling " + tiling
                                    + ": E/W mismatch at " + x + "," + y + " " + a + " vs " + b);
                        }
                        // North neighbour (y up)
                        if (y + 1 < h || tiling) {
                            int ny = (y + 1) % h;
                            int a = worldPort(def.constraints, tile[x][y], rot[x][y], DIR.N);
                            int b = worldPort(def.constraints, tile[x][ny], rot[x][ny], DIR.S);
                            if (a != b) fail(name + " seed " + seed + " tiling " + tiling
                                    + ": N/S mismatch at " + x + "," + y + " " + a + " vs " + b);
                        }
                    }
                }
            }
        }
    }

    private static boolean opaque(BufferedImage img, int px, int py) {
        return ((img.getRGB(px, py) >>> 24) & 0xFF) > 0;
    }

    @Test public void pipeImage() throws IOException { checkImage("Pipe"); }
    @Test public void crossImage() throws IOException { checkImage("Cross"); }

    private void checkImage(String name) throws IOException {
        TilesetDef def = byName(name);
        int tw = def.tilePixelW, th = def.tilePixelH;
        int solved = 0;
        for (int seed = 1; seed <= 10; seed++) {
            SimpleWFC wfc = solve(def, seed, false);
            if (wfc.isContradiction()) continue;
            solved++;
            BufferedImage img = TiledImageExporter.export(wfc, ASSETS.getPath(), def.basename, tw, th);
            int w = wfc.getWidth(), h = wfc.getHeight();
            for (int x = 0; x < w; x++) {
                for (int row = 0; row < h; row++) {
                    int px = x * tw, py = row * th; // image row 0 is the top
                    if (x + 1 < w) {
                        boolean a = opaque(img, px + tw - 1, py + th / 2);
                        boolean b = opaque(img, px + tw, py + th / 2);
                        assertEquals(a, b, name + " seed " + seed + ": horizontal edge at cell " + x + ", image row " + row);
                    }
                    if (row + 1 < h) {
                        boolean a = opaque(img, px + tw / 2, py + th - 1);
                        boolean b = opaque(img, px + tw / 2, py + th);
                        assertEquals(a, b, name + " seed " + seed + ": vertical edge at cell " + x + ", image row " + row);
                    }
                }
            }
        }
        assertTrue(solved > 0, "no seed solved");
    }
}
