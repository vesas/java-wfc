package fi.vesas.wfc.gen;

import java.util.Random;

/**
 * Scatters N seed points across the grid and assigns each cell the region ID of
 * its nearest seed (squared Euclidean distance). Writes region IDs in
 * {@code [0, regionCount)} to the target {@link IntLayer}.
 *
 * Useful for biome tessellation — a later pass maps region ID → biome kind,
 * and a {@link WfcPass} can use that biome layer via
 * {@link WfcPass#constrainedBy(String, java.util.function.IntFunction)}.
 */
public class VoronoiRegionsPass implements GenPass {

    private final String layerName;
    private final int regionCount;

    public VoronoiRegionsPass(String layerName, int regionCount) {
        this.layerName = layerName;
        this.regionCount = Math.max(1, regionCount);
    }

    @Override
    public void apply(GenContext ctx) {

        Random local = new Random(ctx.getRandom().nextLong());
        int w = ctx.getWidth();
        int h = ctx.getHeight();

        int[] seedX = new int[regionCount];
        int[] seedY = new int[regionCount];
        for (int i = 0; i < regionCount; i++) {
            seedX[i] = local.nextInt(w);
            seedY[i] = local.nextInt(h);
        }

        IntLayer out = ctx.intLayer(layerName);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int nearest = 0;
                long best = Long.MAX_VALUE;
                for (int i = 0; i < regionCount; i++) {
                    long dx = x - seedX[i];
                    long dy = y - seedY[i];
                    long d2 = dx * dx + dy * dy;
                    if (d2 < best) {
                        best = d2;
                        nearest = i;
                    }
                }
                out.set(x, y, nearest);
            }
        }
    }
}
