package fi.vesas.wfc;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;

import fi.vesas.wfc.SimpleWFC.Constraints;
import fi.vesas.wfc.SimpleWFC.DIR;

public class MyGdxGame extends ApplicationAdapter {

	private Stage stage;

	SpriteBatch batch;
	ShapeRenderer shaperend;
	Texture [] textures;

	private int[][][] grid = null;
	private int[][][] rots = null;
	private SimpleWFC wfc = null;

	float lighteffect = 0;
	int lighteffect_x = 0;
	int lighteffect_y = 0;

	int TILE_W = 32;
	int TILE_H = 32;
	int GRID_W = 28;
	int GRID_H = 20;
	
	@Override
	public void create () {
		
		stage = new Stage();
		Gdx.input.setInputProcessor(stage);

		// final TextButton button = new TextButton("Click me!", skin);
		// table.add(button);

		/*
		button.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				System.out.println("Clicked! Is checked: " + button.isChecked());
				button.setText("Good job!");
			}
		});
		 
		table.setDebug(true); // This is optional, but enables debug lines for tables.
		*/

		batch = new SpriteBatch();

		shaperend = new ShapeRenderer();
    
		int tileCount = 9;
		boolean emptyAllowed = true;
		boolean rotationsAllowed = true;
		String basename = "circ";

		if(emptyAllowed)
			// do not need texture for empty tile
			textures = new Texture[tileCount-1];
		else {
			textures = new Texture[tileCount];
		}

		for(int i = 0; i < textures.length; i++) {
			textures[i] = new Texture(String.format("%s%d.png", basename, i+1));
		}

		wfc = new SimpleWFC(GRID_W,GRID_H, tileCount, emptyAllowed, rotationsAllowed);
        wfc.setSeed(229);

		wfc.setTilingVertical(true);
		wfc.setTilingHorizontal(true);

        Constraints constraints = new Constraints();

		constraints.addPort(1, DIR.S, 1);
		constraints.addPort(1, DIR.E, 1);
		constraints.addPort(1, DIR.N, 1);
		constraints.addPort(1, DIR.W, 1);

		constraints.addPort(2, DIR.S, 1);
		constraints.addPort(2, DIR.N, 2);

		constraints.addPort(3, DIR.N, 2);

		constraints.addPort(4, DIR.N, 2);
		constraints.addPort(4, DIR.W, 2);

		constraints.addPort(5, DIR.S, 1);

		constraints.addPort(6, DIR.N, 2);
		constraints.addPort(6, DIR.W, 2);
		constraints.addPort(6, DIR.E, 2);

		constraints.addPort(7, DIR.N, 2);
		constraints.addPort(7, DIR.S, 2);

		constraints.addPort(8, DIR.N, 2);
		constraints.addPort(8, DIR.S, 2);

		wfc.setConstraints(constraints);
		wfc.printConstraints();
		grid = wfc.getGrid();
		rots = wfc.getRots();

		Gdx.input.setInputProcessor(new InputAdapter() {

            @Override
            public boolean keyTyped (char key) {

				if(key == '1') {
					runAll();
				}
				else if(key == '2') {
					saveTexture();
				}
				else if(key == ' ') {
					runRound();
				}
				else if(key == 'g')  {
					wfc.printGrid();
				}
				else if(key == 'c')  {
					wfc.printConstraints();
				}
				else if(key == 'r')  {
					wfc.printRotations();
				}
				else if(key == 'o')  {
					wfc.observe();
				}
				else if(key == 'p')  {
					wfc.propagate();
				}
                return true;
            }
        });
		
	}

	private void saveTexture() {

	}

	private void runAll() {

		while(true) {

			wfc.runOneRound();
		
			this.lighteffect_x = wfc.getLastModifiedX();
			this.lighteffect_y = wfc.getLastModifiedY();
			this.lighteffect = 0.6f;

			if(wfc.isFinished())
				break;
		}
	}

	private void runRound() {

		wfc.runOneRound();
		
		grid = wfc.getGrid();
		rots = wfc.getRots();

		this.lighteffect_x = wfc.getLastModifiedX();
		this.lighteffect_y = wfc.getLastModifiedY();
		this.lighteffect = 0.6f;
	}

	@Override
	public void render () {

		ScreenUtils.clear(0.95f, 0.95f, 0.95f, 1);

		if(this.lighteffect > 0) {
			shaperend.begin(ShapeType.Filled);
			float t = this.lighteffect;
			shaperend.setColor(t,t,t*0.5f,1.0f);
			shaperend.rect(lighteffect_x*TILE_W, lighteffect_y*TILE_H, TILE_W, TILE_H);
			shaperend.end();
			this.lighteffect = this.lighteffect - 0.04f;
		}
		
		batch.begin();

		
		for(int x = 0; x < GRID_W; x++) {
			for(int y = 0; y < GRID_H; y++) {
			
				int [] possibilities = grid[x][y];
				int [] rotations = rots[x][y];

                for(int i= 0; i < possibilities.length;i++) {

					int rot = rotations[i];
					int tile = possibilities[i];

					if(tile > 0) {
						// bits 1-4
						for(int j = 0; j < 4; j++) {
							int temp = rot & (1 << (j));

							if(temp > 0) {
								Sprite s=new Sprite(textures[tile-1]);
								s.setOriginCenter();
								s.setRotation(-j * 90);
								s.setPosition(x*TILE_W,y*TILE_H);
								// batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
								// batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                                // s.draw(batch);
								batch.setBlendFunction(GL20.GL_CONSTANT_COLOR, GL20.GL_ONE_MINUS_SRC_ALPHA);
                                // batch.setBlendColor(0.5f, 0.5f, 0.5f, 1.0f);  // 0.5 for 50/50 averaging
                                s.draw(batch);
                                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                                

							}
						}
					}
                }
			}
		}

		batch.end();

		stage.act(Gdx.graphics.getDeltaTime());
		stage.draw();
	}
	
	@Override
	public void dispose () {
		batch.dispose();
		stage.dispose();
	}

	public void resize (int width, int height) {
		stage.getViewport().update(width, height, true);
	}
	
	
}
