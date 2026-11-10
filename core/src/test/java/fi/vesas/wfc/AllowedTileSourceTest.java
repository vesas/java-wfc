package fi.vesas.wfc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AllowedTileSourceTest {

    /**
     * Baseline: with no source (null), behavior is identical to before the seam existed.
     * Same seed → same grid.
     */
    @Test
    public void nullSourceProducesIdenticalResultToUnsetSource() {

        SimpleWFC a = new SimpleWFC(4, 4, 3, true, false);
        a.setConstraints(new Constraints());
        a.setSeed(42);
        a.run();

        SimpleWFC b = new SimpleWFC(4, 4, 3, true, false);
        b.setConstraints(new Constraints());
        b.setAllowedTileSource(null);
        b.setSeed(42);
        b.run();

        assertFalse(a.isContradiction());
        assertFalse(b.isContradiction());

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                assertArrayEquals(a.getGrid(x, y), b.getGrid(x, y),
                        "tile mismatch at " + x + "," + y);
                assertArrayEquals(a.getRots(x, y), b.getRots(x, y),
                        "rot mismatch at " + x + "," + y);
            }
        }
    }

    /**
     * Source narrows tile IDs per cell at set-time. A cell restricted to a single
     * ID becomes a singleton in idGrid immediately.
     */
    @Test
    public void pinnedTilesSourceNarrowsToSingleId() {

        SimpleWFC wfc = new SimpleWFC(3, 3, 3, true, false);
        wfc.setConstraints(new Constraints());

        PinnedTilesSource pins = new PinnedTilesSource();
        pins.pin(1, 1, 2);
        wfc.setAllowedTileSource(pins);

        // The pinned cell now only holds tile 2
        assertArrayEquals(new int[]{2}, wfc.getGrid(1, 1));

        // Other cells are untouched (still 3 candidates)
        assertEquals(3, wfc.getGrid(0, 0).length);
        assertEquals(3, wfc.getGrid(2, 2).length);

        // Solve and confirm pinned cell retained its tile
        wfc.setSeed(7);
        wfc.run();
        assertFalse(wfc.isContradiction());
        assertArrayEquals(new int[]{2}, wfc.getGrid(1, 1));
    }

    /**
     * Zone configuration, wrapped as a tile source, confines tile IDs to bands of rows.
     */
    @Test
    public void zoneTileSourceRestrictsByRow() {

        int width = 4;
        int height = 4;

        SimpleWFC wfc = new SimpleWFC(width, height, 4, true, false);
        wfc.setConstraints(new Constraints());

        ZoneConfig zones = new ZoneConfig();
        zones.enabled = true;
        // Bottom 2 rows: only tile IDs 0 and 1
        zones.zones.add(new ZoneConfig.Zone(2, new boolean[]{true, true, false, false}));
        // Top 2 rows: only tile IDs 2 and 3
        zones.zones.add(new ZoneConfig.Zone(2, new boolean[]{false, false, true, true}));

        assertTrue(zones.isValid(height));

        wfc.setAllowedTileSource(new ZoneTileSource(zones, height));

        // Bottom band: no tile id >= 2
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < 2; y++) {
                for (int id : wfc.getGrid(x, y)) {
                    assertTrue(id <= 1, "bottom band should only hold ids 0/1, got " + id);
                }
            }
        }

        // Top band: no tile id < 2
        for (int x = 0; x < width; x++) {
            for (int y = 2; y < 4; y++) {
                for (int id : wfc.getGrid(x, y)) {
                    assertTrue(id >= 2, "top band should only hold ids 2/3, got " + id);
                }
            }
        }
    }

    /**
     * An empty allowed set at any cell means "nothing is allowed here" and must flip
     * the contradiction flag so the solver doesn't silently produce a garbage result.
     */
    @Test
    public void emptyAllowedSetCausesContradiction() {

        SimpleWFC wfc = new SimpleWFC(2, 2, 3, true, false);
        wfc.setConstraints(new Constraints());

        PinnedTilesSource pins = new PinnedTilesSource();
        pins.pin(0, 0);  // empty varargs → empty allowed set
        wfc.setAllowedTileSource(pins);

        assertTrue(wfc.isContradiction());
        assertEquals(0, wfc.getGrid(0, 0).length);
    }
}
