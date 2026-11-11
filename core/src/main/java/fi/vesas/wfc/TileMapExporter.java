package fi.vesas.wfc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a solved tile grid as map data a game can load.
 *
 * <ul>
 *   <li>{@code .json} — plain grid: tile IDs and quarter turns, row-major, row 0 at the top.</li>
 *   <li>{@code .tmx} / {@code .tmj} — Tiled map (XML / JSON) with an embedded
 *       "collection of images" tileset pointing at the tile PNGs. Rotations
 *       become Tiled flip flags, so the map opens in Tiled and loads in
 *       engines with Tiled support (libGDX TmxMapLoader, Godot, Unity, ...).</li>
 * </ul>
 *
 * Input grids are indexed {@code [x][y]} with y = 0 at the bottom (solver
 * convention). Tile ID 0 is empty when the tileset allows empty cells.
 * Rotation values are the solver's bitmasks (1, 2, 4, 8 = 0, 90, 180, 270
 * degrees clockwise). This class has no AWT or libGDX dependency.
 */
public final class TileMapExporter {

    // Tiled gid flag bits
    static final long FLIP_H = 0x80000000L;
    static final long FLIP_V = 0x40000000L;
    static final long FLIP_D = 0x20000000L;

    private static final String TILED_VERSION = "1.10";

    private TileMapExporter() {}

    /** Quarter turns clockwise (0..3) for a single-bit rotation mask; 0 for 0 or multi-bit. */
    public static int quarterTurns(int rotMask) {
        switch (rotMask) {
            case 2: return 1;
            case 4: return 2;
            case 8: return 3;
            default: return 0;
        }
    }

    /**
     * Tiled global tile ID for a solver tile ID and rotation mask, with
     * firstgid 1 and tile image N at local ID N-1. Tile 0 maps to 0 (empty).
     */
    public static long tiledGid(int tileId, int rotMask) {
        if (tileId <= 0) return 0;
        long gid = tileId;
        switch (quarterTurns(rotMask)) {
            case 1: return gid | FLIP_D | FLIP_H;
            case 2: return gid | FLIP_H | FLIP_V;
            case 3: return gid | FLIP_D | FLIP_V;
            default: return gid;
        }
    }

    // ---------- grid extraction ----------

