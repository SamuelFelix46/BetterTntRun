package com.bettertntrun.listeners;

import com.bettertntrun.BetterTntRun;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCInteractListener implements Listener {

    private final BetterTntRun plugin;

    public NPCInteractListener(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getNpcManager().isNPC(event.getRightClicked())) return;

        event.setCancelled(true);
        handleInteraction(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!plugin.getNpcManager().isNPC(event.getRightClicked())) return;

        event.setCancelled(true);
        handleInteraction(event.getPlayer(), event.getRightClicked());
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (plugin.getNpcManager().isNPC(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent event) {
        if (plugin.getNpcManager().isNPC(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void handleInteraction(Player player, Entity entity) {
        if (!plugin.getNpcManager().isNPC(entity)) return;

        if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
            player.sendMessage("Â§cVous Ãªtes dÃ©jÃ  dans une partie !");
            return;
        }

        if (plugin.getGameManager().joinBestGame(player)) {
            player.sendMessage("Â§aVous avez rejoint une partie !");
        } else {
            player.sendMessage("Â§cAucune partie disponible pour le moment.");
        }
    }
}
