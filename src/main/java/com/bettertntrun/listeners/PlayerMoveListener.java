package com.bettertntrun.listeners;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {

    private final BetterTntRun plugin;

    public PlayerMoveListener(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String instanceId = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (instanceId == null) return;

        Game game = plugin.getGameManager().getGame(instanceId);
        if (game == null || !game.isRunning() || !game.isAlive(player.getUniqueId())) return;

        if (player.getLocation().getY() < game.getEliminationY()) {
            game.eliminatePlayer(player);
        }
    }
}
