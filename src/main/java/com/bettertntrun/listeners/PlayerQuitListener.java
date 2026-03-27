package com.bettertntrun.listeners;

import com.bettertntrun.BetterTntRun;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final BetterTntRun plugin;

    public PlayerQuitListener(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getGameManager().leaveGame(event.getPlayer());
    }
}
