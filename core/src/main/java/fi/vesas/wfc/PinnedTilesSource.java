package fi.vesas.wfc;

import java.util.HashMap;
import java.util.Map;

/**
 * An {@link AllowedTileSource} that pins specific tile IDs to specific cells.
 * Game code uses this to force cells to a known tile (player spawn, stairs,
 * mandatory room anchors) before running the solver.
 *
 * Pinning multiple candidates at a cell is supported — the solver will pick
 * one of the pinned IDs, narrowed further by port compatibility with neighbors.
 *
 * Rotation is intentionally not controlled here: the solver still chooses the
 * rotation during observation (tiled mode) or propagation (after a neighbor
 * collapses). If a specific rotation is required, call
 * {@link SimpleWFC#setRotationsAt(int, int, int...)} directly on the pinned
 * cell after applying this source.
 */
public class PinnedTilesSource implements AllowedTileSource {

    /** Receives each pinned cell and its allowed tile IDs. */
    public interface Visitor {
        void visit(int x, int y, int[] tileIds);
    }

    private final Map<Long, int[]> pins = new HashMap<>();

    /** Pin one tile ID to cell (x, y). */
    public void pin(int x, int y, int tileId) {
        pins.put(key(x, y), new int[]{tileId});
    }

    /** Pin a set of candidate tile IDs to cell (x, y). */
    public void pin(int x, int y, int... tileIds) {
        pins.put(key(x, y), tileIds.clone());
    }

    public void clear() {
        pins.clear();
    }

    public int size() {
        return pins.size();
    }

    public void forEach(Visitor v) {
        for (Map.Entry<Long, int[]> e : pins.entrySet()) {
            long k = e.getKey();
            v.visit((int) (k >> 32), (int) (k & 0xFFFFFFFFL), e.getValue());
        }
    }

    @Override
    public int[] allowedTileIds(int x, int y) {
        return pins.get(key(x, y));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
