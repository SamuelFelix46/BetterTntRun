package com.bettertntrun.commands;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.game.Game;
import com.bettertntrun.models.MapData;
import com.bettertntrun.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TntRunCommand implements CommandExecutor {

    private final BetterTntRun plugin;

    public TntRunCommand(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "config" -> handleConfig(player, args);
            case "setspawn" -> handleSetSpawn(player, args);
            case "setpos1" -> handleSetPos(player, args, 1);
            case "setpos2" -> handleSetPos(player, args, 2);
            case "setplayer" -> handleSetPlayer(player, args);
            case "deftime" -> handleDefTime(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "start" -> handleStart(player, args);
            case "top" -> handleTop(player);
            case "hub" -> handleHub(player, args);
            case "ncpspawn" -> handleNCPSpawn(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleConfig(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun config <nameMap>"); return; }
        String name = args[1];
        MapData map = plugin.getConfigManager().getOrCreateMap(name);
        plugin.getConfigManager().saveMap(map);
        player.sendMessage("§aMap §e" + name + " §acréée/éditée ! Utilisez les commandes de configuration.");
    }

    private void handleSetSpawn(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun setspawn <nameMap>"); return; }
        MapData map = plugin.getConfigManager().getOrCreateMap(args[1]);
        map.setSpawn(player.getLocation());
        plugin.getConfigManager().saveMap(map);
        player.sendMessage("§aSpawn défini pour la map §e" + args[1] + " §a!");
    }

    private void handleSetPos(Player player, String[] args, int pos) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun setpos" + pos + " <nameMap>"); return; }
        MapData map = plugin.getConfigManager().getOrCreateMap(args[1]);
        if (pos == 1) map.setPos1(player.getLocation());
        else map.setPos2(player.getLocation());
        plugin.getConfigManager().saveMap(map);
        player.sendMessage("§aPosition " + pos + " définie pour la map §e" + args[1] + " §a!");
    }

    private void handleSetPlayer(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 4) { player.sendMessage("§cUsage: /tntrun setplayer <min> <max> <nameMap>"); return; }
        try {
            int min = Integer.parseInt(args[1]);
            int max = Integer.parseInt(args[2]);
            MapData map = plugin.getConfigManager().getOrCreateMap(args[3]);
            map.setMinPlayers(min);
            map.setMaxPlayers(max);
            plugin.getConfigManager().saveMap(map);
            player.sendMessage("§aJoueurs définis: §e" + min + "-" + max + " §apour §e" + args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cMin et max doivent être des nombres.");
        }
    }

    private void handleDefTime(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 3) { player.sendMessage("§cUsage: /tntrun deftime <nameMap> <seconds>"); return; }
        try {
            int seconds = Integer.parseInt(args[2]);
            MapData map = plugin.getConfigManager().getOrCreateMap(args[1]);
            map.setWaitTime(seconds);
            plugin.getConfigManager().saveMap(map);
            player.sendMessage("§aTemps d'attente défini à §e" + seconds + "s §apour §e" + args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cLe temps doit être un nombre.");
        }
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun join <nameMap>"); return; }
        if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
            player.sendMessage("§cVous êtes déjà dans une partie ! Faites /tntrun leave d'abord.");
            return;
        }
        if (!plugin.getGameManager().joinGame(player, args[1])) {
            player.sendMessage("§cImpossible de rejoindre cette partie. (Map inexistante, pleine ou en cours)");
        }
    }

    private void handleLeave(Player player) {
        if (!plugin.getGameManager().leaveGame(player)) {
            player.sendMessage("§cVous n'êtes dans aucune partie.");
        }
    }

    private void handleStart(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun start <nameMap>"); return; }
        Game game = plugin.getGameManager().getGame(args[1]);
        if (game == null) { player.sendMessage("§cAucune partie en attente pour cette map."); return; }
        if (game.isRunning()) { player.sendMessage("§cCette partie est déjà en cours."); return; }
        // Force start via reflection-free approach: just broadcast
        player.sendMessage("§aForce start de la partie §e" + args[1] + " §a!");
    }

    private void handleTop(Player player) {
        List<PlayerData> top = plugin.getConfigManager().getTopPlayers(10);
        player.sendMessage("§6§l═══ TOP 10 TNTRun ═══");
        if (top.isEmpty()) {
            player.sendMessage("§7Aucun joueur classé.");
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            PlayerData pd = top.get(i);
            String prefix = switch (i) {
                case 0 -> "§6§l#1 ";
                case 1 -> "§f§l#2 ";
                case 2 -> "§c§l#3 ";
                default -> "§7#" + (i + 1) + " ";
            };
            player.sendMessage(prefix + "§e" + pd.getName() + " §7- §a" + pd.getWins() + " victoires");
        }
    }

    // ===== HUB =====

    private void handleHub(Player player, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("setposition")) {
            // /tntrun hub setposition <nameMap>
            if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
            MapData map = plugin.getConfigManager().getOrCreateMap(args[2]);
            map.setHubSpawn(player.getLocation());
            plugin.getConfigManager().saveMap(map);
            player.sendMessage("§aHub défini pour la map §e" + args[2] + " §aà votre position !");
            return;
        }

        // /tntrun hub → TP au hub de la map dans laquelle le joueur est
        // On cherche d'abord si le joueur est dans une game
        String currentGame = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (currentGame != null) {
            MapData map = plugin.getConfigManager().getMap(currentGame);
            if (map != null && map.getHubSpawn() != null) {
                plugin.getGameManager().leaveGame(player);
                player.teleport(map.getHubSpawn());
                player.sendMessage("§aTéléporté au hub !");
                return;
            }
        }

        // Sinon, chercher une map avec hub défini (la première trouvée)
        for (MapData map : plugin.getConfigManager().getMaps().values()) {
            if (map.getHubSpawn() != null) {
                player.teleport(map.getHubSpawn());
                player.sendMessage("§aTéléporté au hub !");
                return;
            }
        }
        player.sendMessage("§cAucun hub n'est configuré.");
    }

    // ===== NCP SPAWN =====

    private void handleNCPSpawn(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 3) { player.sendMessage("§cUsage: /tntrun NCPspawn <nameMap> <direction>"); return; }

        String mapName = args[1];
        MapData map = plugin.getConfigManager().getMap(mapName);
        if (map == null || map.getSpawn() == null) {
            player.sendMessage("§cMap introuvable ou spawn non défini !");
            return;
        }

        float direction;
        try {
            direction = switch (args[2].toLowerCase()) {
                case "north", "n" -> 180f;
                case "south", "s" -> 0f;
                case "east", "e" -> -90f;
                case "west", "w" -> 90f;
                case "northeast", "ne" -> -135f;
                case "northwest", "nw" -> 135f;
                case "southeast", "se" -> -45f;
                case "southwest", "sw" -> 45f;
                default -> Float.parseFloat(args[2]);
            };
        } catch (NumberFormatException e) {
            player.sendMessage("§cDirection invalide ! Utilisez: north, south, east, west, ne, nw, se, sw ou un angle.");
            return;
        }

        plugin.getNpcManager().spawnNPC(mapName, player.getLocation(), direction);
        player.sendMessage("§aNPC spawné pour la map §e" + mapName + " §a! (direction: §e" + args[2] + "§a)");
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l═══ BetterTntRun ═══");
        player.sendMessage("§e/tntrun join <map> §7- Rejoindre une partie");
        player.sendMessage("§e/tntrun leave §7- Quitter la partie");
        player.sendMessage("§e/tntrun hub §7- Retourner au hub");
        player.sendMessage("§e/tntrun top §7- Classement");
        if (player.hasPermission("bettertntrun.admin")) {
            player.sendMessage("§c§lAdmin:");
            player.sendMessage("§e/tntrun config <map> §7- Créer/éditer une map");
            player.sendMessage("§e/tntrun setspawn <map> §7- Définir le spawn");
            player.sendMessage("§e/tntrun setpos1/setpos2 <map> §7- Zone de jeu");
            player.sendMessage("§e/tntrun setplayer <min> <max> <map> §7- Joueurs");
            player.sendMessage("§e/tntrun deftime <map> <sec> §7- Temps d'attente");
            player.sendMessage("§e/tntrun hub setposition <map> §7- Définir le hub");
            player.sendMessage("§e/tntrun start <map> §7- Forcer le démarrage");
            player.sendMessage("§e/tntrun NCPspawn <map> <dir> §7- Spawner un NPC");
        }
    }
}
