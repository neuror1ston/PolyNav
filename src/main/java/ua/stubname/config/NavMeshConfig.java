package ua.stubname.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NavMeshConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public double defaultCost = 1.0;
    public double pathCost = 0.5;
    public double stoneCost = 0.8;
    public double waterCost = 1000.0;
    public double lavaCost = 100000.0;
    public double climbCost = 2.0;

    public float agentRadius = 0.35f;
    public float agentHeight = 1.95f;
    public float maxStepHeight = 1.0f;
    public float heightResolution = 0.125f; // Conquest Reforged 1/8 layer step
    public boolean allowWater = false; // Strict water avoidance by default

    // Block ID -> Cost multiplier
    public Map<String, Double> blockCosts = new HashMap<>();

    public static File getConfigFile() {
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                return new File(FabricLoader.getInstance().getConfigDir().toFile(), "npcnavs/costs.json");
            }
        } catch (Throwable ignored) {
        }
        return new File("config/npcnavs/costs.json");
    }

    public static NavMeshConfig load() {
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            NavMeshConfig config = new NavMeshConfig();
            config.initDefaults();
            config.save();
            return config;
        }

        try (FileReader reader = new FileReader(configFile)) {
            NavMeshConfig config = GSON.fromJson(reader, NavMeshConfig.class);
            if (config == null) {
                config = new NavMeshConfig();
                config.initDefaults();
            }
            return config;
        } catch (Exception e) {
            e.printStackTrace();
            NavMeshConfig config = new NavMeshConfig();
            config.initDefaults();
            return config;
        }
    }

    public void initDefaults() {
        blockCosts.put("minecraft:dirt_path", 0.5);
        blockCosts.put("minecraft:stone_bricks", 0.7);
        blockCosts.put("minecraft:stone", 0.8);
        blockCosts.put("minecraft:cobblestone", 0.85);
        blockCosts.put("minecraft:grass_block", 1.0);
        blockCosts.put("minecraft:sand", 1.4);
        blockCosts.put("minecraft:gravel", 1.2);
        blockCosts.put("minecraft:soul_sand", 3.0);
        blockCosts.put("minecraft:water", 1000.0);
        blockCosts.put("minecraft:lava", 100000.0);
    }

    public void save() {
        File configFile = getConfigFile();
        try {
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getBlockCost(String blockId) {
        Double cost = blockCosts.get(blockId);
        return cost != null ? cost : defaultCost;
    }
}
