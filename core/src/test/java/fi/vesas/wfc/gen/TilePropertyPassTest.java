package fi.vesas.wfc.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import fi.vesas.wfc.Constraints;
import fi.vesas.wfc.SimpleWFC;
import fi.vesas.wfc.TileMapExporter;
import fi.vesas.wfc.TilesetDef;

public class TilePropertyPassTest {

    @TempDir
    File tempDir;

    // Two tile IDs (0, 1), no ports: anything fits anywhere.
    private static TilesetDef freeTileset() {
        return new TilesetDef("Free", "f", 2, true, false, 8, 8, new Constraints());
    }

    private static GenContext ctxWithTiles(int[][] tiles) {
        GenContext ctx = new GenContext(tiles.length, tiles[0].length, 1);
        IntLayer layer = ctx.intLayer("terrain");
        for (int x = 0; x < tiles.length; x++)
            for (int y = 0; y < tiles[0].length; y++)
                layer.set(x, y, tiles[x][y]);
        return ctx;
    }

    // ---------- TilesetDef tags ----------

    @Test
    public void tagQueries() {
        TilesetDef def = new TilesetDef("T", "t", 4, true, false, 8, 8, new Constraints())
                .tag("walkable", 0, 2)
                .tag("water", 3)
                .tag("walkable", 3);
        assertTrue(def.hasTag(2, "walkable"));
        assertFalse(def.hasTag(1, "walkable"));
        assertFalse(def.hasTag(0, "lava"));
        assertFalse(def.hasTag(99, "walkable"));
        assertArrayEquals(new int[]{0, 2, 3}, def.tileIdsWithTag("walkable"));
        assertEquals(List.of("walkable", "water"), def.tagsOf(3));
        assertEquals(List.of("walkable", "water"), List.copyOf(def.tagNames()));
    }

    @Test
    public void tagIdOutOfRange() {
        TilesetDef def = new TilesetDef("T", "t", 3, true, false, 8, 8, new Constraints());
        assertThrows(IllegalArgumentException.class, () -> def.tag("walkable", 3));
        // no empty tile: IDs are 1..tileCount, so 3 is valid
        TilesetDef noEmpty = new TilesetDef("T", "t", 3, false, false, 8, 8, new Constraints());
        noEmpty.tag("walkable", 3);
        assertTrue(noEmpty.hasTag(3, "walkable"));
    }

    @Test
    public void tagsFromJson() {
        TilesetDef def = TilesetDef.loadFromJson(new File("../assets/circuit.json"));
        assertArrayEquals(new int[]{0}, def.tileIdsWithTag("walkable"));
        assertArrayEquals(new int[]{1}, def.tileIdsWithTag("chip"));
        assertEquals(7, def.tileIdsWithTag("trace").length);
    }

    @Test
    public void badTagsJsonRejected() throws IOException {
        File f = new File(tempDir, "bad.json");
        Files.write(f.toPath(), "{\"name\":\"T\",\"basename\":\"t\",\"tileCount\":3,\"tags\":{\"walkable\":5}}"
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> TilesetDef.loadFromJson(f));
    }

    @Test
    public void withImagesKeepsTags() {
        TilesetDef def = freeTileset().tag("walkable", 1);
        TilesetDef copy = def.withImages("x", "g", 16, 16);
        assertTrue(copy.hasTag(1, "walkable"));
        assertEquals(16, copy.tilePixelW);
    }

    // ---------- TilePropertyPass ----------

    @Test
    public void maskFromTag() {
        TilesetDef def = freeTileset().tag("walkable", 0);
        GenContext ctx = ctxWithTiles(new int[][]{{0, 1}, {1, 0}});
        TilePropertyPass.fromTag(def, "terrain", "walkable", "walk").apply(ctx);
        BoolLayer m = ctx.boolLayer("walk");
        assertTrue(m.get(0, 0));
        assertFalse(m.get(0, 1));
        assertFalse(m.get(1, 0));
        assertTrue(m.get(1, 1));
    }

