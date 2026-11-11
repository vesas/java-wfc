package fi.vesas.wfc.gen;

import java.util.function.IntPredicate;

import fi.vesas.wfc.TilesetDef;

/**
 * Turns a solved tile layer into a {@link BoolLayer} mask: {@code true} where
 * the cell's tile ID matches. Bridges WFC output to mask-based passes such as
 * {@link ReachabilityCheckPass} and to game logic (collision, spawn areas).
 *
 * Typical use:
 * <pre>
 * pipeline
 *     .add(new WfcPass("terrain", def.tileCount, def.constraints))
 *     .add(TilePropertyPass.fromTag(def, "terrain", "walkable", "walkable"))
 *     .add(new ReachabilityCheckPass("walkable", 0, 0));
 * </pre>
 */
public class TilePropertyPass implements GenPass {

    private final String tileLayerName;
    private final String maskLayerName;
    private final IntPredicate matches;

    /** Generic form: {@code matches} tests a tile ID. */
    public TilePropertyPass(String tileLayerName, String maskLayerName, IntPredicate matches) {
        this.tileLayerName = tileLayerName;
        this.maskLayerName = maskLayerName;
        this.matches = matches;
    }

    /**
     * Mask of cells whose tile carries {@code tag} in the tileset definition.
     * Throws when the tileset does not define the tag, so a typo fails at
     * pipeline build time instead of producing an all-false mask.
     */
    public static TilePropertyPass fromTag(TilesetDef def, String tileLayerName, String tag, String maskLayerName) {
        if (!def.hasTagDefined(tag)) {
            throw new IllegalArgumentException("Tileset '" + def.name + "' has no tag '" + tag
                    + "' (defined: " + def.tagNames() + ")");
        }
        return new TilePropertyPass(tileLayerName, maskLayerName, id -> def.hasTag(id, tag));
    }

    /** Mask of cells whose tile ID is one of {@code tileIds}. */
    public static TilePropertyPass fromIds(String tileLayerName, String maskLayerName, int... tileIds) {
        int max = -1;
        for (int id : tileIds) max = Math.max(max, id);
        boolean[] set = new boolean[max + 1];
        for (int id : tileIds) if (id >= 0) set[id] = true;
        return new TilePropertyPass(tileLayerName, maskLayerName, id -> id >= 0 && id < set.length && set[id]);
    }

    @Override
    public void apply(GenContext ctx) {
        if (!ctx.hasLayer(tileLayerName)) {
            throw new IllegalStateException("TilePropertyPass: tile layer '" + tileLayerName + "' does not exist");
        }
        IntLayer tiles = ctx.intLayer(tileLayerName);
        BoolLayer mask = ctx.boolLayer(maskLayerName);
        int w = ctx.getWidth();
        int h = ctx.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                mask.set(x, y, matches.test(tiles.get(x, y)));
            }
        }
    }
}
