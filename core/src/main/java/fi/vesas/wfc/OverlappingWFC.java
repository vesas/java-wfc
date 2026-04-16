package fi.vesas.wfc;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

public class OverlappingWFC {

    private static class Pattern {
        int[] pixels;
        Pattern(int[] pixels) { this.pixels = pixels; }
        @Override public int hashCode() { return Arrays.hashCode(pixels); }
        @Override public boolean equals(Object o) {
            return this == o || (o instanceof Pattern && Arrays.equals(pixels, ((Pattern) o).pixels));
        }
    }

    // Snapshot stores wave + compatible + entropy caches for O(1) restore
    private static class Snapshot {
        boolean[][] wave;
        int[] compatible;
        int[] possibleCount;
        double[] sumsOfWeights;
        double[] sumsOfWeightLogWeights;
        double[] entropies;
        int cell;
        int[] remainingChoices;
        int choiceCount;

        Snapshot(boolean[][] wave, int[] compatible, int[] possibleCount,
                 double[] sumsOfWeights, double[] sumsOfWeightLogWeights, double[] entropies,
                 int cell, int[] choices, int choiceCount) {
            // Deep copy wave
            this.wave = new boolean[wave.length][];
            for (int i = 0; i < wave.length; i++)
                this.wave[i] = Arrays.copyOf(wave[i], wave[i].length);
            this.compatible = Arrays.copyOf(compatible, compatible.length);
            this.possibleCount = Arrays.copyOf(possibleCount, possibleCount.length);
            this.sumsOfWeights = Arrays.copyOf(sumsOfWeights, sumsOfWeights.length);
            this.sumsOfWeightLogWeights = Arrays.copyOf(sumsOfWeightLogWeights, sumsOfWeightLogWeights.length);
            this.entropies = Arrays.copyOf(entropies, entropies.length);
            this.cell = cell;
            this.remainingChoices = Arrays.copyOf(choices, choiceCount);
            this.choiceCount = choiceCount;
        }
    }

    private static final int[] DX = { 0, 1, 0, -1 };
    private static final int[] DY = { -1, 0, 1, 0 };

    private int N;
    private int outputWidth;
    private int outputHeight;
    private int symmetry;

    private int patternCount;
    private int[][] patternPixels;
    private double[] frequencies;
    private double[] logFrequencies;

    // propagator[dir][pattern] = compatible pattern indices
    private int[][][] propagator;

    // Precomputed neighbor table: neighbors[cell * 4 + dir] = neighbor cell index, or -1
    private int[] neighbors;

    private boolean[][] wave;
    private int[] compatible;
    private int[] possibleCount;
    private double[] sumsOfWeights;
    private double[] sumsOfWeightLogWeights;
    private double[] entropies;

    private boolean tilingHorizontal = false;
    private boolean tilingVertical = false;
    private boolean finished = false;
    private boolean contradiction = false;
    private int backtrackCount = 0;

    private Random random = new Random();

    // Flat circular propagation queue — avoids int[] allocation per ban
    private int[] propQueueCells;
    private int[] propQueuePatterns;
    private int propQueueHead, propQueueTail, propQueueMask;

    // Backtrack stack — capped to limit memory
    private static final int MAX_BACKTRACK_DEPTH = 64;
    private Snapshot[] backtrackStack = new Snapshot[MAX_BACKTRACK_DEPTH];
    private int backtrackTop = 0;

    // Reusable buffer for observe
    private int[] observeBuf;

    // Tile-input mode
    private int inputTileW = 1;
    private int inputTileH = 1;
    private BufferedImage[] tileImages;

    private int sampleWidth;
    private int sampleHeight;
    private int[] samplePixels;

    public OverlappingWFC(BufferedImage sample, int N, int outputWidth, int outputHeight, int symmetry) {
        this(sample, N, outputWidth, outputHeight, symmetry, 1, 1);
    }

