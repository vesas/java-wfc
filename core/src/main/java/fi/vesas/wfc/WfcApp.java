package fi.vesas.wfc;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class WfcApp extends ApplicationAdapter {

    private static final int PANEL_WIDTH = 250;

    // Rendering
    private Stage stage;
    private Skin skin;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;

    // WFC state
    private SimpleWFC tiledWfc;
    private OverlappingWFC overlappingWfc;
    private int[][][] grid;
    private int[][][] rots;

    // Tile textures for tiled mode
    private Texture[] tileTextures;
    private int tilePixelW = 32;
    private int tilePixelH = 32;

    // Overlapping mode output texture
    private Texture overlappingTexture;
    private boolean overlappingTextureDirty = true;

    // Tiled mode composited output (for 2x2 preview)
    private Texture tiledCompositeTexture;
    private boolean tiledCompositeDirty = true;

    // Sample images for overlapping mode
    private String[] sampleNames;
    private BufferedImage currentSample;
    private File customSampleFile;

    // Sample preview
    private Texture samplePreviewTexture;

    // Tile preview
    private Texture[] tilePreviewTextures;

    // Animation state
    private boolean autoRunning;
    private float stepInterval = 0.05f;
    private float stepTimer;
    private int stepCount;

    // Highlight effect
    private float lightEffect;
    private int lightEffectX, lightEffectY;

    // Current mode
    private boolean tiledMode = true;

    // Tileset registry
    private List<TilesetDef> tilesets;
    private TilesetDef currentTileset;

    // Last export directory (remembered across exports)
    private File lastExportDir;

    // UI widgets
    private SelectBox<String> modeSelect;
    private Table tiledSection, overlappingSection;
    private SelectBox<String> tilesetSelect;
    private TextField gridWField, gridHField;
    private Table tilePreviewTable;
    private SelectBox<String> sampleSelect;
    private Label customFileLabel;
    private Image samplePreviewImage;
    private Label sampleInfoLabel;
    private TextField inputTileWField, inputTileHField;
    private SelectBox<String> sizePresetSelect;
    private TextField outWField, outHField;
    private SelectBox<String> patternSizeSelect, symmetrySelect;
    private TextField seedField;
    private CheckBox tilingHCheck, tilingVCheck, showTilingCheck;
    private TextButton runBtn;
    private Slider speedSlider;
    private Label statusLabel, stepsLabel, backtracksLabel;

    // Batch dialog
    private Window batchWindow;

    @Override
    public void create() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        stage = new Stage(new ScreenViewport());
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();

        tilesets = TilesetDef.allTilesets();
        currentTileset = tilesets.get(0);

        findSampleImages();
        buildUI();

        InputMultiplexer inputMux = new InputMultiplexer();
        inputMux.addProcessor(stage);
        inputMux.addProcessor(new InputAdapter() {
            @Override
            public boolean keyTyped(char key) {
                if (stage.getKeyboardFocus() instanceof TextField) return false;
                switch (key) {
                    case ' ': doStep(); return true;
                    case '\r': case '\n': toggleRun(); return true;
                    case 'r': doReset(); return true;
                    case 'e': doExport(); return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(inputMux);

        updateTilePreview();
        updateSamplePreview();
        doReset();
    }

    // ========== Sample image management ==========

    private void findSampleImages() {
        String[] names = {"sample_grass.png", "sample_bricks.png", "sample_water.png", "sample_cave.png"};
        for (String name : names) {
            if (!new File(name).exists()) { createDefaultSamples(); break; }
        }
        sampleNames = names;
    }

    private void createDefaultSamples() {
        java.util.Random rng = new java.util.Random(42);

        // Grass/dirt terrain — 3 colors, organic feel
        createSample("sample_grass.png", 16, (img, x, y) -> {
            int[] grass = {0xFF2d6e2d, 0xFF3a8a3a, 0xFF4ca64c};
            int[] dirt  = {0xFF8b6b3e, 0xFF7a5c32};
            // Grass with occasional dirt patches
            double n = Math.sin(x * 0.8) * Math.cos(y * 0.6) + Math.sin((x + y) * 0.4);
            if (n > 0.7) return dirt[(x + y) % 2];
            return grass[(x * 3 + y * 7) % 3];
        });

        // Brick wall — structured, clear pattern
        createSample("sample_bricks.png", 16, (img, x, y) -> {
            int mortar = 0xFF888888;
            int[] brick = {0xFFa04030, 0xFFb04838, 0xFF983828};
            // Horizontal mortar lines
            if (y % 4 == 0) return mortar;
            // Vertical mortar — offset every other row
            int offset = (y / 4) % 2 == 0 ? 0 : 4;
            if ((x + offset) % 8 == 0) return mortar;
            return brick[((x / 4) + (y / 4) * 3) % 3];
        });

        // Water — flowing blue tones
        createSample("sample_water.png", 16, (img, x, y) -> {
            int[] water = {0xFF1a3a6e, 0xFF2248a0, 0xFF1e3f80, 0xFF3060b0};
            int[] foam  = {0xFF5888cc, 0xFF6898dd};
            double wave = Math.sin(x * 0.5 + y * 0.3) + Math.sin(x * 0.3 - y * 0.7);
            if (wave > 1.0) return foam[(x + y) % 2];
            return water[(x * 5 + y * 11) % 4];
        });

        // Cave/stone — dark with mineral veins
        createSample("sample_cave.png", 16, (img, x, y) -> {
            int[] stone = {0xFF3a3a3a, 0xFF444444, 0xFF333333, 0xFF4a4a4a};
            int vein = 0xFF5a6a5a;
            // Diagonal veins
            if ((x + y * 2) % 11 == 0 || (x * 2 + y) % 13 == 0) return vein;
            return stone[(x * 7 + y * 13) % 4];
        });
    }

    private void createSample(String filename, int size, SamplePixelFunc func) {
        try {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    img.setRGB(x, y, func.getRGB(img, x, y));
            ImageIO.write(img, "PNG", new File(filename));
        } catch (Exception e) {
            System.err.println("Could not save sample: " + filename + " - " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SamplePixelFunc { int getRGB(BufferedImage img, int x, int y); }

    private BufferedImage loadSampleAsBufferedImage(String filename) {
        try {
            File f = new File(filename);
            if (f.exists()) return ImageIO.read(f);
            f = Gdx.files.internal(filename).file();
            if (f.exists()) return ImageIO.read(f);
        } catch (Exception e) {
            System.err.println("Could not load sample: " + filename + " - " + e.getMessage());
        }
        return null;
    }

    // ========== UI CONSTRUCTION ==========

    private void buildUI() {
        Table root = new Table(skin);
        root.setFillParent(true);

        Table controlPanel = new Table(skin);
        controlPanel.top().left().pad(10);

        // -- Mode selector --
        controlPanel.add(new Label("Mode:", skin)).left().padRight(6);
        modeSelect = new SelectBox<>(skin);
        modeSelect.setItems("Tiled", "Overlapping");
        modeSelect.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                tiledMode = modeSelect.getSelectedIndex() == 0;
                tiledSection.setVisible(tiledMode);
                overlappingSection.setVisible(!tiledMode);
                autoRunning = false;
                runBtn.setText("Run");
            }
        });
        controlPanel.add(modeSelect).fillX().row();

        // -- Tiled section --
        tiledSection = new Table(skin);
        buildTiledSection(tiledSection);
        controlPanel.add(tiledSection).colspan(2).fillX().row();

        // -- Overlapping section --
        overlappingSection = new Table(skin);
        buildOverlappingSection(overlappingSection);
        overlappingSection.setVisible(false);
        controlPanel.add(overlappingSection).colspan(2).fillX().row();

        // -- Common section --
        addSeparator(controlPanel);

        controlPanel.add(new Label("Seed:", skin)).left().padRight(6);
        seedField = new TextField("229", skin);
        seedField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        controlPanel.add(seedField).fillX().row();

        tilingHCheck = new CheckBox(" Tile H", skin);
        tilingHCheck.setChecked(true);
        controlPanel.add(tilingHCheck).left().colspan(2).row();

        tilingVCheck = new CheckBox(" Tile V", skin);
        tilingVCheck.setChecked(true);
        controlPanel.add(tilingVCheck).left().colspan(2).row();

        showTilingCheck = new CheckBox(" 2x2 Preview", skin);
        controlPanel.add(showTilingCheck).left().colspan(2).row();

        // -- Action buttons --
        addSeparator(controlPanel);

        Table btnRow1 = new Table();
        TextButton stepBtn = new TextButton("Step", skin);
        stepBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { doStep(); }
        });
        btnRow1.add(stepBtn).width(68).padRight(4);

        runBtn = new TextButton("Run", skin);
        runBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { toggleRun(); }
        });
        btnRow1.add(runBtn).width(68).padRight(4);

        TextButton resetBtn = new TextButton("Reset", skin);
        resetBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { doReset(); }
        });
        btnRow1.add(resetBtn).width(68);
        controlPanel.add(btnRow1).colspan(2).row();

        Table btnRow2 = new Table();
        TextButton exportBtn = new TextButton("Export", skin);
        exportBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { doExport(); }
        });
        btnRow2.add(exportBtn).width(104).padRight(4);

        TextButton batchBtn = new TextButton("Batch...", skin);
        batchBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { showBatchDialog(); }
        });
        btnRow2.add(batchBtn).width(104);
        controlPanel.add(btnRow2).colspan(2).padTop(4).row();

        // -- Speed slider --
        addSeparator(controlPanel);
        controlPanel.add(new Label("Speed:", skin)).left().padRight(6);
        speedSlider = new Slider(0f, 1f, 0.01f, false, skin);
        speedSlider.setValue(0.9f);
        speedSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                stepInterval = 0.5f * (1f - speedSlider.getValue());
            }
        });
        stepInterval = 0.5f * (1f - 0.9f);
        controlPanel.add(speedSlider).fillX().row();

        // -- Status labels --
        addSeparator(controlPanel);
        statusLabel = new Label("Ready", skin);
        controlPanel.add(statusLabel).left().colspan(2).row();
        stepsLabel = new Label("Steps: 0", skin);
        controlPanel.add(stepsLabel).left().colspan(2).row();
        backtracksLabel = new Label("Backtracks: 0", skin);
        controlPanel.add(backtracksLabel).left().colspan(2).row();

        addSeparator(controlPanel);
        controlPanel.add(new Label("Space=Step Enter=Run\nR=Reset  E=Export", skin)).left().colspan(2).row();

        // Assemble
        ScrollPane scrollPane = new ScrollPane(controlPanel, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        root.add(scrollPane).width(PANEL_WIDTH).fillY().expandY().top();
        root.add().expand().fill();

        stage.addActor(root);
    }

    private void buildTiledSection(Table section) {
        section.add(new Label("Tileset:", skin)).left().padRight(6);
        tilesetSelect = new SelectBox<>(skin);
        rebuildTilesetDropdown();
        tilesetSelect.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                currentTileset = tilesets.get(tilesetSelect.getSelectedIndex());
                updateTilePreview();
            }
        });
        section.add(tilesetSelect).fillX().row();

        // Load tileset button
        section.add(new Label("", skin));
        TextButton loadTilesetBtn = new TextButton("Load Tileset...", skin);
        loadTilesetBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { openTilesetBrowser(); }
        });
        section.add(loadTilesetBtn).fillX().row();

        // Tile preview strip
        tilePreviewTable = new Table();
        ScrollPane tilePreviewScroll = new ScrollPane(tilePreviewTable, skin);
        tilePreviewScroll.setScrollingDisabled(false, true);
        section.add(tilePreviewScroll).colspan(2).height(40).fillX().padTop(4).row();

        section.add(new Label("Grid W:", skin)).left().padRight(6);
        gridWField = new TextField("28", skin);
        gridWField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        section.add(gridWField).width(60).left().row();

        section.add(new Label("Grid H:", skin)).left().padRight(6);
        gridHField = new TextField("20", skin);
        gridHField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        section.add(gridHField).width(60).left().row();
    }

    private void buildOverlappingSection(Table section) {
        // Help text
        Label helpLabel = new Label("Use a small image (16-64px)\nwith few distinct colors.", skin);
        helpLabel.setWrap(true);
        section.add(helpLabel).colspan(2).fillX().padBottom(4).row();

        section.add(new Label("Sample:", skin)).left().padRight(6);
        sampleSelect = new SelectBox<>(skin);
        sampleSelect.setItems(sampleNames);
        sampleSelect.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                customSampleFile = null;
                customFileLabel.setText("");
                updateSamplePreview();
            }
        });
        section.add(sampleSelect).fillX().row();

        section.add(new Label("", skin));
        TextButton browseBtn = new TextButton("Browse...", skin);
        browseBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { openFileBrowser(); }
        });
        section.add(browseBtn).fillX().row();

        customFileLabel = new Label("", skin);
        customFileLabel.setWrap(true);
        section.add(customFileLabel).colspan(2).fillX().left().row();

        // Sample preview image
        samplePreviewImage = new Image();
        section.add(samplePreviewImage).colspan(2).size(200, 150).padTop(4).row();

        // Sample info (dimensions, colors)
        sampleInfoLabel = new Label("", skin);
        sampleInfoLabel.setWrap(true);
        section.add(sampleInfoLabel).colspan(2).fillX().left().row();

        // Input tile size
        Label tileHelpLabel = new Label("Tile size: 1=pixel mode.\nSet >1 if input is a tilemap.", skin);
        tileHelpLabel.setWrap(true);
        section.add(tileHelpLabel).colspan(2).fillX().padTop(4).row();

        section.add(new Label("Tile W:", skin)).left().padRight(6);
        inputTileWField = new TextField("1", skin);
        inputTileWField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        section.add(inputTileWField).width(60).left().row();

        section.add(new Label("Tile H:", skin)).left().padRight(6);
        inputTileHField = new TextField("1", skin);
        inputTileHField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        section.add(inputTileHField).width(60).left().row();

        // Size preset
        section.add(new Label("Output:", skin)).left().padRight(6);
        sizePresetSelect = new SelectBox<>(skin);
        sizePresetSelect.setItems("Custom", "64x64", "128x128", "256x256", "512x512", "1024x1024");
        sizePresetSelect.setSelectedIndex(2); // 128x128
        sizePresetSelect.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                String sel = sizePresetSelect.getSelected();
                if (sel.contains("x")) {
                    String[] parts = sel.split("x");
                    outWField.setText(parts[0]);
                    outHField.setText(parts[1]);
                }
            }
        });
        section.add(sizePresetSelect).fillX().row();

        section.add(new Label("Out W:", skin)).left().padRight(6);
        outWField = new TextField("128", skin);
        outWField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        section.add(outWField).width(60).left().row();

        section.add(new Label("Out H:", skin)).left().padRight(6);
        outHField = new TextField("128", skin);
        outHField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        section.add(outHField).width(60).left().row();

        // Pattern size with help
        Label patternHelp = new Label("Pattern: size of local\nneighborhood. 3=default.", skin);
        patternHelp.setWrap(true);
        section.add(patternHelp).colspan(2).fillX().padTop(4).row();

        section.add(new Label("Pattern:", skin)).left().padRight(6);
        patternSizeSelect = new SelectBox<>(skin);
        patternSizeSelect.setItems("2", "3");
        patternSizeSelect.setSelectedIndex(1);
        section.add(patternSizeSelect).fillX().row();

        // Symmetry with help
        Label symHelp = new Label("Symmetry: 1=as-is,\n4=rotations, 8=rot+flip.", skin);
        symHelp.setWrap(true);
        section.add(symHelp).colspan(2).fillX().padTop(4).row();

        section.add(new Label("Symmetry:", skin)).left().padRight(6);
        symmetrySelect = new SelectBox<>(skin);
        symmetrySelect.setItems("1", "2", "4", "8");
        symmetrySelect.setSelectedIndex(3);
        section.add(symmetrySelect).fillX().row();
    }

    private void addSeparator(Table table) {
        table.add(new Label("", skin)).colspan(2).padTop(6).padBottom(2).row();
    }

    // ========== Preview helpers ==========

    private void updateSamplePreview() {
        disposeSamplePreview();
        sampleInfoLabel.setText("");
        try {
            File fileToLoad = null;
            if (customSampleFile != null) {
                fileToLoad = customSampleFile;
            } else {
                String name = sampleSelect.getSelected();
                if (name != null) {
                    File f = new File(name);
                    fileToLoad = f.exists() ? f : Gdx.files.internal(name).file();
                }
            }

            if (fileToLoad != null && fileToLoad.exists()) {
                samplePreviewTexture = new Texture(new FileHandle(fileToLoad));
                samplePreviewTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
                samplePreviewImage.setDrawable(new TextureRegionDrawable(new TextureRegion(samplePreviewTexture)));

                // Show sample info
                BufferedImage info = ImageIO.read(fileToLoad);
                if (info != null) {
                    java.util.Set<Integer> colors = new java.util.HashSet<>();
                    for (int y = 0; y < info.getHeight(); y++)
                        for (int x = 0; x < info.getWidth(); x++)
                            colors.add(info.getRGB(x, y));
                    sampleInfoLabel.setText(info.getWidth() + "x" + info.getHeight()
                        + "px, " + colors.size() + " colors");
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load preview: " + e.getMessage());
        }
    }

    private void updateTilePreview() {
        disposeTilePreview();
        tilePreviewTable.clear();
        int texCount = currentTileset.tileCount - (currentTileset.emptyAllowed ? 1 : 0);
        tilePreviewTextures = new Texture[texCount];
        for (int i = 0; i < texCount; i++) {
            try {
                String path = currentTileset.tileDir != null
                    ? currentTileset.tileDir + "/" + currentTileset.basename + (i + 1) + ".png"
                    : currentTileset.basename + (i + 1) + ".png";
                tilePreviewTextures[i] = currentTileset.tileDir != null
                    ? new Texture(Gdx.files.absolute(path))
                    : new Texture(path);
                Image tileImg = new Image(new TextureRegionDrawable(new TextureRegion(tilePreviewTextures[i])));
                tilePreviewTable.add(tileImg).size(32, 32).pad(1);
            } catch (Exception e) {
                // Tile image not found — skip
            }
        }
    }

    private void rebuildTilesetDropdown() {
        String[] names = new String[tilesets.size()];
        for (int i = 0; i < tilesets.size(); i++) names[i] = tilesets.get(i).name;
        tilesetSelect.setItems(names);
    }

    // ========== ACTIONS ==========

    private void doStep() {
        if (tiledMode) {
            if (tiledWfc == null) doReset();
            if (tiledWfc != null && !tiledWfc.isFinished() && !tiledWfc.isContradiction()) {
                tiledWfc.runOneRound();
                grid = tiledWfc.getGrid();
                rots = tiledWfc.getRots();
                lightEffectX = tiledWfc.getLastModifiedX();
                lightEffectY = tiledWfc.getLastModifiedY();
                lightEffect = 0.6f;
                tiledCompositeDirty = true;
                stepCount++;
                updateStatusLabels();
            }
        } else {
            if (overlappingWfc == null) doReset();
            if (overlappingWfc != null && !overlappingWfc.isFinished() && !overlappingWfc.isContradiction()) {
                overlappingWfc.runOneRound();
                overlappingTextureDirty = true;
                stepCount++;
                updateStatusLabels();
            }
        }
    }

    private void toggleRun() {
        autoRunning = !autoRunning;
        runBtn.setText(autoRunning ? "Pause" : "Run");
        stepTimer = 0;
    }

    private void doReset() {
        autoRunning = false;
        runBtn.setText("Run");
        stepCount = 0;
        if (tiledMode) resetTiledMode();
        else resetOverlappingMode();
        updateStatusLabels();
    }

    private void resetTiledMode() {
        int gridW = parseIntSafe(gridWField.getText(), 28);
        int gridH = parseIntSafe(gridHField.getText(), 20);

        tiledWfc = new SimpleWFC(gridW, gridH, currentTileset.tileCount,
                currentTileset.emptyAllowed, currentTileset.rotationsAllowed);

        String seedText = seedField.getText().trim();
        if (!seedText.isEmpty()) tiledWfc.setSeed(parseIntSafe(seedText, 229));

        tiledWfc.setTilingHorizontal(tilingHCheck.isChecked());
        tiledWfc.setTilingVertical(tilingVCheck.isChecked());
        tiledWfc.setConstraints(currentTileset.constraints);

        tilePixelW = currentTileset.tilePixelW;
        tilePixelH = currentTileset.tilePixelH;

        disposeTileTextures();
        int texCount = currentTileset.tileCount - (currentTileset.emptyAllowed ? 1 : 0);
        tileTextures = new Texture[texCount];
        for (int i = 0; i < texCount; i++) {
            String path = currentTileset.tileDir != null
                ? currentTileset.tileDir + "/" + currentTileset.basename + (i + 1) + ".png"
                : currentTileset.basename + (i + 1) + ".png";
            tileTextures[i] = currentTileset.tileDir != null
                ? new Texture(Gdx.files.absolute(path))
                : new Texture(path);
        }

        grid = tiledWfc.getGrid();
        rots = tiledWfc.getRots();
        tiledCompositeDirty = true;

        overlappingWfc = null;
        disposeOverlappingTexture();
        statusLabel.setText("Ready");
    }

    private void resetOverlappingMode() {
        if (customSampleFile != null) {
            try { currentSample = ImageIO.read(customSampleFile); }
            catch (Exception e) { currentSample = null; }
        } else {
            currentSample = loadSampleAsBufferedImage(sampleSelect.getSelected());
        }

        if (currentSample == null) { statusLabel.setText("Error: cannot load sample"); return; }

        int outW = parseIntSafe(outWField.getText(), 128);
        int outH = parseIntSafe(outHField.getText(), 128);
        int inTileW = parseIntSafe(inputTileWField.getText(), 1);
        int inTileH = parseIntSafe(inputTileHField.getText(), 1);
        int patternSize = Integer.parseInt(patternSizeSelect.getSelected());
        int symmetry = Integer.parseInt(symmetrySelect.getSelected());

        overlappingWfc = new OverlappingWFC(currentSample, patternSize, outW, outH, symmetry, inTileW, inTileH);

        String seedText = seedField.getText().trim();
        if (!seedText.isEmpty()) overlappingWfc.setSeed(Long.parseLong(seedText));

        overlappingWfc.setTilingHorizontal(tilingHCheck.isChecked());
        overlappingWfc.setTilingVertical(tilingVCheck.isChecked());

        overlappingTextureDirty = true;
        tiledWfc = null;
        grid = null;
        rots = null;
        disposeTileTextures();

        statusLabel.setText("Ready (" + overlappingWfc.getPatternCount() + " patterns)");
    }

    // ========== File browsers ==========

    private void openFileBrowser() {
        new Thread(() -> {
            JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
            chooser.setDialogTitle("Select sample image");
            chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp", "gif"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                Gdx.app.postRunnable(() -> {
                    customSampleFile = selected;
                    customFileLabel.setText(selected.getName());
                    updateSamplePreview();
                });
            }
        }, "file-chooser").start();
    }

    private void openTilesetBrowser() {
        new Thread(() -> {
            JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
            chooser.setDialogTitle("Select tileset JSON");
            chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                Gdx.app.postRunnable(() -> {
                    try {
                        TilesetDef loaded = TilesetDef.loadFromJson(selected);
                        tilesets.add(loaded);
                        rebuildTilesetDropdown();
                        tilesetSelect.setSelectedIndex(tilesets.size() - 1);
                        currentTileset = loaded;
                        updateTilePreview();
                        statusLabel.setText("Loaded: " + loaded.name);
                    } catch (Exception e) {
                        statusLabel.setText("Error loading tileset: " + e.getMessage());
                    }
                });
            }
        }, "tileset-chooser").start();
    }

    // ========== Export ==========

    private void doExport() {
        boolean canExport = (tiledMode && tiledWfc != null && tiledWfc.isFinished())
            || (!tiledMode && overlappingWfc != null && overlappingWfc.isFinished());

        if (!canExport) { statusLabel.setText("Run to completion first"); return; }

        // Build default filename
        String defaultName;
        if (tiledMode) {
            int gw = parseIntSafe(gridWField.getText(), 28);
            int gh = parseIntSafe(gridHField.getText(), 20);
            defaultName = String.format("wfc_tiled_%dx%d_%d.png", gw, gh, System.currentTimeMillis() / 1000);
        } else {
            int ow = parseIntSafe(outWField.getText(), 128);
            int oh = parseIntSafe(outHField.getText(), 128);
            defaultName = String.format("wfc_overlap_%dx%d_%d.png", ow, oh, System.currentTimeMillis() / 1000);
        }

        new Thread(() -> {
            JFileChooser chooser = new JFileChooser(lastExportDir != null ? lastExportDir : new File(System.getProperty("user.dir")));
            chooser.setDialogTitle("Save WFC Output");
            chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
            chooser.setSelectedFile(new File(defaultName));
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                String path = selected.getAbsolutePath();
                if (!path.toLowerCase().endsWith(".png")) path += ".png";
                lastExportDir = selected.getParentFile();
                final String exportPath = path;
                Gdx.app.postRunnable(() -> {
                    try {
                        if (tiledMode) {
                            String tileDir = currentTileset.tileDir != null ? currentTileset.tileDir : ".";
                            TiledImageExporter.exportToFile(tiledWfc, tileDir,
                                    currentTileset.basename, tilePixelW, tilePixelH, exportPath);
                        } else {
                            overlappingWfc.saveToFile(exportPath);
                        }
                        statusLabel.setText("Saved: " + new File(exportPath).getName());
                    } catch (Exception e) {
                        statusLabel.setText("Export error: " + e.getMessage());
                    }
                });
            }
        }, "export-chooser").start();
    }

    // ========== Batch dialog ==========

    private void showBatchDialog() {
        if (batchWindow != null) { batchWindow.remove(); batchWindow = null; }

        batchWindow = new Window("Batch Generate", skin);
        batchWindow.setModal(true);
        batchWindow.setMovable(true);

        Table content = new Table(skin);
        content.pad(10);

        content.add(new Label("Count:", skin)).left().padRight(6);
        TextField countField = new TextField("10", skin);
        countField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        content.add(countField).width(80).row();

        content.add(new Label("Base seed:", skin)).left().padRight(6);
        TextField baseSeedField = new TextField(seedField.getText(), skin);
        baseSeedField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        content.add(baseSeedField).width(80).row();

        Label dirLabel = new Label("(choose folder)", skin);
        final File[] outputDir = {null};

        content.add(new Label("", skin));
        TextButton chooseDirBtn = new TextButton("Choose Folder...", skin);
        chooseDirBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                new Thread(() -> {
                    JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
                    chooser.setDialogTitle("Select output folder");
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        Gdx.app.postRunnable(() -> {
                            outputDir[0] = chooser.getSelectedFile();
                            dirLabel.setText(outputDir[0].getName() + "/");
                        });
                    }
                }, "batch-dir-chooser").start();
            }
        });
        content.add(chooseDirBtn).fillX().row();

        content.add(dirLabel).colspan(2).left().padTop(2).row();

        content.add(new Label("", skin)).height(8).colspan(2).row();

        Table btns = new Table();
        TextButton genBtn = new TextButton("Generate", skin);
        genBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (outputDir[0] == null) { statusLabel.setText("Choose a folder first"); return; }
                int count = parseIntSafe(countField.getText(), 10);
                long baseSeed = Long.parseLong(baseSeedField.getText().isEmpty() ? "0" : baseSeedField.getText());
                long[] seeds = BatchRunner.generateSeeds(baseSeed, count);
                String dir = outputDir[0].getAbsolutePath();

                batchWindow.remove();
                batchWindow = null;
                statusLabel.setText("Batch generating " + count + "...");

                new Thread(() -> {
                    try {
                        if (tiledMode) {
                            String tileDir = currentTileset.tileDir != null ? currentTileset.tileDir : ".";
                            BatchRunner.runTiledBatch(
                                parseIntSafe(gridWField.getText(), 28),
                                parseIntSafe(gridHField.getText(), 20),
                                currentTileset.tileCount,
                                currentTileset.emptyAllowed, currentTileset.rotationsAllowed,
                                tilingHCheck.isChecked(), tilingVCheck.isChecked(),
                                currentTileset.constraints,
                                tileDir, currentTileset.basename,
                                currentTileset.tilePixelW, currentTileset.tilePixelH,
                                dir, "wfc", seeds);
                        } else {
                            BufferedImage sample;
                            if (customSampleFile != null) sample = ImageIO.read(customSampleFile);
                            else sample = loadSampleAsBufferedImage(sampleSelect.getSelected());

                            if (sample != null) {
                                BatchRunner.runOverlappingBatch(sample,
                                    Integer.parseInt(patternSizeSelect.getSelected()),
                                    parseIntSafe(outWField.getText(), 128),
                                    parseIntSafe(outHField.getText(), 128),
                                    Integer.parseInt(symmetrySelect.getSelected()),
                                    tilingHCheck.isChecked(), tilingVCheck.isChecked(),
                                    dir, "wfc", seeds);
                            }
                        }
                        Gdx.app.postRunnable(() ->
                            statusLabel.setText("Batch done: " + count + " files in " + outputDir[0].getName() + "/"));
                    } catch (Exception ex) {
                        Gdx.app.postRunnable(() ->
                            statusLabel.setText("Batch error: " + ex.getMessage()));
                    }
                }, "batch-runner").start();
            }
        });
        btns.add(genBtn).width(100).padRight(6);

        TextButton cancelBtn = new TextButton("Cancel", skin);
        cancelBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                batchWindow.remove();
                batchWindow = null;
            }
        });
        btns.add(cancelBtn).width(100);
        content.add(btns).colspan(2).row();

        batchWindow.add(content);
        batchWindow.pack();
        batchWindow.setPosition(
            (Gdx.graphics.getWidth() - batchWindow.getWidth()) / 2,
            (Gdx.graphics.getHeight() - batchWindow.getHeight()) / 2);
        stage.addActor(batchWindow);
    }

    // ========== Status ==========

    private void updateStatusLabels() {
        stepsLabel.setText("Steps: " + stepCount);
        if (tiledMode && tiledWfc != null) {
            backtracksLabel.setText("Backtracks: " + tiledWfc.getBacktrackCount());
            if (tiledWfc.isFinished()) {
                statusLabel.setText("Finished");
                autoRunning = false; runBtn.setText("Run");
            } else if (tiledWfc.isContradiction()) {
                statusLabel.setText("Contradiction!");
                autoRunning = false; runBtn.setText("Run");
            } else {
                statusLabel.setText("Running...");
            }
        } else if (!tiledMode && overlappingWfc != null) {
            backtracksLabel.setText("Backtracks: " + overlappingWfc.getBacktrackCount());
            if (overlappingWfc.isFinished()) {
                statusLabel.setText("Finished");
                autoRunning = false; runBtn.setText("Run");
            } else if (overlappingWfc.isContradiction()) {
                statusLabel.setText("Contradiction!");
                autoRunning = false; runBtn.setText("Run");
            } else {
                statusLabel.setText("Running...");
            }
        }
    }

    // ========== RENDERING ==========

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.18f, 1);

        if (autoRunning) {
            stepTimer += Gdx.graphics.getDeltaTime();
            if (stepTimer >= stepInterval) {
                stepTimer -= stepInterval;
                doStep();
            }
        }

        if (tiledMode) renderTiledOutput();
        else renderOverlappingOutput();

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    private void renderTiledOutput() {
        if (grid == null || tileTextures == null) return;

        boolean show2x2 = showTilingCheck.isChecked() && tiledWfc != null && tiledWfc.isFinished();

        if (show2x2) {
            renderTiled2x2();
            return;
        }

        int gridW = grid.length;
        int gridH = grid[0].length;

        if (lightEffect > 0) {
            shapeRenderer.begin(ShapeType.Filled);
            float t = lightEffect;
            shapeRenderer.setColor(t, t, t * 0.5f, 1.0f);
            shapeRenderer.rect(PANEL_WIDTH + lightEffectX * tilePixelW, lightEffectY * tilePixelH, tilePixelW, tilePixelH);
            shapeRenderer.end();
            lightEffect -= 0.04f;
        }

        batch.begin();
        for (int x = 0; x < gridW; x++) {
            for (int y = 0; y < gridH; y++) {
                int[] possibilities = grid[x][y];
                int[] rotations = rots[x][y];
                for (int i = 0; i < possibilities.length; i++) {
                    int rot = rotations[i], tile = possibilities[i];
                    if (tile > 0 && tile - 1 < tileTextures.length) {
                        for (int j = 0; j < 4; j++) {
                            if ((rot & (1 << j)) > 0) {
                                Sprite s = new Sprite(tileTextures[tile - 1]);
                                s.setOriginCenter();
                                s.setRotation(-j * 90);
                                s.setPosition(PANEL_WIDTH + x * tilePixelW, y * tilePixelH);
                                batch.setBlendFunction(GL20.GL_CONSTANT_COLOR, GL20.GL_ONE_MINUS_SRC_ALPHA);
                                s.draw(batch);
                                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                            }
                        }
                    }
                }
            }
        }
        batch.end();
    }

    private void renderTiled2x2() {
        if (tiledCompositeDirty) {
            disposeTiledComposite();
            try {
                String tileDir = currentTileset.tileDir != null ? currentTileset.tileDir : ".";
                BufferedImage img = TiledImageExporter.export(tiledWfc, tileDir,
                        currentTileset.basename, tilePixelW, tilePixelH);
                tiledCompositeTexture = bufferedImageToTexture(img);
                tiledCompositeTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            } catch (Exception e) {
                System.err.println("Could not composite tiled output: " + e.getMessage());
            }
            tiledCompositeDirty = false;
        }
        if (tiledCompositeTexture != null) {
            drawTexture2x2(tiledCompositeTexture);
        }
    }

    private void renderOverlappingOutput() {
        if (overlappingWfc == null) return;

        if (overlappingTextureDirty) {
            disposeOverlappingTexture();
            BufferedImage img = overlappingWfc.getOutputImage();
            if (img != null) {
                overlappingTexture = bufferedImageToTexture(img);
                overlappingTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            }
            overlappingTextureDirty = false;
        }

        if (overlappingTexture == null) return;

        if (showTilingCheck.isChecked()) {
            drawTexture2x2(overlappingTexture);
        } else {
            drawTextureFit(overlappingTexture);
        }
    }

    private void drawTextureFit(Texture tex) {
        float canvasW = Gdx.graphics.getWidth() - PANEL_WIDTH;
        float canvasH = Gdx.graphics.getHeight();
        float scale = Math.min(canvasW / tex.getWidth(), canvasH / tex.getHeight());
        float drawW = tex.getWidth() * scale, drawH = tex.getHeight() * scale;
        float drawX = PANEL_WIDTH + (canvasW - drawW) / 2;
        float drawY = (canvasH - drawH) / 2;
        batch.begin();
        batch.draw(tex, drawX, drawY, drawW, drawH);
        batch.end();
    }

    private void drawTexture2x2(Texture tex) {
        float canvasW = Gdx.graphics.getWidth() - PANEL_WIDTH;
        float canvasH = Gdx.graphics.getHeight();
        float scale = Math.min(canvasW / (tex.getWidth() * 2f), canvasH / (tex.getHeight() * 2f));
        float tileW = tex.getWidth() * scale, tileH = tex.getHeight() * scale;
        float baseX = PANEL_WIDTH + (canvasW - tileW * 2) / 2;
        float baseY = (canvasH - tileH * 2) / 2;
        batch.begin();
        for (int ty = 0; ty < 2; ty++)
            for (int tx = 0; tx < 2; tx++)
                batch.draw(tex, baseX + tx * tileW, baseY + ty * tileH, tileW, tileH);
        batch.end();
    }

    private Texture bufferedImageToTexture(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                pixmap.drawPixel(x, y, ((argb >> 16) & 0xFF) << 24 | ((argb >> 8) & 0xFF) << 16
                    | (argb & 0xFF) << 8 | ((argb >> 24) & 0xFF));
            }
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    // ========== LIFECYCLE ==========

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        stage.dispose();
        skin.dispose();
        disposeTileTextures();
        disposeOverlappingTexture();
        disposeTiledComposite();
        disposeSamplePreview();
        disposeTilePreview();
    }

    private void disposeTileTextures() {
        if (tileTextures != null) {
            for (Texture t : tileTextures) if (t != null) t.dispose();
            tileTextures = null;
        }
    }

    private void disposeOverlappingTexture() {
        if (overlappingTexture != null) { overlappingTexture.dispose(); overlappingTexture = null; }
    }

    private void disposeTiledComposite() {
        if (tiledCompositeTexture != null) { tiledCompositeTexture.dispose(); tiledCompositeTexture = null; }
    }

    private void disposeSamplePreview() {
        if (samplePreviewTexture != null) { samplePreviewTexture.dispose(); samplePreviewTexture = null; }
    }

    private void disposeTilePreview() {
        if (tilePreviewTextures != null) {
            for (Texture t : tilePreviewTextures) if (t != null) t.dispose();
            tilePreviewTextures = null;
        }
    }

    private int parseIntSafe(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }
}
