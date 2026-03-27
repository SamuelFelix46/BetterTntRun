package com.bettertntrun.listeners;

import com.bettertntrun.BetterTntRun;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public class MapProtectionListener implements Listener {

    private final BetterTntRun plugin;

    public MapProtectionListener(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
            event.setCancelled(true);
            return;
        }

        if (isInGameWorld(event.getBlock().getLocation()) && !player.hasPermission("bettertntrun.admin")) {
            event.setCancelled(true);
            player.sendMessage("Â§cVous ne pouvez pas casser des blocs ici !");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
            event.setCancelled(true);
            return;
        }

        if (isInGameWorld(event.getBlock().getLocation()) && !player.hasPermission("bettertntrun.admin")) {
            event.setCancelled(true);
            player.sendMessage("Â§cVous ne pouvez pas placer des blocs ici !");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (plugin.getGameManager().getPlayerGame(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = getDamagingPlayer(event.getDamager());
        if (attacker == null) return;

        if (plugin.getGameManager().getPlayerGame(victim.getUniqueId()) != null
                || plugin.getGameManager().getPlayerGame(attacker.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        if (isInGameWorld(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private Player getDamagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    private boolean isInGameWorld(Location loc) {
        World gameWorld = plugin.getGameManager().getGameWorld();
        return gameWorld != null && loc.getWorld().equals(gameWorld);
    }
}
