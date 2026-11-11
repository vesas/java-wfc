package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

public class TileMapExporterTest {

    @TempDir
    File tempDir;

    private static final long FLAGS = TileMapExporter.FLIP_H | TileMapExporter.FLIP_V | TileMapExporter.FLIP_D;

    @Test
    public void gidFlags() {
        assertEquals(0, TileMapExporter.tiledGid(0, 4));
        assertEquals(3, TileMapExporter.tiledGid(3, 1));
        assertEquals(3 | TileMapExporter.FLIP_D | TileMapExporter.FLIP_H, TileMapExporter.tiledGid(3, 2));
        assertEquals(3 | TileMapExporter.FLIP_H | TileMapExporter.FLIP_V, TileMapExporter.tiledGid(3, 4));
        assertEquals(3 | TileMapExporter.FLIP_D | TileMapExporter.FLIP_V, TileMapExporter.tiledGid(3, 8));
    }

    // Apply Tiled's flip flags to an image: diagonal first, then horizontal, then vertical.
    private static BufferedImage applyTiledFlags(BufferedImage src, long gid) {
        BufferedImage img = src;
        int n = src.getWidth();
        if ((gid & TileMapExporter.FLIP_D) != 0) {
            BufferedImage o = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) o.setRGB(x, y, img.getRGB(y, x));
            img = o;
        }
        if ((gid & TileMapExporter.FLIP_H) != 0) {
            BufferedImage o = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) o.setRGB(x, y, img.getRGB(n - 1 - x, y));
            img = o;
        }
        if ((gid & TileMapExporter.FLIP_V) != 0) {
            BufferedImage o = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) o.setRGB(x, y, img.getRGB(x, n - 1 - y));
            img = o;
        }
        return img;
    }

    /** Tiled's rendering of each rotation must match the PNG exporter pixel for pixel. */
    @Test
    public void rotationFlagsMatchPngExporter() throws IOException {
        int n = 8;
        // asymmetric tile: one marked corner pixel and a bar on one edge
        BufferedImage tile = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) tile.setRGB(x, y, 0xFF000000);
        for (int x = 0; x < n; x++) tile.setRGB(x, 0, 0xFFFF0000);
        tile.setRGB(0, 1, 0xFF00FF00);
        ImageIO.write(tile, "PNG", new File(tempDir, "r1.png"));

        for (int j = 0; j < 4; j++) {
            SimpleWFC wfc = new SimpleWFC(1, 1, 2, true, true);
            wfc.setConstraints(new Constraints());
            wfc.setValuesAt(0, 0, 1);
            wfc.setRotationsAt(0, 0, 1 << j);

            BufferedImage png = TiledImageExporter.export(wfc, tempDir.getPath(), "r", n, n);
            long gid = TileMapExporter.tiledGid(TileMapExporter.tilesOf(wfc)[0][0], TileMapExporter.rotationsOf(wfc)[0][0]);
            BufferedImage tiled = applyTiledFlags(tile, gid);

            for (int x = 0; x < n; x++)
                for (int y = 0; y < n; y++)
                    assertEquals(png.getRGB(x, y), tiled.getRGB(x, y), "rotation bit " + j + " at " + x + "," + y);
        }
    }

    private static SimpleWFC solvedCircuit(TilesetDef def, int w, int h, int seed) {
        SimpleWFC wfc = def.createSolver(w, h);
        wfc.setSeed(seed);
        wfc.run();
        assertTrue(wfc.isFinished() && !wfc.isContradiction());
        return wfc;
    }

    private static List<Long> parseCsv(String csv) {
        List<Long> out = new ArrayList<>();
        for (String s : csv.trim().split("[,\\s]+")) out.add(Long.parseLong(s));
        return out;
    }

    @Test
    public void tmxLayoutAndImages() throws IOException {
        TilesetDef def = TilesetDef.loadFromJson(new File("../assets/circuit.json"));
        SimpleWFC wfc = solvedCircuit(def, 6, 4, 229);
        File out = new File(tempDir, "maps/out.tmx");
        TileMapExporter.exportToFile(wfc, def, out.getPath());

        String xml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("width=\"6\" height=\"4\" tilewidth=\"32\" tileheight=\"32\""));
        assertTrue(xml.contains("tilecount=\"8\""));

        // image paths are relative to the map file and point at real files
        int idx = xml.indexOf("source=\"");
        String src = xml.substring(idx + 8, xml.indexOf('"', idx + 8));
        assertTrue(new File(out.getParentFile(), src).isFile(), "missing image " + src);
        assertTrue(src.endsWith("circ1.png"));

        String csv = xml.substring(xml.indexOf("<data encoding=\"csv\">") + 21, xml.indexOf("</data>"));
        List<Long> gids = parseCsv(csv);
        assertEquals(24, gids.size());

        // row 0 of the map is the top row (solver y = h-1)
        int[][] tiles = TileMapExporter.tilesOf(wfc);
        int[][] rots = TileMapExporter.rotationsOf(wfc);
        for (int row = 0; row < 4; row++) {
            for (int x = 0; x < 6; x++) {
                int y = 3 - row;
                long gid = gids.get(row * 6 + x);
                assertEquals(TileMapExporter.tiledGid(tiles[x][y], rots[x][y]), gid);
                assertEquals(tiles[x][y], gid & ~FLAGS);
            }
        }
    }

    @Test
    public void tmjParsesAndMatchesTmx() throws IOException {
        TilesetDef def = TilesetDef.loadFromJson(new File("../assets/circuit.json"));
        SimpleWFC wfc = solvedCircuit(def, 5, 3, 42);
        File tmj = new File(tempDir, "out.tmj");
        File tmx = new File(tempDir, "out.tmx");
        TileMapExporter.exportToFile(wfc, def, tmj.getPath());
        TileMapExporter.exportToFile(wfc, def, tmx.getPath());

        JsonValue root = new JsonReader().parse(new String(Files.readAllBytes(tmj.toPath()), StandardCharsets.UTF_8));
        assertEquals("map", root.getString("type"));
        assertEquals(5, root.getInt("width"));
        assertEquals(3, root.getInt("height"));
        JsonValue layer = root.get("layers").child;
        assertEquals("tilelayer", layer.getString("type"));

        String xml = new String(Files.readAllBytes(tmx.toPath()), StandardCharsets.UTF_8);
        List<Long> fromTmx = parseCsv(xml.substring(xml.indexOf("<data encoding=\"csv\">") + 21, xml.indexOf("</data>")));
        // read raw numbers from the JSON text (libGDX parses large ints as doubles)
        String text = new String(Files.readAllBytes(tmj.toPath()), StandardCharsets.UTF_8);
        int start = text.indexOf("\"data\": [") + 9;
        List<Long> fromTmj = parseCsv(text.substring(start, text.indexOf(']', start)));
        assertEquals(fromTmx, fromTmj);

        JsonValue ts = root.get("tilesets").child;
        assertEquals(1, ts.getInt("firstgid"));
        assertEquals(8, ts.get("tiles").size);
        String image = ts.get("tiles").child.getString("image");
        assertTrue(new File(tempDir, image).isFile(), "missing image " + image);
    }

    @Test
    public void plainJsonGrid() throws IOException {
        int[][] tiles = {{1, 2}, {3, 0}};   // [x][y], y=0 bottom
        int[][] rots = {{1, 2}, {4, 8}};
        String json = TileMapExporter.toJson(tiles, rots, "T");
        JsonValue root = new JsonReader().parse(json);
        assertEquals(2, root.getInt("width"));
        // top row first: (0,1)=2, (1,1)=0, then (0,0)=1, (1,0)=3
        int[] t = root.get("tiles").asIntArray();
        int[] r = root.get("rotations").asIntArray();
        assertEquals(List.of(2, 0, 1, 3), List.of(t[0], t[1], t[2], t[3]));
        assertEquals(List.of(1, 3, 0, 2), List.of(r[0], r[1], r[2], r[3]));
    }

    @Test
    public void cliWritesTiledMap() throws IOException {
        File out = new File(tempDir, "cli/map.tmx");
        WfcCli.main(new String[]{"tiled", "../assets/circuit.json", "--size", "8x6", "--seed", "229",
                "--output", out.getPath()});
        assertTrue(out.isFile());
        String xml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<map "));
        assertTrue(xml.contains("width=\"8\" height=\"6\""));
    }

    @Test
    public void cliBatchFormat() {
        File dir = new File(tempDir, "batch");
        WfcCli.main(new String[]{"tiled", "../assets/circuit.json", "--size", "6x6", "--seed", "1",
                "--count", "3", "--format", "tmj", "--output", dir.getPath()});
        String[] files = dir.list((d, name) -> name.endsWith(".tmj"));
        assertEquals(3, files.length);
    }

    @Test
    public void cliDirModeUsesBuiltInCircuit() {
        TilesetDef def = WfcCli.loadTileset("../assets", null, new int[]{16, 16});
        assertEquals("Circuit", def.name);
        assertEquals(16, def.tilePixelW);
        assertEquals("../assets", def.tileDir.replace('\\', '/'));
    }
}
