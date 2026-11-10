package fi.vesas.wfc.gen;

/**
 * Converts a {@link FloatLayer} to a {@link BoolLayer} by thresholding:
 * output is {@code true} where the float value is {@code >= threshold}.
 */
public class ThresholdPass implements GenPass {

    private final String fromLayer;
    private final String toLayer;
    private final float threshold;

    public ThresholdPass(String fromLayer, String toLayer, float threshold) {
        this.fromLayer = fromLayer;
        this.toLayer = toLayer;
        this.threshold = threshold;
    }

    @Override
    public void apply(GenContext ctx) {

        FloatLayer src = ctx.floatLayer(fromLayer);
        BoolLayer dst = ctx.boolLayer(toLayer);

        int w = ctx.getWidth();
        int h = ctx.getHeight();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                dst.set(x, y, src.get(x, y) >= threshold);
            }
        }
    }
}
