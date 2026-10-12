package pl.ratrynek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public static class Rule {
        public String item = "";
        public long maxPrice = 0;
    }

    public boolean enabled = false;
    public String command = "/ah";
    public int intervalMs = 1000;
    public List<Rule> rules = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ratrynek.json");
    private static Config instance = new Config();

    public static Config get() { return instance; }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                Config c = GSON.fromJson(Files.readString(PATH), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception ignored) {}
        instance.enabled = false; // zawsze startuje wylaczony
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(instance));
        } catch (Exception ignored) {}
    }
}
