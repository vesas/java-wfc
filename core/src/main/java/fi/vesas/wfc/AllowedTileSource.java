package fi.vesas.wfc;

/**
 * Per-cell constraint on which tile IDs may appear. A runtime generation
 * pipeline uses this to feed upstream information (biome labels, region IDs,
 * game-authored pins) into {@link SimpleWFC} without the solver needing to
 * know what those concepts mean.
 *
 * Returning {@code null} means "no restriction for this cell" — the cell keeps
 * every tile ID it currently holds. Returning an empty array means "nothing is
 * allowed here", which will force a contradiction when the source is applied.
 */
public interface AllowedTileSource {

    /**
     * @param x grid x, in [0, width)
     * @param y grid y, in [0, height)
     * @return tile IDs allowed at this cell, or {@code null} for no restriction
     */
    int[] allowedTileIds(int x, int y);
}
