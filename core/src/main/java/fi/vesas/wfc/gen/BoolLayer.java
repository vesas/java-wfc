package fi.vesas.wfc.gen;

/**
 * 2D boolean grid. Holds masks: walkable, solid, room-interior, etc.
 */
public class BoolLayer extends Layer {

    private final boolean[][] data;

    public BoolLayer(String name, int width, int height) {
        super(name, width, height);
        this.data = new boolean[width][height];
    }

    public boolean get(int x, int y) {
        return data[x][y];
    }

    public void set(int x, int y, boolean value) {
        data[x][y] = value;
    }

    public boolean[][] raw() {
        return data;
    }
}
