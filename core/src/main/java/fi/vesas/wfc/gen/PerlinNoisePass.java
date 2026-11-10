package fi.vesas.wfc.gen;

import java.util.Random;

/**
 * Fills a {@link FloatLayer} with 2D Perlin noise, optionally stacked as
 * fractal Brownian motion (FBM) over multiple octaves. Output is in
 * approximately [-1, 1].
 *
 * Derives a local RNG from {@link GenContext#getRandom()} to keep pipeline
 * determinism intact across reorderings of unrelated passes.
 */
public class PerlinNoisePass implements GenPass {

    private final String layerName;
    private final float scale;
    private final int octaves;
    private final float persistence;

    public PerlinNoisePass(String layerName, float scale) {
        this(layerName, scale, 1, 0.5f);
    }

    public PerlinNoisePass(String layerName, float scale, int octaves, float persistence) {
        this.layerName = layerName;
        this.scale = scale;
        this.octaves = Math.max(1, octaves);
        this.persistence = persistence;
    }

    @Override
    public void apply(GenContext ctx) {

        Random local = new Random(ctx.getRandom().nextLong());
        int[] p = buildPermutation(local);

        FloatLayer out = ctx.floatLayer(layerName);
        int w = ctx.getWidth();
        int h = ctx.getHeight();

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float value = 0f;
                float amplitude = 1f;
                float frequency = scale;
                float norm = 0f;
                for (int i = 0; i < octaves; i++) {
                    value += amplitude * noise2D(x * frequency, y * frequency, p);
                    norm += amplitude;
                    amplitude *= persistence;
                    frequency *= 2f;
                }
                out.set(x, y, value / norm);
            }
        }
    }

    private static int[] buildPermutation(Random r) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        int[] perm = new int[512];
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        return perm;
    }

    private static float noise2D(float x, float y, int[] p) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);
        float u = fade(xf);
        float v = fade(yf);
        int aa = p[p[xi] + yi];
        int ab = p[p[xi] + yi + 1];
        int ba = p[p[xi + 1] + yi];
        int bb = p[p[xi + 1] + yi + 1];
        float x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        float x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    private static float grad(int hash, float x, float y) {
        int h = hash & 3;
        float u = h < 2 ? x : y;
        float v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