    /** First remaining tile ID per cell, [x][y]. */
    public static int[][] tilesOf(SimpleWFC wfc) {
        int w = wfc.getWidth(), h = wfc.getHeight();
        int[][] out = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int[] t = wfc.getGrid(x, y);
                out[x][y] = t.length > 0 ? t[0] : 0;
            }
        }
        return out;
    }

    /** First remaining rotation mask per cell, [x][y]. */
    public static int[][] rotationsOf(SimpleWFC wfc) {
        int w = wfc.getWidth(), h = wfc.getHeight();
        int[][] out = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int[] r = wfc.getRots(x, y);
                out[x][y] = r.length > 0 ? r[0] : 1;
            }
        }
        return out;
    }

    // ---------- plain JSON ----------

    public static String toJson(int[][] tiles, int[][] rots, String tilesetName) {
        return toJson(tiles, rots, tilesetName, null);
    }

    /** Plain grid; when {@code def} is given its tags are written as {@code "tags": {name: [tile IDs]}}. */
    public static String toJson(int[][] tiles, int[][] rots, TilesetDef def) {
        return toJson(tiles, rots, def != null ? def.name : null, def);
    }

    private static String toJson(int[][] tiles, int[][] rots, String tilesetName, TilesetDef def) {
        int w = tiles.length, h = w > 0 ? tiles[0].length : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"width\": ").append(w).append(",\n");
        sb.append("  \"height\": ").append(h).append(",\n");
        if (tilesetName != null) {
            sb.append("  \"tileset\": ").append(jsonString(tilesetName)).append(",\n");
        }
        if (def != null && !def.tagNames().isEmpty()) {
            sb.append("  \"tags\": {");
            boolean first = true;
            for (String t : def.tagNames()) {
                sb.append(first ? "\n    " : ",\n    ").append(jsonString(t)).append(": [");
                int[] ids = def.tileIdsWithTag(t);
                for (int i = 0; i < ids.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(ids[i]);
                }
                sb.append(']');
                first = false;
            }
            sb.append("\n  },\n");
        }
        sb.append("  \"origin\": \"top-left\",\n");
        sb.append("  \"rotation\": \"quarter turns clockwise\",\n");
        sb.append("  \"tiles\": [");
        appendRows(sb, w, h, (x, y) -> tiles[x][y]);
        sb.append("],\n");
        sb.append("  \"rotations\": [");
        appendRows(sb, w, h, (x, y) -> quarterTurns(rots[x][y]));
        sb.append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ---------- Tiled TMX (XML) ----------

    /**
     * @param imageDir path written into the tileset for tile images, relative
     *                 to the map file (use {@link #relativeImageDir}).
     */
    public static String toTmx(int[][] tiles, int[][] rots, TilesetDef def, String imageDir) {
        int w = tiles.length, h = w > 0 ? tiles[0].length : 0;
        int tw = def.tilePixelW, th = def.tilePixelH;
        int count = def.maxTileId();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<map version=\"").append(TILED_VERSION).append("\" orientation=\"orthogonal\"")
          .append(" renderorder=\"right-down\" width=\"").append(w).append("\" height=\"").append(h)
          .append("\" tilewidth=\"").append(tw).append("\" tileheight=\"").append(th)
          .append("\" infinite=\"0\" nextlayerid=\"2\" nextobjectid=\"1\">\n");

        sb.append(" <tileset firstgid=\"1\" name=\"").append(xml(def.name))
          .append("\" tilewidth=\"").append(tw).append("\" tileheight=\"").append(th)
          .append("\" tilecount=\"").append(count).append("\" columns=\"0\">\n");
        sb.append("  <grid orientation=\"orthogonal\" width=\"1\" height=\"1\"/>\n");
        for (int id = 1; id <= count; id++) {
            sb.append("  <tile id=\"").append(id - 1).append("\">\n");
            List<String> tileTags = def.tagsOf(id);
            if (!tileTags.isEmpty()) {
                sb.append("   <properties>\n");
                for (String t : tileTags) {
                    sb.append("    <property name=\"").append(xml(t)).append("\" type=\"bool\" value=\"true\"/>\n");
                }
                sb.append("   </properties>\n");
            }
            sb.append("   <image source=\"").append(xml(imagePath(imageDir, def.tileImageName(id))))
              .append("\" width=\"").append(tw).append("\" height=\"").append(th).append("\"/>\n");
            sb.append("  </tile>\n");
        }
        sb.append(" </tileset>\n");

        sb.append(" <layer id=\"1\" name=\"wfc\" width=\"").append(w).append("\" height=\"").append(h).append("\">\n");
        sb.append("  <data encoding=\"csv\">\n");
        for (int row = 0; row < h; row++) {
            int y = h - 1 - row;
            for (int x = 0; x < w; x++) {
                sb.append(tiledGid(tiles[x][y], rots[x][y]));
                if (row < h - 1 || x < w - 1) sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  </data>\n");
        sb.append(" </layer>\n");
        sb.append("</map>\n");
        return sb.toString();
    }

    // ---------- Tiled TMJ (JSON) ----------

    public static String toTmj(int[][] tiles, int[][] rots, TilesetDef def, String imageDir) {
        int w = tiles.length, h = w > 0 ? tiles[0].length : 0;
        int tw = def.tilePixelW, th = def.tilePixelH;
        int count = def.maxTileId();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(" \"type\": \"map\",\n");
        sb.append(" \"version\": \"").append(TILED_VERSION).append("\",\n");
        sb.append(" \"orientation\": \"orthogonal\",\n");
        sb.append(" \"renderorder\": \"right-down\",\n");
        sb.append(" \"width\": ").append(w).append(",\n");
        sb.append(" \"height\": ").append(h).append(",\n");
        sb.append(" \"tilewidth\": ").append(tw).append(",\n");
        sb.append(" \"tileheight\": ").append(th).append(",\n");
        sb.append(" \"infinite\": false,\n");
        sb.append(" \"compressionlevel\": -1,\n");
        sb.append(" \"nextlayerid\": 2,\n");
        sb.append(" \"nextobjectid\": 1,\n");

        sb.append(" \"layers\": [{\n");
        sb.append("  \"id\": 1, \"name\": \"wfc\", \"type\": \"tilelayer\",\n");
        sb.append("  \"x\": 0, \"y\": 0, \"width\": ").append(w).append(", \"height\": ").append(h).append(",\n");
        sb.append("  \"opacity\": 1, \"visible\": true,\n");
        sb.append("  \"data\": [");
        appendRowsLong(sb, w, h, (x, y) -> tiledGid(tiles[x][y], rots[x][y]));
        sb.append("]\n");
        sb.append(" }],\n");

        sb.append(" \"tilesets\": [{\n");
        sb.append("  \"firstgid\": 1, \"name\": ").append(jsonString(def.name)).append(",\n");
        sb.append("  \"tilewidth\": ").append(tw).append(", \"tileheight\": ").append(th)
          .append(", \"tilecount\": ").append(count).append(", \"columns\": 0,\n");
        sb.append("  \"margin\": 0, \"spacing\": 0,\n");
        sb.append("  \"grid\": {\"orientation\": \"orthogonal\", \"width\": 1, \"height\": 1},\n");
        sb.append("  \"tiles\": [");
        for (int id = 1; id <= count; id++) {
            sb.append(id == 1 ? "\n" : ",\n");
            sb.append("   {\"id\": ").append(id - 1)
              .append(", \"image\": ").append(jsonString(imagePath(imageDir, def.tileImageName(id))))
              .append(", \"imagewidth\": ").append(tw).append(", \"imageheight\": ").append(th);
            List<String> tileTags = def.tagsOf(id);
            if (!tileTags.isEmpty()) {
                sb.append(", \"properties\": [");
                for (int i = 0; i < tileTags.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("{\"name\": ").append(jsonString(tileTags.get(i)))
                      .append(", \"type\": \"bool\", \"value\": true}");
                }
                sb.append(']');
            }
            sb.append('}');
        }
        sb.append("\n  ]\n");
        sb.append(" }]\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ---------- file output ----------

    /** True when the path's extension is one this exporter writes. */
    public static boolean isMapFormat(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".json") || p.endsWith(".tmx") || p.endsWith(".tmj");
    }

    /** Write the solved grid in the format chosen by the file extension (.json, .tmx, .tmj). */
    public static void exportToFile(SimpleWFC wfc, TilesetDef def, String outputPath) throws IOException {
        exportToFile(tilesOf(wfc), rotationsOf(wfc), def, outputPath);
    }

    public static void exportToFile(int[][] tiles, int[][] rots, TilesetDef def, String outputPath) throws IOException {
        File out = new File(outputPath).getAbsoluteFile();
        String lower = outputPath.toLowerCase();
        String text;
        if (lower.endsWith(".tmx")) {
            text = toTmx(tiles, rots, def, relativeImageDir(out, def));
        } else if (lower.endsWith(".tmj")) {
            text = toTmj(tiles, rots, def, relativeImageDir(out, def));
        } else if (lower.endsWith(".json")) {
            text = toJson(tiles, rots, def);
        } else {
            throw new IllegalArgumentException("Unsupported map format (use .json, .tmx or .tmj): " + outputPath);
        }
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        Files.write(out.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Directory of the tileset's images, relative to the map file's folder,
     * with forward slashes. Falls back to an absolute path when no relative
     * path exists (e.g. different drives on Windows). A null tileDir means the
     * working directory (where built-in assets are loaded from).
     */
    public static String relativeImageDir(File mapFile, TilesetDef def) {
        Path images = new File(def.tileDir != null ? def.tileDir : ".").getAbsoluteFile().toPath().normalize();
        File mapDir = mapFile.getAbsoluteFile().getParentFile();
        if (mapDir == null) return images.toString().replace('\\', '/');
        try {
            String rel = mapDir.toPath().normalize().relativize(images).toString().replace('\\', '/');
            return rel;
        } catch (IllegalArgumentException e) {
            return images.toString().replace('\\', '/');
        }
    }

    // ---------- helpers ----------

    private interface CellInt { int at(int x, int y); }
    private interface CellLong { long at(int x, int y); }

    // Row-major, top row first.
    private static void appendRows(StringBuilder sb, int w, int h, CellInt f) {
        for (int row = 0; row < h; row++) {
            int y = h - 1 - row;
            sb.append("\n    ");
            for (int x = 0; x < w; x++) {
                sb.append(f.at(x, y));
                if (row < h - 1 || x < w - 1) sb.append(", ");
            }
        }
        sb.append("\n  ");
    }

    private static void appendRowsLong(StringBuilder sb, int w, int h, CellLong f) {
        for (int row = 0; row < h; row++) {
            int y = h - 1 - row;
            sb.append("\n   ");
            for (int x = 0; x < w; x++) {
                sb.append(f.at(x, y));
                if (row < h - 1 || x < w - 1) sb.append(", ");
            }
        }
        sb.append("\n  ");
    }

    private static String imagePath(String dir, String file) {
        if (dir == null || dir.isEmpty()) return file;
        return dir.endsWith("/") ? dir + file : dir + "/" + file;
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
