package fi.vesas.wfc.gen;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

import fi.vesas.wfc.AllowedTileSource;
import fi.vesas.wfc.Constraints;
import fi.vesas.wfc.SimpleWFC;

/**
 * Runs {@link SimpleWFC} as a pipeline step. Writes solved tile IDs into a
 * named {@link IntLayer}, optionally writing rotation bitmasks to a second
 * layer.
 *
 * Configure via fluent builder methods. Two constraint inputs are supported:
 * <ul>
 *   <li>{@link #constrainedBy(String, IntFunction)} — read an upstream IntLayer
 *       (biome labels, region IDs) and map each cell's value to the tile IDs
 *       allowed there. This is the biome-aware WFC case.</li>
 *   <li>{@link #withAllowedTileSource(AllowedTileSource)} — plug in any
 *       {@link AllowedTileSource} directly (e.g. a {@code PinnedTilesSource}).</li>
 * </ul>
 * When both are set, their allowed sets are intersected per cell.
 *
 * Throws {@link WfcContradictionException} if the solver cannot find a solution;
 * game code should catch and retry with a different seed.
 */
public class WfcPass implements GenPass {

    private final String tileLayerName;
    private final int tileCount;
    private final Constraints constraints;

    private boolean emptyAllowed = true;
    private boolean rotationsAllowed = true;
    private boolean tilingHorizontal = false;
    private boolean tilingVertical = false;

    private String controlLayerName;
    private IntFunction<int[]> controlMapper;
    private AllowedTileSource explicitSource;
    private String rotationLayerName;

    public WfcPass(String tileLayerName, int tileCount, Constraints constraints) {
        this.tileLayerName = tileLayerName;
        this.tileCount = tileCount;
        this.constraints = constraints;
    }

    public WfcPass emptyAllowed(boolean b) {
        this.emptyAllowed = b;
        return this;
    }

    public WfcPass rotationsAllowed(boolean b) {
        this.rotationsAllowed = b;
        return this;
    }

    public WfcPass tiling(boolean horizontal, boolean vertical) {
        this.tilingHorizontal = horizontal;
        this.tilingVertical = vertical;
        return this;
    }

    /**
     * Constrain each cell's allowed tile IDs based on the value in an upstream
     * IntLayer. The layer must already exist in the context when this pass runs.
     */
    public WfcPass constrainedBy(String controlLayerName, IntFunction<int[]> mapper) {
        this.controlLayerName = controlLayerName;
        this.controlMapper = mapper;
        return this;
    }

    public WfcPass withAllowedTileSource(AllowedTileSource source) {
        this.explicitSource = source;
        return this;
    }

    /** Also write rotation bitmasks (1, 2, 4, 8) to a second layer. */
    public WfcPass writingRotationsTo(String rotationLayerName) {
        this.rotationLayerName = rotationLayerName;
        return this;
    }

    @Override
    public void apply(GenContext ctx) {

        SimpleWFC wfc = new SimpleWFC(ctx.getWidth(), ctx.getHeight(),
                tileCount, emptyAllowed, rotationsAllowed);
        wfc.setConstraints(constraints);
        wfc.setSeed(ctx.getRandom().nextInt());
        wfc.setTilingHorizontal(tilingHorizontal);
        wfc.setTilingVertical(tilingVertical);
        wfc.setCancellationCheck(ctx::isCancelled);

        AllowedTileSource effective = buildEffectiveSource(ctx);
        if (effective != null) {
            wfc.setAllowedTileSource(effective);
        }

        if (!wfc.isContradiction()) {
            wfc.run();
        }

        if (ctx.isCancelled()) {
            return;
        }

        if (wfc.isContradiction()) {
            throw new WfcContradictionException(
                    "WfcPass '" + tileLayerName + "' contradicted; try a different seed");
        }

        writeOutputs(ctx, wfc);
    }

    private AllowedTileSource buildEffectiveSource(GenContext ctx) {

        AllowedTileSource biomeSource = null;
        if (controlLayerName != null) {
            IntLayer control = ctx.intLayer(controlLayerName);
            IntFunction<int[]> mapper = controlMapper;
            biomeSource = (x, y) -> mapper.apply(control.get(x, y));
        }

        if (biomeSource != null && explicitSource != null) {
            AllowedTileSource a = biomeSource;
            AllowedTileSource b = explicitSource;
            return (x, y) -> intersect(a.allowedTileIds(x, y), b.allowedTileIds(x, y));
        }
        if (biomeSource != null) {
            return biomeSource;
        }
        return explicitSource;
    }

    private static int[] intersect(int[] a, int[] b) {
        if (a == null) return b;
        if (b == null) return a;
        Set<Integer> set = new HashSet<>(a.length * 2);
        for (int id : a) set.add(id);
        int[] out = new int[Math.min(a.length, b.length)];
        int n = 0;
        for (int id : b) {
            if (set.contains(id)) out[n++] = id;
        }
        return Arrays.copyOf(out, n);
    }

    private void writeOutputs(GenContext ctx, SimpleWFC wfc) {
        IntLayer tileOut = ctx.intLayer(tileLayerName);
        IntLayer rotOut = rotationLayerName != null ? ctx.intLayer(rotationLayerName) : null;
        for (int x = 0; x < ctx.getWidth(); x++) {
            for (int y = 0; y < ctx.getHeight(); y++) {
                int[] tiles = wfc.getGrid(x, y);
                int[] rots = wfc.getRots(x, y);
                tileOut.set(x, y, tiles.length > 0 ? tiles[0] : 0);
                if (rotOut != null) {
                    rotOut.set(x, y, rots.length > 0 ? rots[0] : 0);
                }
            }
        }
    }
}
