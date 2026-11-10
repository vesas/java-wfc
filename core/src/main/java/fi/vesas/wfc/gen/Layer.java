package fi.vesas.wfc.gen;

/**
 * A named 2D grid managed by {@link GenContext}. Subclasses carry typed data
 * (int / float / bool). Runtime-pure — no rendering or I/O dependencies.
 */
public abstract class Layer {

    protected final String name;
    protected final int width;
    protected final int height;

    protected Layer(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
