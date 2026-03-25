package com.bettertntrun.listeners;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.game.Game;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerMoveListener implements Listener {

    private final BetterTntRun plugin;
    private final Set<Location> scheduledBlocks = new HashSet<>();

    public PlayerMoveListener(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String mapName = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (mapName == null) return;

        Game game = plugin.getGameManager().getGame(mapName);
        if (game == null || !game.isRunning() || !game.isAlive(player.getUniqueId())) return;

        // Check fall
        if (player.getLocation().getY() < 0) {
            game.eliminatePlayer(player);
            return;
        }

        // TNT mechanic
        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        if (below.getType() == Material.TNT && !scheduledBlocks.contains(below.getLocation())) {
            scheduledBlocks.add(below.getLocation());
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (below.getType() == Material.TNT) {
                        below.setType(Material.AIR);
                    }
                    scheduledBlocks.remove(below.getLocation());
                }
            }.runTaskLater(plugin, 20L);
        }
    }
}
