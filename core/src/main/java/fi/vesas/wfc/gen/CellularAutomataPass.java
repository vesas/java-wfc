package fi.vesas.wfc.gen;

/**
 * Classic cellular-automata cave smoothing. In each iteration, a cell becomes
 * live if it has at least {@code liveThreshold} live neighbors in its 8-cell
 * Moore neighborhood. Out-of-bounds cells count as live (walls).
 *
 * Typical parameters for roguelike caves: 4–5 iterations with a threshold of 5.
 */
public class CellularAutomataPass implements GenPass {

    private final String layerName;
    private final int iterations;
    private final int liveThreshold;

    public CellularAutomataPass(String layerName, int iterations, int liveThreshold) {
        this.layerName = layerName;
        this.iterations = iterations;
        this.liveThreshold = liveThreshold;
    }

    @Override
    public void apply(GenContext ctx) {

        BoolLayer layer = ctx.boolLayer(layerName);
        int w = ctx.getWidth();
        int h = ctx.getHeight();
        boolean[][] buffer = new boolean[w][h];

        for (int iter = 0; iter < iterations; iter++) {
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int live = countLiveNeighbors(layer, x, y, w, h);
                    buffer[x][y] = live >= liveThreshold;
                }
            }
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    layer.set(x, y, buffer[x][y]);
                }
            }
        }
    }

    private static int countLiveNeighbors(BoolLayer layer, int x, int y, int w, int h) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                    count++;
                } else if (layer.get(nx, ny)) {
                    count++;
                }
            }
        }
        return count;
    }
}
