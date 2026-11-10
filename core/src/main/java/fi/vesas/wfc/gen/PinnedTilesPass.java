package fi.vesas.wfc.gen;

import fi.vesas.wfc.AllowedTileSource;
import fi.vesas.wfc.PinnedTilesSource;

/**
 * Carries game-authored tile pins into the pipeline. Game code calls
 * {@link #pin(int, int, int)} before running the pipeline to stake out
 * mandatory cells (stairs, spawns, required room anchors).
 *
 * When applied, the pass writes each pin's first tile ID into the target
 * {@link IntLayer} so the pre-WFC grid is inspectable. Downstream
 * {@link WfcPass} instances consume pins through {@link #asSource()} to force
 * those cells during solving.
 */
public class PinnedTilesPass implements GenPass {

    private final String layerName;
    private final PinnedTilesSource source = new PinnedTilesSource();

    public PinnedTilesPass(String layerName) {
        this.layerName = layerName;
    }

    public PinnedTilesPass pin(int x, int y, int tileId) {
        source.pin(x, y, tileId);
        return this;
    }

    public PinnedTilesPass pin(int x, int y, int... tileIds) {
        source.pin(x, y, tileIds);
        return this;
    }

    public AllowedTileSource asSource() {
        return source;
    }

    @Override
    public void apply(GenContext ctx) {
        if (layerName == null) return;
        IntLayer layer = ctx.intLayer(layerName);
        source.forEach((x, y, tileIds) -> {
            if (tileIds.length > 0) {
                layer.set(x, y, tileIds[0]);
            }
        });
    }
}
