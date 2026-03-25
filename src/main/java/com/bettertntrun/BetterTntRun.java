package com.bettertntrun;

import com.bettertntrun.commands.TntRunCommand;
import com.bettertntrun.commands.TntRunTabCompleter;
import com.bettertntrun.listeners.NPCInteractListener;
import com.bettertntrun.listeners.PlayerMoveListener;
import com.bettertntrun.listeners.PlayerQuitListener;
import com.bettertntrun.managers.ConfigManager;
import com.bettertntrun.managers.GameManager;
import com.bettertntrun.managers.NPCManager;
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

        npcManager.loadAllNPCs();

        getLogger().info("BetterTntRun enabled!");
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

    public static BetterTntRun getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public GameManager getGameManager() { return gameManager; }
    public NPCManager getNpcManager() { return npcManager; }
}
