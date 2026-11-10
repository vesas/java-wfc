package fi.vesas.wfc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import fi.vesas.wfc.gen.BoolLayer;
import fi.vesas.wfc.gen.CellularAutomataPass;
import fi.vesas.wfc.gen.FloatLayer;
import fi.vesas.wfc.gen.GenContext;
import fi.vesas.wfc.gen.GenerationFailedException;
import fi.vesas.wfc.gen.IntLayer;
import fi.vesas.wfc.gen.PerlinNoisePass;
import fi.vesas.wfc.gen.PinnedTilesPass;
import fi.vesas.wfc.gen.ThresholdPass;
import fi.vesas.wfc.gen.VoronoiRegionsPass;
import fi.vesas.wfc.gen.WfcPass;

/**
 * Visual playground for the fi.vesas.wfc.gen pipeline. Each demo exercises one
 * or more passes and renders the result as a texture so parameter changes show
 * up immediately.
 */
public class PipelineDemoApp extends ApplicationAdapter {

    private static final int PANEL_WIDTH = 280;
    private static final int GRID_SIZE = 128;

    private Stage stage;
    private Skin skin;
    private SpriteBatch batch;

    private Texture outputTexture;
    private String statusText = "";

    private List<Demo> demos;
    private Demo currentDemo;
    private Table paramsPanel;
    private final List<Slider> activeSliders = new ArrayList<>();
    private final List<Label> activeValueLabels = new ArrayList<>();
    private TextField seedField;
    private Label statusLabel;

    @Override
    public void create() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        stage = new Stage(new ScreenViewport());
        batch = new SpriteBatch();

        demos = buildDemos();
        currentDemo = demos.get(0);

        buildUI();
        Gdx.input.setInputProcessor(stage);