    public OverlappingWFC(BufferedImage sample, int N, int outputWidth, int outputHeight, int symmetry,
                           int inputTileW, int inputTileH) {
        this.N = N;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.symmetry = symmetry;
        this.inputTileW = inputTileW;
        this.inputTileH = inputTileH;

        if (inputTileW > 1 || inputTileH > 1) {
            sliceIntoTiles(sample);
        } else {
            this.sampleWidth = sample.getWidth();
            this.sampleHeight = sample.getHeight();
            this.samplePixels = sample.getRGB(0, 0, sampleWidth, sampleHeight, null, 0, sampleWidth);
        }

        long t0 = System.currentTimeMillis();
        extractPatterns();
        long t1 = System.currentTimeMillis();
        buildPropagator();
        long t2 = System.currentTimeMillis();
        buildNeighborTable();
        initPropQueue();
        initWave();
        long t3 = System.currentTimeMillis();

        System.out.println("Init: patterns=" + patternCount
            + (tileImages != null ? " uniqueTiles=" + tileImages.length : "")
            + " extract=" + (t1 - t0) + "ms"
            + " propagator=" + (t2 - t1) + "ms"
            + " wave=" + (t3 - t2) + "ms");
    }

    // ========== Tile slicing ==========

    private void sliceIntoTiles(BufferedImage sample) {
        int imgW = sample.getWidth();
        int imgH = sample.getHeight();
        int gridW = imgW / inputTileW;
        int gridH = imgH / inputTileH;

        Map<Long, Integer> tileHashToId = new HashMap<>();
        List<BufferedImage> palette = new ArrayList<>();
        int[] grid = new int[gridW * gridH];

        for (int ty = 0; ty < gridH; ty++) {
            for (int tx = 0; tx < gridW; tx++) {
                BufferedImage tile = sample.getSubimage(tx * inputTileW, ty * inputTileH, inputTileW, inputTileH);
                int[] tilePixels = tile.getRGB(0, 0, inputTileW, inputTileH, null, 0, inputTileW);

                long hash = Arrays.hashCode(tilePixels);
                Integer existingId = tileHashToId.get(hash);
                if (existingId != null) {
                    int[] existing = palette.get(existingId).getRGB(0, 0, inputTileW, inputTileH, null, 0, inputTileW);
                    if (Arrays.equals(tilePixels, existing)) {
                        grid[ty * gridW + tx] = existingId;
                        continue;
                    }
                    boolean found = false;
                    for (int i = 0; i < palette.size(); i++) {
                        int[] other = palette.get(i).getRGB(0, 0, inputTileW, inputTileH, null, 0, inputTileW);
                        if (Arrays.equals(tilePixels, other)) {
                            grid[ty * gridW + tx] = i;
                            found = true;
                            break;
                        }
                    }
                    if (found) continue;
                }

                int id = palette.size();
                tileHashToId.put(hash, id);
                BufferedImage copy = new BufferedImage(inputTileW, inputTileH, BufferedImage.TYPE_INT_ARGB);
                copy.setRGB(0, 0, inputTileW, inputTileH, tilePixels, 0, inputTileW);
                palette.add(copy);
                grid[ty * gridW + tx] = id;
            }
        }

        this.sampleWidth = gridW;
        this.sampleHeight = gridH;
        this.samplePixels = grid;
        this.tileImages = palette.toArray(new BufferedImage[0]);
    }

    // ========== Public API ==========

    public void setSeed(long seed) { this.random = new Random(seed); }
    public void setTilingHorizontal(boolean t) { this.tilingHorizontal = t; buildNeighborTable(); }
    public void setTilingVertical(boolean t) { this.tilingVertical = t; buildNeighborTable(); }
    public boolean isFinished() { return finished; }
    public boolean isContradiction() { return contradiction; }
    public int getBacktrackCount() { return backtrackCount; }
    public int getPatternCount() { return patternCount; }

    // ========== Pattern extraction ==========

    private int[] extractPatch(int startX, int startY) {
        int[] patch = new int[N * N];
        for (int dy = 0; dy < N; dy++)
            for (int dx = 0; dx < N; dx++)
                patch[dy * N + dx] = samplePixels[((startY + dy) % sampleHeight) * sampleWidth + (startX + dx) % sampleWidth];
        return patch;
    }

