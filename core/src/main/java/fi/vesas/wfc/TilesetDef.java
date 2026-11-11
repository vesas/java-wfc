package fi.vesas.wfc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import fi.vesas.wfc.SimpleWFC.DIR;

/**
 * Bundles everything needed for a tiled-mode WFC tileset:
 * name, file prefix, tile count, pixel dimensions, and port constraints.
 */
public class TilesetDef {

    public final String name;
    public final String basename;
    public final int tileCount;
    public final boolean emptyAllowed;
    public final boolean rotationsAllowed;
    public final int tilePixelW;
    public final int tilePixelH;
    public final Constraints constraints;
    public final String tileDir; // absolute path to tile images, null for built-in assets
    public final double[] weights; // selection weight per tile ID, null = uniform

    // tag name -> flag per tile ID (walkable, solid, water, ...). Insertion order kept.
    private final Map<String, boolean[]> tags = new LinkedHashMap<>();

    public TilesetDef(String name, String basename, int tileCount,
                      boolean emptyAllowed, boolean rotationsAllowed,
                      int tilePixelW, int tilePixelH, Constraints constraints) {
        this(name, basename, tileCount, emptyAllowed, rotationsAllowed, tilePixelW, tilePixelH, constraints, null);
    }

    public TilesetDef(String name, String basename, int tileCount,
                      boolean emptyAllowed, boolean rotationsAllowed,
                      int tilePixelW, int tilePixelH, Constraints constraints, String tileDir) {
        this(name, basename, tileCount, emptyAllowed, rotationsAllowed, tilePixelW, tilePixelH,
                constraints, tileDir, null);
    }

    public TilesetDef(String name, String basename, int tileCount,
                      boolean emptyAllowed, boolean rotationsAllowed,
                      int tilePixelW, int tilePixelH, Constraints constraints, String tileDir,
                      double[] weights) {
        this.weights = weights;
        this.name = name;
        this.basename = basename;
        this.tileCount = tileCount;
        this.emptyAllowed = emptyAllowed;
        this.rotationsAllowed = rotationsAllowed;
        this.tilePixelW = tilePixelW;
        this.tilePixelH = tilePixelH;
        this.constraints = constraints;
        this.tileDir = tileDir;
    }

    @Override
    public String toString() {
        return name;
    }

    /** Number of tile ID slots (IDs run 0..idSlots()-1; ID 0 is unused when empty is not allowed). */
    public int idSlots() {
        return emptyAllowed ? tileCount : tileCount + 1;
    }

    /**
     * Mark tile IDs with a tag (e.g. "walkable"). Adds to any IDs already
     * tagged. Returns this for chaining.
     */
    public TilesetDef tag(String tag, int... tileIds) {
        if (tag == null || tag.isEmpty()) throw new IllegalArgumentException("Tag name must not be empty");
        boolean[] flags = tags.computeIfAbsent(tag, k -> new boolean[idSlots()]);
        for (int id : tileIds) {
            if (id < 0 || id >= flags.length) {
                throw new IllegalArgumentException("Tag '" + tag + "': tile ID out of range: " + id);
            }
            flags[id] = true;
        }
        return this;
    }

    /** True when the tileset defines this tag (even if no tile carries it). */
    public boolean hasTagDefined(String tag) {
        return tags.containsKey(tag);
    }

    /** True when tile ID carries the tag. Unknown tags and IDs out of range are false. */
    public boolean hasTag(int tileId, String tag) {
        boolean[] flags = tags.get(tag);
        return flags != null && tileId >= 0 && tileId < flags.length && flags[tileId];
    }

    /** Tile IDs that carry the tag, ascending. Empty for unknown tags. */
    public int[] tileIdsWithTag(String tag) {
        boolean[] flags = tags.get(tag);
        if (flags == null) return new int[0];
        int n = 0;
        for (boolean b : flags) if (b) n++;
        int[] out = new int[n];
        int i = 0;
        for (int id = 0; id < flags.length; id++) if (flags[id]) out[i++] = id;
        return out;
    }

    /** Tag names in definition order. */
    public Set<String> tagNames() {
        return Collections.unmodifiableSet(tags.keySet());
    }

