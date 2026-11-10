package fi.vesas.wfc.gen;

/**
 * 2D float grid. Holds continuous fields such as elevation, moisture, density.
 */
public class FloatLayer extends Layer {

    private final float[][] data;

    public FloatLayer(String name, int width, int height) {
        super(name, width, height);
        this.data = new float[width][height];
    }

    public float get(int x, int y) {
        return data[x][y];
    }

    public void set(int x, int y, float value) {
        data[x][y] = value;
    }

    public float[][] raw() {
        return data;
    }
}
