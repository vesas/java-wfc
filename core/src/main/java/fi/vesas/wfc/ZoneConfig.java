package fi.vesas.wfc;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal row-band zone configuration for tiled-mode WFC.
 *
 * Each Zone occupies a contiguous stack of rows (measured from the bottom of the grid upward)
 * and restricts which tile IDs may appear in that band. WFC propagation handles the
 * transitions at zone boundaries automatically through the existing port constraints.
 */
public class ZoneConfig {

    public static class Zone {
        public int rowCount;
        public boolean[] allowedTiles;

        public Zone(int rowCount, boolean[] allowedTiles) {
            this.rowCount = rowCount;
            this.allowedTiles = allowedTiles;
        }

        public int[] allowedTileIds() {
            int count = 0;
            for (boolean b : allowedTiles) if (b) count++;
            int[] out = new int[count];
            int idx = 0;
            for (int i = 0; i < allowedTiles.length; i++) {
                if (allowedTiles[i]) out[idx++] = i;
            }
            return out;
        }

        public int allowedCount() {
            int c = 0;
            for (boolean b : allowedTiles) if (b) c++;
            return c;
        }
    }

    public boolean enabled = false;
    public final List<Zone> zones = new ArrayList<>();

    public int totalRows() {
        int sum = 0;
        for (Zone z : zones) sum += Math.max(0, z.rowCount);
        return sum;
    }

    public boolean isValid(int gridHeight) {
        if (zones.isEmpty()) return false;
        for (Zone z : zones) {
            if (z.rowCount <= 0) return false;
            if (z.allowedCount() == 0) return false;
        }
        return totalRows() == gridHeight;
    }

    /**
     * Returns the tile IDs allowed for the given row (y coordinate in the grid),
     * or null when zones are disabled or no zone covers this row.
     *
     * Zones stack from the bottom (y=0) upward in the order they appear in {@link #zones}.
     */
    public int[] allowedTilesForRow(int y, int gridHeight) {
        if (!enabled) return null;
        if (zones.isEmpty()) return null;
        int rowCursor = 0;
        for (Zone z : zones) {
            int end = rowCursor + Math.max(0, z.rowCount);
            if (y >= rowCursor && y < end) {
                return z.allowedTileIds();
            }
            rowCursor = end;
        }
        return null;
    }

    public void clear() {
        enabled = false;
        zones.clear();
    }
}