    /** Tags carried by one tile ID, in definition order. */
    public List<String> tagsOf(int tileId) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, boolean[]> e : tags.entrySet()) {
            boolean[] f = e.getValue();
            if (tileId >= 0 && tileId < f.length && f[tileId]) out.add(e.getKey());
        }
        return out;
    }

    /** New solver with this tileset's size flags, constraints and weights applied. */
    public SimpleWFC createSolver(int gridW, int gridH) {
        SimpleWFC wfc = new SimpleWFC(gridW, gridH, tileCount, emptyAllowed, rotationsAllowed);
        wfc.setConstraints(constraints);
        wfc.setWeights(weights);
        return wfc;
    }

    /** Highest tile ID that has an image ({basename}{id}.png). ID 0 is empty when emptyAllowed. */
    public int maxTileId() {
        return emptyAllowed ? tileCount - 1 : tileCount;
    }

    /** Image file for a tile ID, relative to {@link #tileDir} (or the assets root when tileDir is null). */
    public String tileImageName(int tileId) {
        return basename + tileId + ".png";
    }

    public static TilesetDef loadFromJson(File jsonFile) {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(new com.badlogic.gdx.files.FileHandle(jsonFile));

        String name = root.getString("name");
        String basename = root.getString("basename");
        int tileCount = root.getInt("tileCount");
        boolean emptyAllowed = root.getBoolean("emptyAllowed", true);
        boolean rotationsAllowed = root.getBoolean("rotationsAllowed", true);
        int tilePixelW = root.getInt("tilePixelW", 32);
        int tilePixelH = root.getInt("tilePixelH", 32);

        Constraints constraints = new Constraints();
        JsonValue ports = root.get("ports");
        if (ports != null) {
            for (JsonValue port = ports.child; port != null; port = port.next) {
                int tile = port.getInt("tile");
                String dirStr = port.getString("dir");
                int portId = port.getInt("port");

                DIR dir;
                switch (dirStr.toUpperCase()) {
                    case "N": dir = DIR.N; break;
                    case "S": dir = DIR.S; break;
                    case "E": dir = DIR.E; break;
                    case "W": dir = DIR.W; break;
                    default: throw new IllegalArgumentException("Unknown direction: " + dirStr);
                }
                constraints.addPort(tile, dir, portId);
            }
        }

        double[] weights = parseWeights(root.get("weights"), emptyAllowed ? tileCount : tileCount + 1);

        File parent = jsonFile.getAbsoluteFile().getParentFile();
        String tileDir = parent != null ? parent.getPath() : null;
        TilesetDef def = new TilesetDef(name, basename, tileCount, emptyAllowed, rotationsAllowed,
                tilePixelW, tilePixelH, constraints, tileDir, weights);
        parseTags(root.get("tags"), def);
        return def;
    }

    /** {@code "tags": {"walkable": [0, 2, 3], "water": [5]}} — tag name to tile IDs. */
    private static void parseTags(JsonValue node, TilesetDef def) {
        if (node == null) return;
        if (!node.isObject()) throw new IllegalArgumentException("tags must be an object of tag -> [tile IDs]");
        for (JsonValue t = node.child; t != null; t = t.next) {
            if (!t.isArray()) throw new IllegalArgumentException("tags." + t.name + " must be an array of tile IDs");
            int[] ids = new int[t.size];
            int i = 0;
            for (JsonValue v = t.child; v != null; v = v.next) ids[i++] = v.asInt();
            def.tag(t.name, ids);
        }
    }

    /** Copy with a different image location / size; constraints, weights and tags are shared. */
    public TilesetDef withImages(String newTileDir, String newBasename, int newTileW, int newTileH) {
        TilesetDef copy = new TilesetDef(name, newBasename, tileCount, emptyAllowed, rotationsAllowed,
                newTileW, newTileH, constraints, newTileDir, weights);
        copy.tags.putAll(tags);
        return copy;
    }

    /**
     * Accepts either an array indexed by tile ID ({@code [1, 1, 0.2]}) or an
     * object keyed by tile ID ({@code {"3": 0.2}}). Unlisted tiles get weight 1.
     */
    private static double[] parseWeights(JsonValue node, int size) {
        if (node == null) return null;
        double[] out = new double[size];
        java.util.Arrays.fill(out, 1.0);
        if (node.isArray()) {
            int i = 0;
            for (JsonValue v = node.child; v != null; v = v.next, i++) {
                if (i >= size) throw new IllegalArgumentException("weights has more entries than tile IDs (" + size + ")");
                out[i] = v.asDouble();
            }
        } else if (node.isObject()) {
            for (JsonValue v = node.child; v != null; v = v.next) {
                int id;
                try {
                    id = Integer.parseInt(v.name);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("weights key is not a tile ID: " + v.name);
                }
                if (id < 0 || id >= size) throw new IllegalArgumentException("weights tile ID out of range: " + id);
                out[id] = v.asDouble();
            }
        } else {
            throw new IllegalArgumentException("weights must be an array or an object");
        }
        for (int i = 0; i < size; i++) {
            if (out[i] < 0) throw new IllegalArgumentException("weight for tile " + i + " is negative");
        }
        return out;
    }

    public static List<TilesetDef> allTilesets() {
        List<TilesetDef> list = new ArrayList<>();
        list.add(circuitTileset());
        list.add(crossTileset());
        list.add(pipeTileset());
        return list;
    }

    public static TilesetDef circuitTileset() {
        Constraints c = new Constraints();

        c.addPort(1, DIR.S, 1);
        c.addPort(1, DIR.E, 1);
        c.addPort(1, DIR.N, 1);
        c.addPort(1, DIR.W, 1);

        c.addPort(2, DIR.S, 1);
        c.addPort(2, DIR.N, 2);

        c.addPort(3, DIR.N, 2);

        c.addPort(4, DIR.N, 2);
        c.addPort(4, DIR.W, 2);

        c.addPort(5, DIR.S, 1);

        c.addPort(6, DIR.N, 2);
        c.addPort(6, DIR.W, 2);
        c.addPort(6, DIR.E, 2);

        c.addPort(7, DIR.N, 2);
        c.addPort(7, DIR.S, 2);

        c.addPort(8, DIR.N, 2);
        c.addPort(8, DIR.S, 2);

        return new TilesetDef("Circuit", "circ", 9, true, true, 32, 32, c);
    }

    public static TilesetDef crossTileset() {
        Constraints c = new Constraints();

        // cross1: vertical bar - ports N and S
        c.addPort(1, DIR.N, 1);
        c.addPort(1, DIR.S, 1);

        // cross2: full cross - ports on all 4 sides
        c.addPort(2, DIR.N, 1);
        c.addPort(2, DIR.S, 1);
        c.addPort(2, DIR.E, 1);
        c.addPort(2, DIR.W, 1);

        // cross3: T-junction - ports S, E, W
        c.addPort(3, DIR.S, 1);
        c.addPort(3, DIR.E, 1);
        c.addPort(3, DIR.W, 1);

        // cross4: elbow - ports S and W
        c.addPort(4, DIR.S, 1);
        c.addPort(4, DIR.W, 1);

        return new TilesetDef("Cross", "cross", 5, true, true, 32, 32, c);
    }

    public static TilesetDef pipeTileset() {
        Constraints c = new Constraints();

        // pipe1: straight vertical - ports N and S
        c.addPort(1, DIR.N, 1);
        c.addPort(1, DIR.S, 1);

        // pipe2: elbow - ports E and S
        c.addPort(2, DIR.E, 1);
        c.addPort(2, DIR.S, 1);

        // pipe3: T-junction - ports S, E, W
        c.addPort(3, DIR.S, 1);
        c.addPort(3, DIR.E, 1);
        c.addPort(3, DIR.W, 1);

        // pipe4: cross - ports on all 4 sides
        c.addPort(4, DIR.N, 1);
        c.addPort(4, DIR.S, 1);
        c.addPort(4, DIR.E, 1);
        c.addPort(4, DIR.W, 1);

        return new TilesetDef("Pipe", "pipe", 5, true, true, 32, 32, c);
    }
}