        regenerate();
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1f);

        if (outputTexture != null) {
            float x = PANEL_WIDTH + 16;
            float y = 16;
            float w = Gdx.graphics.getWidth() - x - 16;
            float h = Gdx.graphics.getHeight() - 32;
            float scale = Math.min(w / outputTexture.getWidth(), h / outputTexture.getHeight());
            float drawW = outputTexture.getWidth() * scale;
            float drawH = outputTexture.getHeight() * scale;
            float drawX = x + (w - drawW) / 2f;
            float drawY = y + (h - drawH) / 2f;

            batch.begin();
            batch.draw(outputTexture, drawX, drawY, drawW, drawH);
            batch.end();
        }

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (outputTexture != null) outputTexture.dispose();
        stage.dispose();
        skin.dispose();
        batch.dispose();
    }

    // ========== UI ==========

    private void buildUI() {

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(12f);
        stage.addActor(root);

        Label title = new Label("Pipeline demos", skin);
        title.setFontScale(1.2f);
        root.add(title).left().padBottom(8f).row();

        Label demoLabel = new Label("Demo", skin);
        root.add(demoLabel).left().row();

        final SelectBox<String> demoSelect = new SelectBox<>(skin);
        String[] names = new String[demos.size()];
        for (int i = 0; i < demos.size(); i++) names[i] = demos.get(i).name();
        demoSelect.setItems(names);
        demoSelect.setSelected(currentDemo.name());
        demoSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String selected = demoSelect.getSelected();
                for (Demo d : demos) {
                    if (d.name().equals(selected)) {
                        currentDemo = d;
                        break;
                    }
                }
                rebuildParams();
                regenerate();
            }
        });
        root.add(demoSelect).width(PANEL_WIDTH - 24).left().padBottom(12f).row();

        paramsPanel = new Table();
        paramsPanel.top().left();
        root.add(paramsPanel).width(PANEL_WIDTH - 24).left().padBottom(12f).row();

        Label seedLabel = new Label("Seed", skin);
        root.add(seedLabel).left().row();
        seedField = new TextField("1", skin);
        root.add(seedField).width(PANEL_WIDTH - 24).left().padBottom(8f).row();

        TextButton regen = new TextButton("Regenerate", skin);
        regen.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                regenerate();
            }
        });
        root.add(regen).width(PANEL_WIDTH - 24).left().padBottom(8f).row();

        TextButton randomize = new TextButton("Random seed", skin);
        randomize.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                seedField.setText(String.valueOf(System.nanoTime() & 0xFFFFFFL));
                regenerate();
            }
        });
        root.add(randomize).width(PANEL_WIDTH - 24).left().padBottom(12f).row();

        statusLabel = new Label("", skin);
        statusLabel.setWrap(true);
        root.add(statusLabel).width(PANEL_WIDTH - 24).left().padBottom(8f).row();

        rebuildParams();
    }

    private void rebuildParams() {
        paramsPanel.clear();
        activeSliders.clear();
        activeValueLabels.clear();

        for (Param p : currentDemo.params()) {
            Label name = new Label(p.label, skin);
            paramsPanel.add(name).left().row();

            Table row = new Table();
            Slider slider = new Slider(p.min, p.max, p.step, false, skin);
            slider.setValue(p.initial);
            activeSliders.add(slider);

            Label val = new Label(formatValue(slider.getValue(), p.integer), skin);
            val.setWidth(60);
            activeValueLabels.add(val);

            slider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    val.setText(formatValue(slider.getValue(), p.integer));
                    regenerate();
                }
            });

            row.add(slider).width(PANEL_WIDTH - 80).padRight(6f);
            row.add(val).width(60);
            paramsPanel.add(row).left().padBottom(6f).row();
        }
    }

    private static String formatValue(float v, boolean integer) {
        return integer ? String.valueOf((int) v) : String.format("%.3f", v);
    }

    // ========== Regeneration ==========

    private void regenerate() {
        long seed = parseSeed();

        float[] values = new float[activeSliders.size()];
        for (int i = 0; i < values.length; i++) values[i] = activeSliders.get(i).getValue();

        Pixmap pm = null;
        try {
            pm = currentDemo.generate(GRID_SIZE, GRID_SIZE, seed, values);
            statusText = "ok";
        } catch (GenerationFailedException ex) {
            pm = errorPixmap(GRID_SIZE);
            statusText = "contradiction: " + ex.getMessage();
        } catch (RuntimeException ex) {
            pm = errorPixmap(GRID_SIZE);
            statusText = "error: " + ex.getMessage();
        }

        if (outputTexture != null) outputTexture.dispose();
        outputTexture = new Texture(pm);
        pm.dispose();

        if (statusLabel != null) statusLabel.setText(statusText);
    }

    private long parseSeed() {
        try {
            return Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    private static Pixmap errorPixmap(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0.4f, 0.1f, 0.1f, 1f);
        pm.fill();
        return pm;
    }

    // ========== Demo framework ==========

    private static class Param {
        final String label;
        final float min, max, step, initial;
        final boolean integer;

        Param(String label, float min, float max, float step, float initial, boolean integer) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.initial = initial;
            this.integer = integer;
        }

        static Param f(String label, float min, float max, float step, float initial) {
            return new Param(label, min, max, step, initial, false);
        }

        static Param i(String label, int min, int max, int initial) {
            return new Param(label, min, max, 1, initial, true);
        }
    }

    private interface Demo {
        String name();
        List<Param> params();
        Pixmap generate(int w, int h, long seed, float[] values);
    }

    private List<Demo> buildDemos() {
        List<Demo> list = new ArrayList<>();
        list.add(new PerlinDemo());
        list.add(new ThresholdDemo());
        list.add(new CaveDemo());
        list.add(new VoronoiDemo());
        list.add(new BiomeWfcDemo());
        list.add(new PinnedWfcDemo());
        return list;
    }

    // ========== Rendering helpers ==========

    private static Pixmap floatToGrayscale(FloatLayer layer) {
        int w = layer.getWidth();
        int h = layer.getHeight();
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float v = layer.get(x, y);
                float g = Math.min(1f, Math.max(0f, (v + 1f) * 0.5f));
                pm.drawPixel(x, h - 1 - y, rgba(g, g, g, 1f));
            }
        }
        return pm;
    }

    private static Pixmap boolToBW(BoolLayer layer) {
        int w = layer.getWidth();
        int h = layer.getHeight();
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                boolean v = layer.get(x, y);
                pm.drawPixel(x, h - 1 - y, v ? 0xFFFFFFFF : 0x000000FF);
            }
        }
        return pm;
    }

    private static Pixmap intToPalette(IntLayer layer, int[] palette) {
        int w = layer.getWidth();
        int h = layer.getHeight();
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int id = layer.get(x, y);
                int color = palette[Math.floorMod(id, palette.length)];
                pm.drawPixel(x, h - 1 - y, color);
            }
        }
        return pm;
    }

    private static int rgba(float r, float g, float b, float a) {
        int ri = Math.round(r * 255f);
        int gi = Math.round(g * 255f);
        int bi = Math.round(b * 255f);
        int ai = Math.round(a * 255f);
        return (ri << 24) | (gi << 16) | (bi << 8) | ai;
    }

    private static final int[] REGION_PALETTE = new int[]{
            0xE04040FF, 0x40E040FF, 0x4080E0FF, 0xE0C040FF, 0xC040E0FF,
            0x40E0C0FF, 0xE08040FF, 0x40A0FFFF, 0xA0E040FF, 0xFF40A0FF,
            0x808040FF, 0x408080FF, 0x804040FF, 0x408040FF, 0x404080FF,
            0x8040C0FF, 0xC04080FF, 0x40C080FF, 0xC08040FF, 0xA0A0A0FF,
    };

    private static final int[] BIOME_PALETTE = new int[]{
            0x2048A0FF, // water
            0xD0B870FF, // sand
            0x40A048FF, // grass
            0x286028FF, // forest
            0x807060FF, // mountain
            0xF0F0FFFF, // snow
            0xA06040FF, // desert
            0x606060FF, // rock
    };

    private static final int[] TILE_PALETTE = new int[]{
            0x303030FF, 0xE04040FF, 0x40E040FF, 0x4080E0FF, 0xE0C040FF,
            0xC040E0FF, 0x40E0C0FF, 0xE08040FF, 0x40A0FFFF, 0xA0E040FF,
            0xFF40A0FF, 0x808040FF,
    };

    // ========== Demos ==========

    /** Perlin noise with FBM, rendered as grayscale height field. */
    private class PerlinDemo implements Demo {
        @Override public String name() { return "1. Perlin noise"; }
        @Override public List<Param> params() {
            return List.of(
                    Param.f("scale", 0.01f, 0.3f, 0.005f, 0.05f),
                    Param.i("octaves", 1, 6, 4),
                    Param.f("persistence", 0.1f, 0.9f, 0.05f, 0.5f)
            );
        }
        @Override public Pixmap generate(int w, int h, long seed, float[] v) {
            GenContext ctx = new GenContext(w, h, seed);
            new PerlinNoisePass("n", v[0], (int) v[1], v[2]).apply(ctx);
            return floatToGrayscale(ctx.floatLayer("n"));
        }
    }

    /** Perlin noise thresholded to a black/white mask. */
    private class ThresholdDemo implements Demo {
        @Override public String name() { return "2. Threshold"; }
        @Override public List<Param> params() {
            return List.of(
                    Param.f("scale", 0.01f, 0.3f, 0.005f, 0.05f),
                    Param.f("threshold", -0.5f, 0.5f, 0.02f, 0.0f)
            );
        }
        @Override public Pixmap generate(int w, int h, long seed, float[] v) {
            GenContext ctx = new GenContext(w, h, seed);
            new PerlinNoisePass("n", v[0], 4, 0.5f).apply(ctx);
            new ThresholdPass("n", "mask", v[1]).apply(ctx);
            return boolToBW(ctx.boolLayer("mask"));
        }
    }

    /** Random fill + cellular-automata smoothing — classic roguelike cave generation. */
    private class CaveDemo implements Demo {
        @Override public String name() { return "3. Cellular automata cave"; }
        @Override public List<Param> params() {
            return List.of(
                    Param.f("density", 0.30f, 0.70f, 0.01f, 0.45f),
                    Param.i("iterations", 1, 8, 4),
                    Param.i("live threshold", 3, 6, 5)
            );
        }
        @Override public Pixmap generate(int w, int h, long seed, float[] v) {
            GenContext ctx = new GenContext(w, h, seed);
            BoolLayer mask = ctx.boolLayer("mask");
            Random r = new Random(seed);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    mask.set(x, y, r.nextFloat() < v[0]);
                }
            }
            new CellularAutomataPass("mask", (int) v[1], (int) v[2]).apply(ctx);
            return boolToBW(mask);
        }
    }

    /** Voronoi tessellation — each cell is colored by nearest seed point. */
    private class VoronoiDemo implements Demo {
        @Override public String name() { return "4. Voronoi regions"; }
        @Override public List<Param> params() {
            return List.of(Param.i("region count", 2, 40, 12));
        }
        @Override public Pixmap generate(int w, int h, long seed, float[] v) {
            GenContext ctx = new GenContext(w, h, seed);
            new VoronoiRegionsPass("region", (int) v[0]).apply(ctx);
            return intToPalette(ctx.intLayer("region"), REGION_PALETTE);
        }
    }

    /** Voronoi biome layer feeding a biome-constrained WFC — the overworld flow. */
    private class BiomeWfcDemo implements Demo {
        @Override public String name() { return "5. Biome-constrained WFC"; }
        @Override public List<Param> params() {
            return List.of(
                    Param.i("biomes", 2, 6, 4),
                    Param.i("tiles/biome", 1, 4, 2)
            );
        }
        @Override public Pixmap generate(int w, int h, long seed, float[] v) {
            GenContext ctx = new GenContext(w, h, seed);
            int biomes = (int) v[0];
            int tilesPerBiome = (int) v[1];
            int totalTiles = biomes * tilesPerBiome;

            new VoronoiRegionsPass("biome", biomes).apply(ctx);

            new WfcPass("terrain", totalTiles, new Constraints())
                    .rotationsAllowed(false)
                    .constrainedBy("biome", biomeId -> {
                        int[] allowed = new int[tilesPerBiome];
                        for (int i = 0; i < tilesPerBiome; i++) {
                            allowed[i] = biomeId * tilesPerBiome + i;
                        }
                        return allowed;
                    })
                    .apply(ctx);

            // Render by mapping each tile to its biome's base color, shifted slightly
            return tintedBiomeRender(ctx.intLayer("terrain"), tilesPerBiome);
        }
    }

    private static Pixmap tintedBiomeRender(IntLayer terrain, int tilesPerBiome) {
        int w = terrain.getWidth();
        int h = terrain.getHeight();
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int tile = terrain.get(x, y);
                int biome = tile / tilesPerBiome;
                int variant = tile % tilesPerBiome;
                int base = BIOME_PALETTE[Math.floorMod(biome, BIOME_PALETTE.length)];
                float tint = 1f - variant * 0.15f;
                int r = (int) (((base >> 24) & 0xFF) * tint) & 0xFF;
                int g = (int) (((base >> 16) & 0xFF) * tint) & 0xFF;
                int b = (int) (((base >> 8) & 0xFF) * tint) & 0xFF;
                int color = (r << 24) | (g << 16) | (b << 8) | 0xFF;
                pm.drawPixel(x, h - 1 - y, color);
            }
        }
        return pm;
    }

    /** WFC with port constraints (circuit tileset) and random pinned cells. */
    private class PinnedWfcDemo implements Demo {
        @Override public String name() { return "6. Pinned WFC (circuit)"; }
        @Override public List<Param> params() {
            return List.of(Param.i("pins", 0, 30, 6));
        }
        @Override public Pixmap generate(int w, int h, long seed, float[] v) {
            // Use a smaller grid for circuit WFC so it solves quickly
            int size = 48;
            GenContext ctx = new GenContext(size, size, seed);
            Constraints constraints = buildCircuitConstraints();

            PinnedTilesPass pins = new PinnedTilesPass("terrain");
            Random r = new Random(seed + 12345L);
            int pinCount = (int) v[0];
            for (int i = 0; i < pinCount; i++) {
                int x = r.nextInt(size);
                int y = r.nextInt(size);
                int tileId = 1 + r.nextInt(8);
                pins.pin(x, y, tileId);
            }

            new WfcPass("terrain", 9, constraints)
                    .withAllowedTileSource(pins.asSource())
                    .apply(ctx);

            return intToPalette(ctx.intLayer("terrain"), TILE_PALETTE);
        }
    }

    private static Constraints buildCircuitConstraints() {
        Constraints c = new Constraints();
        c.addPort(1, SimpleWFC.DIR.S, 1);
        c.addPort(1, SimpleWFC.DIR.E, 1);
        c.addPort(1, SimpleWFC.DIR.N, 1);
        c.addPort(1, SimpleWFC.DIR.W, 1);
        c.addPort(2, SimpleWFC.DIR.S, 1);
        c.addPort(2, SimpleWFC.DIR.N, 2);
        c.addPort(3, SimpleWFC.DIR.N, 2);
        c.addPort(4, SimpleWFC.DIR.N, 2);
        c.addPort(4, SimpleWFC.DIR.W, 2);
        c.addPort(5, SimpleWFC.DIR.S, 1);
        c.addPort(6, SimpleWFC.DIR.N, 2);
        c.addPort(6, SimpleWFC.DIR.W, 2);
        c.addPort(6, SimpleWFC.DIR.E, 2);
        c.addPort(7, SimpleWFC.DIR.N, 2);
        c.addPort(7, SimpleWFC.DIR.S, 2);
        c.addPort(8, SimpleWFC.DIR.N, 2);
        c.addPort(8, SimpleWFC.DIR.S, 2);
        return c;
    }
}
