package com.bettertntrun;

import com.bettertntrun.commands.TntRunCommand;
import com.bettertntrun.commands.TntRunTabCompleter;
import com.bettertntrun.listeners.MapProtectionListener;
import com.bettertntrun.listeners.NPCInteractListener;
import com.bettertntrun.listeners.PlayerMoveListener;
import com.bettertntrun.listeners.PlayerQuitListener;
import com.bettertntrun.managers.ConfigManager;
import com.bettertntrun.managers.GameManager;
import com.bettertntrun.managers.NPCManager;
import com.bettertntrun.world.VoidChunkGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public class BetterTntRun extends JavaPlugin {

    private static BetterTntRun instance;
    private ConfigManager configManager;
    private GameManager gameManager;
    private NPCManager npcManager;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        gameManager = new GameManager(this);
        npcManager = new NPCManager(this);

        getCommand("tntrun").setExecutor(new TntRunCommand(this));
        getCommand("tntrun").setTabCompleter(new TntRunTabCompleter(this));

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new NPCInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new MapProtectionListener(this), this);

        // Load ArmorStand NPCs after a tick to ensure worlds are loaded
        getServer().getScheduler().runTaskLater(this, () -> npcManager.loadAllNPCs(), 20L);

        // Start lone player redistribution check every 30 seconds
        getServer().getScheduler().runTaskTimer(this, () -> gameManager.checkLonePlayers(), 600L, 600L);

        getLogger().info("BetterTntRun v5 enabled! (ArmorStand NPCs, instance reuse, smart matchmaking)");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopAllGames();
        }
        if (npcManager != null) {
            npcManager.removeAllNPCs();
        }
        getLogger().info("BetterTntRun disabled!");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if ("tntrun_games".equals(worldName)) {
            return new VoidChunkGenerator();
        }
        return null;
    }

    public static BetterTntRun getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public GameManager getGameManager() { return gameManager; }
    public NPCManager getNpcManager() { return npcManager; }
}
