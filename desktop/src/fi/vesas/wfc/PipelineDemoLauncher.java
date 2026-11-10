package fi.vesas.wfc;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class PipelineDemoLauncher {

    public static void main(String[] args) {
        new Lwjgl3Application(new PipelineDemoApp(), defaultConfig());
    }

    private static Lwjgl3ApplicationConfiguration defaultConfig() {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Pipeline demos");
        cfg.useVsync(true);
        cfg.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        cfg.setWindowedMode(1280, 800);
        return cfg;
    }
}
