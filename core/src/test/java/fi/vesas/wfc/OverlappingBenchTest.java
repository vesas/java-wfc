package fi.vesas.wfc;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/**
 * Performance benchmarks for OverlappingWFC.
 */
public class OverlappingBenchTest {

    private BufferedImage makeNoise(int w, int h, int numColors, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.util.Random rng = new java.util.Random(seed);
        int[] palette = new int[numColors];
        for (int i = 0; i < numColors; i++)
            palette[i] = 0xFF000000 | rng.nextInt(0xFFFFFF);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, palette[rng.nextInt(numColors)]);
        return img;
    }

    private BufferedImage makeGradient(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, 0xFF000000 | ((x * 255 / w) << 16) | ((y * 255 / h) << 8) | ((x + y) * 128 / (w + h)));
        return img;
    }

    @Test
    public void benchStepByStep_noise8col() {
        BufferedImage sample = makeNoise(16, 16, 8, 99);
        int runs = 20;
        int totalSteps = 0, totalBt = 0;

        // Warmup
        for (int i = 0; i < 5; i++) {
            OverlappingWFC wfc = new OverlappingWFC(sample, 2, 12, 12, 1);
            wfc.setSeed(i);
            while (!wfc.isFinished() && !wfc.isContradiction()) wfc.runOneRound();
        }

        long t0 = System.nanoTime();
        for (int seed = 0; seed < runs; seed++) {
            OverlappingWFC wfc = new OverlappingWFC(sample, 2, 12, 12, 1);
            wfc.setSeed(seed + 100);
            int steps = 0;
            while (!wfc.isFinished() && !wfc.isContradiction() && steps < 5000) {
                wfc.runOneRound();
                steps++;
            }
            totalSteps += steps;
            totalBt += wfc.getBacktrackCount();
        }
        long elapsed = System.nanoTime() - t0;

        System.out.println("=== noise8col N=2 12x12, " + runs + " runs ===");
        System.out.println("Total time: " + (elapsed / 1_000_000) + " ms");
        System.out.println("Avg per run: " + (elapsed / runs / 1_000_000) + " ms");
        System.out.println("Total steps: " + totalSteps + ", backtracks: " + totalBt);
    }

    @Test
    public void benchStepByStep_gradient_large() {
        BufferedImage sample = makeGradient(32, 32);

        // Warmup
        for (int i = 0; i < 3; i++) {
            OverlappingWFC wfc = new OverlappingWFC(sample, 3, 48, 48, 1);
            wfc.setSeed(i);
            wfc.run();
        }

        int runs = 10;
        long t0 = System.nanoTime();
        for (int seed = 0; seed < runs; seed++) {
            OverlappingWFC wfc = new OverlappingWFC(sample, 3, 48, 48, 1);
            wfc.setSeed(seed + 100);
            wfc.run();
        }
        long elapsed = System.nanoTime() - t0;

        System.out.println("=== gradient 32x32 N=3 -> 48x48, " + runs + " runs ===");
        System.out.println("Total time: " + (elapsed / 1_000_000) + " ms");
        System.out.println("Avg per run: " + (elapsed / runs / 1_000_000) + " ms");
    }

    @Test
    public void benchStepByStep_heavyBacktrack() {
        // 8 colors, many patterns, high backtrack pressure
        BufferedImage sample = makeNoise(16, 16, 8, 42);
        int runs = 20;
        int totalBt = 0, finished = 0;

        long t0 = System.nanoTime();
        for (int seed = 0; seed < runs; seed++) {
            OverlappingWFC wfc = new OverlappingWFC(sample, 2, 16, 16, 1);
            wfc.setSeed(seed);
            int steps = 0;
            while (!wfc.isFinished() && !wfc.isContradiction() && steps < 5000) {
                wfc.runOneRound();
                steps++;
            }
            totalBt += wfc.getBacktrackCount();
            if (wfc.isFinished()) finished++;
        }
        long elapsed = System.nanoTime() - t0;

        System.out.println("=== heavy backtrack: noise8col N=2 16x16, " + runs + " runs ===");
        System.out.println("Total time: " + (elapsed / 1_000_000) + " ms");
        System.out.println("Avg per run: " + (elapsed / runs / 1_000_000) + " ms");
        System.out.println("Finished: " + finished + "/" + runs + ", total backtracks: " + totalBt);
    }
}