    @Test
    public void maskFromIds() {
        GenContext ctx = ctxWithTiles(new int[][]{{0, 5}, {7, 2}});
        TilePropertyPass.fromIds("terrain", "m", 5, 2).apply(ctx);
        BoolLayer m = ctx.boolLayer("m");
        assertFalse(m.get(0, 0));
        assertTrue(m.get(0, 1));
        assertFalse(m.get(1, 0));
        assertTrue(m.get(1, 1));
    }

    @Test
    public void unknownTagFailsEarly() {
        TilesetDef def = freeTileset().tag("walkable", 0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TilePropertyPass.fromTag(def, "terrain", "walkabel", "walk"));
        assertTrue(e.getMessage().contains("walkable"), e.getMessage());
    }

    @Test
    public void missingTileLayerFails() {
        GenContext ctx = new GenContext(2, 2, 1);
        assertThrows(IllegalStateException.class,
                () -> TilePropertyPass.fromIds("terrain", "m", 0).apply(ctx));
    }

    // ---------- pipeline: WFC -> mask -> reachability ----------

    @Test
    public void pipelineReachablePasses() {
        TilesetDef def = freeTileset().tag("walkable", 0, 1);
        GenPipeline p = new GenPipeline()
                .add(new WfcPass("terrain", def.tileCount, def.constraints).rotationsAllowed(false))
                .add(TilePropertyPass.fromTag(def, "terrain", "walkable", "walk"))
                .add(new ReachabilityCheckPass("walk", 0, 0));
        p.run(new GenContext(10, 10, 5));  // must not throw
    }

    @Test
    public void pipelineWalledOffCellFails() {
        TilesetDef def = freeTileset().tag("walkable", 0);
        // ring of wall tiles (1) around (2,2), which is pinned walkable; corner (0,0) walkable too
        PinnedTilesPass pins = new PinnedTilesPass(null);
        for (int x = 1; x <= 3; x++)
            for (int y = 1; y <= 3; y++)
                pins.pin(x, y, (x == 2 && y == 2) ? 0 : 1);
        pins.pin(0, 0, 0);
        GenPipeline p = new GenPipeline()
                .add(new WfcPass("terrain", def.tileCount, def.constraints)
                        .rotationsAllowed(false)
                        .withAllowedTileSource(pins.asSource()))
                .add(TilePropertyPass.fromTag(def, "terrain", "walkable", "walk"))
                .add(new ReachabilityCheckPass("walk", 0, 0));
        assertThrows(GenerationFailedException.class, () -> p.run(new GenContext(6, 6, 5)));
    }

    // ---------- export ----------

    @Test
    public void tagsExportedAsTiledProperties() throws IOException {
        TilesetDef def = TilesetDef.loadFromJson(new File("../assets/circuit.json"));
        SimpleWFC wfc = def.createSolver(4, 4);
        wfc.setSeed(3);
        wfc.run();

        File tmx = new File(tempDir, "m.tmx");
        TileMapExporter.exportToFile(wfc, def, tmx.getPath());
        String xml = new String(Files.readAllBytes(tmx.toPath()), StandardCharsets.UTF_8);
        // tile ID 1 (chip) is Tiled local id 0
        int tile0 = xml.indexOf("<tile id=\"0\">");
        int tile1 = xml.indexOf("<tile id=\"1\">");
        assertTrue(xml.substring(tile0, tile1).contains("<property name=\"chip\" type=\"bool\" value=\"true\"/>"));
        assertFalse(xml.substring(tile0, tile1).contains("trace"));

        File tmj = new File(tempDir, "m.tmj");
        TileMapExporter.exportToFile(wfc, def, tmj.getPath());
        JsonValue tiles = new JsonReader().parse(new String(Files.readAllBytes(tmj.toPath()), StandardCharsets.UTF_8))
                .get("tilesets").child.get("tiles");
        JsonValue prop = tiles.get(1).get("properties").child;  // tile ID 2 -> trace
        assertEquals("trace", prop.getString("name"));
        assertTrue(prop.getBoolean("value"));

        File json = new File(tempDir, "m.json");
        TileMapExporter.exportToFile(wfc, def, json.getPath());
        JsonValue root = new JsonReader().parse(new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8));
        assertArrayEquals(new int[]{0}, root.get("tags").get("walkable").asIntArray());
    }
}
