package fi.vesas.wfc.gen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Shared state for a generation pipeline run. Owns the grid dimensions, the
 * seeded RNG that every pass derives from, the set of named layers, and the
 * cancellation flag.
 *
 * Passes read their inputs and write their outputs through the typed layer
 * accessors. Accessor semantics are "get or create": calling
 * {@link #intLayer(String)} creates the layer if it doesn't exist; calling it
 * again returns the same instance. A name collision with a different layer
 * type throws.
 */
public class GenContext {

    private final int width;
    private final int height;
    private final long seed;
    private final Random random;
    private final Map<String, Layer> layers = new LinkedHashMap<>();

    private volatile boolean cancelled = false;

    public GenContext(int width, int height, long seed) {
        this.width = width;
        this.height = height;
        this.seed = seed;
        this.random = new Random(seed);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getSeed() {
        return seed;
    }

    public Random getRandom() {
        return random;
    }

    public boolean hasLayer(String name) {
        return layers.containsKey(name);
    }

    public IntLayer intLayer(String name) {
        Layer existing = layers.get(name);
        if (existing == null) {
            IntLayer layer = new IntLayer(name, width, height);
            layers.put(name, layer);
            return layer;
        }
        if (existing instanceof IntLayer) {
            return (IntLayer) existing;
        }
        throw new IllegalStateException("Layer '" + name + "' exists but is "
                + existing.getClass().getSimpleName() + ", not IntLayer");
    }

    public FloatLayer floatLayer(String name) {
        Layer existing = layers.get(name);
        if (existing == null) {
            FloatLayer layer = new FloatLayer(name, width, height);
            layers.put(name, layer);
            return layer;
        }
        if (existing instanceof FloatLayer) {
            return (FloatLayer) existing;
        }
        throw new IllegalStateException("Layer '" + name + "' exists but is "
                + existing.getClass().getSimpleName() + ", not FloatLayer");
    }

    public BoolLayer boolLayer(String name) {
        Layer existing = layers.get(name);
        if (existing == null) {
            BoolLayer layer = new BoolLayer(name, width, height);
            layers.put(name, layer);
            return layer;
        }
        if (existing instanceof BoolLayer) {
            return (BoolLayer) existing;
        }
        throw new IllegalStateException("Layer '" + name + "' exists but is "
                + existing.getClass().getSimpleName() + ", not BoolLayer");
    }

    /**
     * Signal cancellation. Long-running passes should poll {@link #isCancelled()}
     * at safe checkpoints and return early. {@link GenPipeline} checks this flag
     * between passes.
     */
    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