    private int[] rotatePattern(int[] p) {
        int[] r = new int[N * N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
                r[x * N + (N - 1 - y)] = p[y * N + x];
        return r;
    }

    private int[] reflectPattern(int[] p) {
        int[] r = new int[N * N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
                r[y * N + (N - 1 - x)] = p[y * N + x];
        return r;
    }

    private void extractPatterns() {
        Map<Pattern, Integer> freq = new HashMap<>();
        for (int y = 0; y < sampleHeight; y++) {
            for (int x = 0; x < sampleWidth; x++) {
                int[] base = extractPatch(x, y);
                addVariants(freq, base);
            }
        }

        patternCount = freq.size();
        patternPixels = new int[patternCount][];
        frequencies = new double[patternCount];
        logFrequencies = new double[patternCount];
        observeBuf = new int[patternCount];

        int idx = 0;
        for (Map.Entry<Pattern, Integer> e : freq.entrySet()) {
            patternPixels[idx] = e.getKey().pixels;
            frequencies[idx] = e.getValue();
            idx++;
        }
        for (int i = 0; i < patternCount; i++)
            logFrequencies[i] = Math.log(frequencies[i]);
    }

    private void addVariants(Map<Pattern, Integer> freq, int[] base) {
        freq.merge(new Pattern(base), 1, Integer::sum);
        if (symmetry >= 2)
            freq.merge(new Pattern(reflectPattern(base)), 1, Integer::sum);
        if (symmetry >= 4) {
            int[] r1 = rotatePattern(base), r2 = rotatePattern(r1), r3 = rotatePattern(r2);
            freq.merge(new Pattern(r1), 1, Integer::sum);
            freq.merge(new Pattern(r2), 1, Integer::sum);
            freq.merge(new Pattern(r3), 1, Integer::sum);
            if (symmetry >= 8) {
                int[] ref = reflectPattern(base);
                freq.merge(new Pattern(rotatePattern(ref)), 1, Integer::sum);
                freq.merge(new Pattern(rotatePattern(rotatePattern(ref))), 1, Integer::sum);
                freq.merge(new Pattern(rotatePattern(rotatePattern(rotatePattern(ref)))), 1, Integer::sum);
            }
        }
    }

    // ========== Propagator ==========

    private boolean overlapEquals(int[] a, int[] b, int dir) {
        switch (dir) {
            case 1: // E
                for (int y = 0; y < N; y++)
                    for (int x = 1; x < N; x++)
                        if (a[y * N + x] != b[y * N + (x - 1)]) return false;
                break;
            case 3: // W
                for (int y = 0; y < N; y++)
                    for (int x = 0; x < N - 1; x++)
                        if (a[y * N + x] != b[y * N + (x + 1)]) return false;
                break;
            case 2: // S
                for (int y = 1; y < N; y++)
                    for (int x = 0; x < N; x++)
                        if (a[y * N + x] != b[(y - 1) * N + x]) return false;
                break;
            case 0: // N
                for (int y = 0; y < N - 1; y++)
                    for (int x = 0; x < N; x++)
                        if (a[y * N + x] != b[(y + 1) * N + x]) return false;
                break;
        }
        return true;
    }

    private long overlapHash(int[] pattern, int dir, boolean source) {
        long h = 0;
        switch (dir) {
            case 1: // E
                for (int y = 0; y < N; y++)
                    for (int x = (source ? 1 : 0); x < (source ? N : N - 1); x++)
                        h = h * 31 + pattern[y * N + x];
                break;
            case 3: // W
                for (int y = 0; y < N; y++)
                    for (int x = (source ? 0 : 1); x < (source ? N - 1 : N); x++)
                        h = h * 31 + pattern[y * N + x];
                break;
            case 2: // S
                for (int y = (source ? 1 : 0); y < (source ? N : N - 1); y++)
                    for (int x = 0; x < N; x++)
                        h = h * 31 + pattern[y * N + x];
                break;
            case 0: // N
                for (int y = (source ? 0 : 1); y < (source ? N - 1 : N); y++)
                    for (int x = 0; x < N; x++)
                        h = h * 31 + pattern[y * N + x];
                break;
        }
        return h;
    }

    private void buildPropagator() {
        propagator = new int[4][patternCount][];
        for (int dir = 0; dir < 4; dir++) {
            Map<Long, List<Integer>> targetsByHash = new HashMap<>();
            for (int p = 0; p < patternCount; p++)
                targetsByHash.computeIfAbsent(overlapHash(patternPixels[p], dir, false), k -> new ArrayList<>()).add(p);

            for (int p1 = 0; p1 < patternCount; p1++) {
                long srcHash = overlapHash(patternPixels[p1], dir, true);
                List<Integer> cands = targetsByHash.get(srcHash);
                if (cands == null) { propagator[dir][p1] = new int[0]; continue; }
                int count = 0;
                int[] buf = new int[cands.size()];
                for (int p2 : cands)
                    if (overlapEquals(patternPixels[p1], patternPixels[p2], dir))
                        buf[count++] = p2;
                propagator[dir][p1] = Arrays.copyOf(buf, count);
            }
        }
    }

    // ========== Precomputed neighbor table ==========

    private void buildNeighborTable() {
        int totalCells = outputWidth * outputHeight;
        neighbors = new int[totalCells * 4];
        for (int cell = 0; cell < totalCells; cell++) {
            int cx = cell % outputWidth, cy = cell / outputWidth;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + DX[dir], ny = cy + DY[dir];
                if (nx < 0) nx = tilingHorizontal ? nx + outputWidth : -1;
                else if (nx >= outputWidth) nx = tilingHorizontal ? nx - outputWidth : -1;
                if (ny < 0) ny = tilingVertical ? ny + outputHeight : -1;
                else if (ny >= outputHeight) ny = tilingVertical ? ny - outputHeight : -1;
                neighbors[cell * 4 + dir] = (nx < 0 || ny < 0) ? -1 : ny * outputWidth + nx;
            }
        }
    }

    // ========== Flat propagation queue ==========

    private void initPropQueue() {
        // Power-of-2 size for fast masking
        int cap = Integer.highestOneBit(Math.max(outputWidth * outputHeight * patternCount, 256)) << 1;
        propQueueCells = new int[cap];
        propQueuePatterns = new int[cap];
        propQueueMask = cap - 1;
        propQueueHead = propQueueTail = 0;
    }

    private void propQueuePush(int cell, int pattern) {
        propQueueCells[propQueueTail & propQueueMask] = cell;
        propQueuePatterns[propQueueTail & propQueueMask] = pattern;
        propQueueTail++;
    }

    private boolean propQueueEmpty() { return propQueueHead == propQueueTail; }

    private void propQueueClear() { propQueueHead = propQueueTail = 0; }

    // ========== Wave initialization ==========

    private void initWave() {
        int totalCells = outputWidth * outputHeight;
        wave = new boolean[totalCells][patternCount];
        compatible = new int[totalCells * patternCount * 4];
        possibleCount = new int[totalCells];
        sumsOfWeights = new double[totalCells];
        sumsOfWeightLogWeights = new double[totalCells];
        entropies = new double[totalCells];

        int[] initCompat = new int[patternCount * 4];
        for (int p = 0; p < patternCount; p++)
            for (int dir = 0; dir < 4; dir++)
                initCompat[p * 4 + dir] = propagator[dir][p].length;

        double startSumW = 0, startSumWLogW = 0;
        for (int p = 0; p < patternCount; p++) {
            startSumW += frequencies[p];
            startSumWLogW += frequencies[p] * logFrequencies[p];
        }
        double startEntropy = Math.log(startSumW) - startSumWLogW / startSumW;

        for (int cell = 0; cell < totalCells; cell++) {
            Arrays.fill(wave[cell], true);
            possibleCount[cell] = patternCount;
            sumsOfWeights[cell] = startSumW;
            sumsOfWeightLogWeights[cell] = startSumWLogW;
            entropies[cell] = startEntropy;
            System.arraycopy(initCompat, 0, compatible, cell * patternCount * 4, patternCount * 4);
        }
    }

    // ========== Observe ==========

    private int findLowestEntropy() {
        double minEntropy = Double.MAX_VALUE;
        int minCell = -1;
        int totalCells = outputWidth * outputHeight;
        for (int cell = 0; cell < totalCells; cell++) {
            if (possibleCount[cell] <= 1) continue;
            // Noise breaks ties — use cheaper bit trick instead of nextDouble per cell
            double entropy = entropies[cell] + (random.nextDouble() * 1e-6);
            if (entropy < minEntropy) {
                minEntropy = entropy;
                minCell = cell;
            }
        }
        return minCell;
    }

    private void observe() {
        int cell = findLowestEntropy();
        if (cell == -1) { finished = true; return; }

        // Collect possible patterns into reusable buffer (no boxing)
        int count = 0;
        double totalWeight = 0;
        boolean[] cellWave = wave[cell];
        for (int p = 0; p < patternCount; p++) {
            if (cellWave[p]) {
                observeBuf[count++] = p;
                totalWeight += frequencies[p];
            }
        }
        if (count == 0) { contradiction = true; return; }

        // Weighted random selection
        double roll = random.nextDouble() * totalWeight;
        int chosen = observeBuf[count - 1];
        for (int i = 0; i < count; i++) {
            roll -= frequencies[observeBuf[i]];
            if (roll <= 0) { chosen = observeBuf[i]; break; }
        }

        // Save snapshot (stores compatible + entropy caches for fast restore)
        pushSnapshot(cell, observeBuf, count);

        // Collapse
        for (int p = 0; p < patternCount; p++) {
            if (cellWave[p] && p != chosen) {
                ban(cell, p);
            }
        }
    }

    // ========== Propagation ==========

    private void ban(int cell, int pattern) {
        wave[cell][pattern] = false;
        possibleCount[cell]--;

        double f = frequencies[pattern];
        sumsOfWeights[cell] -= f;
        sumsOfWeightLogWeights[cell] -= f * logFrequencies[pattern];
        double sw = sumsOfWeights[cell];
        if (sw > 0) entropies[cell] = Math.log(sw) - sumsOfWeightLogWeights[cell] / sw;

        int base = (cell * patternCount + pattern) * 4;
        compatible[base] = 0;
        compatible[base + 1] = 0;
        compatible[base + 2] = 0;
        compatible[base + 3] = 0;

        propQueuePush(cell, pattern);
    }

    private void propagate() {
        while (!propQueueEmpty()) {
            int cell = propQueueCells[propQueueHead & propQueueMask];
            int pattern = propQueuePatterns[propQueueHead & propQueueMask];
            propQueueHead++;

            int cellBase = cell * 4;
            for (int dir = 0; dir < 4; dir++) {
                int neighborCell = neighbors[cellBase + dir];
                if (neighborCell < 0) continue;

                int[] compatPatterns = propagator[dir][pattern];
                int oppDir = (dir + 2) & 3;
                boolean[] neighborWave = wave[neighborCell];

                for (int i = 0; i < compatPatterns.length; i++) {
                    int cp = compatPatterns[i];
                    if (!neighborWave[cp]) continue;

                    int idx = (neighborCell * patternCount + cp) * 4 + oppDir;
                    if (--compatible[idx] == 0) {
                        ban(neighborCell, cp);
                        if (possibleCount[neighborCell] == 0) {
                            contradiction = true;
                            return;
                        }
                    }
                }
            }
        }
    }

    // ========== Backtracking ==========

    private void pushSnapshot(int cell, int[] choices, int choiceCount) {
        if (backtrackTop >= MAX_BACKTRACK_DEPTH) {
            // Shift stack: drop oldest, make room at top
            System.arraycopy(backtrackStack, 1, backtrackStack, 0, MAX_BACKTRACK_DEPTH - 1);
            backtrackTop = MAX_BACKTRACK_DEPTH - 1;
        }
        backtrackStack[backtrackTop++] = new Snapshot(
            wave, compatible, possibleCount,
            sumsOfWeights, sumsOfWeightLogWeights, entropies,
            cell, choices, choiceCount);
    }

    private boolean backtrack() {
        while (backtrackTop > 0) {
            backtrackCount++;
            Snapshot snap = backtrackStack[--backtrackTop];
            backtrackStack[backtrackTop] = null; // allow GC

            if (snap.choiceCount <= 1) continue;

            // Shift choices: drop first (the one that failed), advance
            int newCount = snap.choiceCount - 1;
            int[] newChoices = new int[newCount];
            System.arraycopy(snap.remainingChoices, 1, newChoices, 0, newCount);

            // Restore full state from snapshot — O(n) memcpy, no recomputation
            int totalCells = outputWidth * outputHeight;
            for (int i = 0; i < totalCells; i++)
                System.arraycopy(snap.wave[i], 0, wave[i], 0, patternCount);
            System.arraycopy(snap.compatible, 0, compatible, 0, compatible.length);
            System.arraycopy(snap.possibleCount, 0, possibleCount, 0, possibleCount.length);
            System.arraycopy(snap.sumsOfWeights, 0, sumsOfWeights, 0, sumsOfWeights.length);
            System.arraycopy(snap.sumsOfWeightLogWeights, 0, sumsOfWeightLogWeights, 0, sumsOfWeightLogWeights.length);
            System.arraycopy(snap.entropies, 0, entropies, 0, entropies.length);

            contradiction = false;
            finished = false;
            propQueueClear();

            // Push updated snapshot for further alternatives
            if (newCount > 1) {
                pushSnapshot(snap.cell, newChoices, newCount);
            }

            // Collapse to next alternative
            int chosen = newChoices[0];
            boolean[] cellWave = wave[snap.cell];
            for (int p = 0; p < patternCount; p++) {
                if (cellWave[p] && p != chosen) ban(snap.cell, p);
            }

            propagate();
            if (!contradiction) return true;
            // Failed — loop tries next alternative or goes up
        }
        return false;
    }

    // ========== Run ==========

    public void runOneRound() {
        if (finished || contradiction) return;

        observe();
        if (finished) return;
        if (contradiction) { backtrack(); return; }

        propagate();
        if (contradiction) backtrack();
    }

    public void run() { run(10); }

    public void run(int maxRetries) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                System.out.println("  retry " + attempt + "/" + maxRetries + "...");
                resetWave();
            }
            while (!finished && !contradiction) {
                observe();
                if (finished) break;
                if (contradiction) { if (backtrack()) continue; break; }
                propagate();
                if (contradiction) { if (backtrack()) continue; break; }
            }
            if (finished) return;
        }
    }

    private void resetWave() {
        contradiction = false;
        finished = false;
        backtrackCount = 0;
        backtrackTop = 0;
        Arrays.fill(backtrackStack, null);
        propQueueClear();
        initWave();
    }

    // ========== Output ==========

    public BufferedImage getOutputImage() {
        return tileImages != null ? getOutputImageTiled() : getOutputImagePixel();
    }

    private BufferedImage getOutputImagePixel() {
        BufferedImage output = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < outputHeight; y++)
            for (int x = 0; x < outputWidth; x++) {
                int cp = getCollapsedPattern(y * outputWidth + x);
                output.setRGB(x, y, cp >= 0 ? patternPixels[cp][0] : 0x00000000);
            }
        return output;
    }

    private BufferedImage getOutputImageTiled() {
        int pw = outputWidth * inputTileW, ph = outputHeight * inputTileH;
        BufferedImage output = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = output.createGraphics();
        for (int y = 0; y < outputHeight; y++)
            for (int x = 0; x < outputWidth; x++) {
                int cp = getCollapsedPattern(y * outputWidth + x);
                if (cp >= 0) {
                    int tileId = patternPixels[cp][0];
                    if (tileId >= 0 && tileId < tileImages.length)
                        g.drawImage(tileImages[tileId], x * inputTileW, y * inputTileH, null);
                }
            }
        g.dispose();
        return output;
    }

    private int getCollapsedPattern(int cell) {
        boolean[] cw = wave[cell];
        for (int p = 0; p < patternCount; p++)
            if (cw[p]) return p;
        return -1;
    }

    public void saveToFile(String path) throws IOException {
        ImageIO.write(getOutputImage(), "PNG", new File(path));
    }
}
