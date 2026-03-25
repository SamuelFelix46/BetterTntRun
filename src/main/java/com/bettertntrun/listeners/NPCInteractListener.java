package com.bettertntrun.listeners;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.models.MapData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

public class NPCInteractListener implements Listener {

    private final BetterTntRun plugin;

    public NPCInteractListener(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) return;
        if (!plugin.getNpcManager().isNPC(armorStand)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        String mapName = plugin.getNpcManager().getLinkedMap(armorStand.getUniqueId());
        if (mapName == null) return;

        MapData map = plugin.getConfigManager().getMap(mapName);
        if (map == null || map.getSpawn() == null) {
            player.sendMessage("§cCette map n'est pas correctement configurée.");
            return;
        }

        // TP to map spawn (join the game)
        if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
            player.sendMessage("§cVous êtes déjà dans une partie !");
            return;
        }

        if (plugin.getGameManager().joinGame(player, mapName)) {
            player.sendMessage("§aVous avez rejoint la partie §e" + mapName + " §a!");
        } else {
            player.sendMessage("§cImpossible de rejoindre. (Partie pleine ou en cours)");
        }
    }
}
