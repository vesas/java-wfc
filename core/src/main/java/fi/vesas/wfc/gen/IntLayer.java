package fi.vesas.wfc.gen;

/**
 * 2D int grid. Holds tile IDs, biome labels, region IDs, or any other integer
 * per-cell value.
 */
public class IntLayer extends Layer {

    private final int[][] data;

    public IntLayer(String name, int width, int height) {
        super(name, width, height);
        this.data = new int[width][height];
    }

    public int get(int x, int y) {
        return data[x][y];
    }

    public void set(int x, int y, int value) {
        data[x][y] = value;
    }

    public void fill(int value) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                data[x][y] = value;
            }
        }
    }

    /** Direct access to the underlying array. Use for performance-sensitive reads/writes. */
    public int[][] raw() {
        return data;
    }
}
