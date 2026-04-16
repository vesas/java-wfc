package fi.vesas.wfc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    public TilesetDef(String name, String basename, int tileCount,
                      boolean emptyAllowed, boolean rotationsAllowed,
                      int tilePixelW, int tilePixelH, Constraints constraints) {
        this(name, basename, tileCount, emptyAllowed, rotationsAllowed, tilePixelW, tilePixelH, constraints, null);
    }

    public TilesetDef(String name, String basename, int tileCount,
                      boolean emptyAllowed, boolean rotationsAllowed,
                      int tilePixelW, int tilePixelH, Constraints constraints, String tileDir) {
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

        String tileDir = jsonFile.getParent();
        return new TilesetDef(name, basename, tileCount, emptyAllowed, rotationsAllowed,
                tilePixelW, tilePixelH, constraints, tileDir);
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

        // cross1: full cross - ports on all 4 sides
        c.addPort(1, DIR.N, 1);
        c.addPort(1, DIR.S, 1);
        c.addPort(1, DIR.E, 1);
        c.addPort(1, DIR.W, 1);

        // cross2: vertical bar - ports N and S
        c.addPort(2, DIR.N, 1);
        c.addPort(2, DIR.S, 1);

        // cross3: T-junction - ports N, E, W
        c.addPort(3, DIR.N, 1);
        c.addPort(3, DIR.E, 1);
        c.addPort(3, DIR.W, 1);

        // cross4: end cap - port S only
        c.addPort(4, DIR.S, 1);

        return new TilesetDef("Cross", "cross", 5, true, true, 32, 32, c);
    }

    public static TilesetDef pipeTileset() {
        Constraints c = new Constraints();

        // pipe1: straight vertical - ports N and S
        c.addPort(1, DIR.N, 1);
        c.addPort(1, DIR.S, 1);

        // pipe2: elbow - ports N and E
        c.addPort(2, DIR.N, 1);
        c.addPort(2, DIR.E, 1);

        // pipe3: T-junction - ports N, E, W
        c.addPort(3, DIR.N, 1);
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
