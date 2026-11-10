package fi.vesas.wfc;

/**
 * Adapts a {@link ZoneConfig} (row-band zones stacked from the bottom of the
 * grid upward) into an {@link AllowedTileSource} the solver consumes directly.
 *
 * ZoneConfig remains the authoring format; this class is the integration seam.
 */
public class ZoneTileSource implements AllowedTileSource {

    private final ZoneConfig zones;
    private final int gridHeight;

    public ZoneTileSource(ZoneConfig zones, int gridHeight) {
        this.zones = zones;
        this.gridHeight = gridHeight;
    }

    @Override
    public int[] allowedTileIds(int x, int y) {
        return zones.allowedTilesForRow(y, gridHeight);
    }
}
