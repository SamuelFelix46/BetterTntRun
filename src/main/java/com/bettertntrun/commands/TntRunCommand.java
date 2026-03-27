package com.bettertntrun.commands;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.game.Game;
import com.bettertntrun.models.MapData;
import com.bettertntrun.models.PlayerData;
import com.bettertntrun.models.TemplateBlockData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class TntRunCommand implements CommandExecutor {

    private final BetterTntRun plugin;

    // Temporary storage for admin map building (pos1/pos2 before scanning)
    private final Map<UUID, Location> tempPos1 = new HashMap<>();
    private final Map<UUID, Location> tempPos2 = new HashMap<>();
    private final Map<UUID, String> tempMapName = new HashMap<>();

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
            case "scan" -> handleScan(player, args);
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
        player.sendMessage("§aMap §e" + name + " §acréée/éditée !");
        player.sendMessage("§7Étapes: §esetpos1§7, §esetpos2§7, §esetspawn§7, §escan§7, §ehub setposition");
    }

    /**
     * Set spawn: stored as an OFFSET relative to pos1 (the min corner).
     */
    private void handleSetSpawn(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun setspawn <nameMap>"); return; }

        String mapName = args[1];
        Location pos1 = tempPos1.get(player.getUniqueId());
        Location pos2 = tempPos2.get(player.getUniqueId());
        String storedMapName = tempMapName.get(player.getUniqueId());

        if (pos1 == null || pos2 == null || !mapName.equalsIgnoreCase(storedMapName)) {
            player.sendMessage("§cVeuillez d'abord définir pos1 et pos2 pour cette map !");
            return;
        }

        Location playerLoc = player.getLocation();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());

        double offsetX = playerLoc.getX() - minX;
        double offsetY = playerLoc.getY() - minY;
        double offsetZ = playerLoc.getZ() - minZ;

        MapData map = plugin.getConfigManager().getOrCreateMap(mapName);
        map.setSpawnOffset(offsetX, offsetY, offsetZ, playerLoc.getYaw(), playerLoc.getPitch());
        plugin.getConfigManager().saveMap(map);
        player.sendMessage("§aSpawn défini pour la map §e" + mapName + " §a(offset: " +
                String.format("%.1f, %.1f, %.1f", offsetX, offsetY, offsetZ) + ")");
    }

    private void handleSetPos(Player player, String[] args, int pos) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun setpos" + pos + " <nameMap>"); return; }

        String mapName = args[1];
        if (pos == 1) {
            tempPos1.put(player.getUniqueId(), player.getLocation());
        } else {
            tempPos2.put(player.getUniqueId(), player.getLocation());
        }
        tempMapName.put(player.getUniqueId(), mapName);

        player.sendMessage("§aPosition " + pos + " définie pour la map §e" + mapName + " §a!");

        if (tempPos1.containsKey(player.getUniqueId()) && tempPos2.containsKey(player.getUniqueId())) {
            player.sendMessage("§7Les deux positions sont définies. Utilisez §e/tntrun scan " + mapName + " §7pour enregistrer les blocs.");
        }
    }

    /**
     * Scan all blocks between pos1 and pos2, save as template.
     */
    private void handleScan(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun scan <nameMap>"); return; }

        String mapName = args[1];
        Location pos1 = tempPos1.get(player.getUniqueId());
        Location pos2 = tempPos2.get(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            player.sendMessage("§cVeuillez d'abord définir pos1 et pos2 !");
            return;
        }

        player.sendMessage("§eScan en cours...");

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        List<TemplateBlockData> templateBlocks = new ArrayList<>();
        List<int[]> tntPositions = new ArrayList<>();
        int blockCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = pos1.getWorld().getBlockAt(x, y, z);
                    Material mat = block.getType();
                    if (mat != Material.AIR) {
                        int relX = x - minX;
                        int relY = y - minY;
                        int relZ = z - minZ;
                        templateBlocks.add(new TemplateBlockData(relX, relY, relZ, block.getBlockData().getAsString()));
                        blockCount++;

                        if (mat == Material.TNT) {
                            tntPositions.add(new int[]{relX, relY, relZ});
                        }
                    }
                }
            }
        }

        MapData map = plugin.getConfigManager().getOrCreateMap(mapName);
        map.setTemplateBlocks(templateBlocks);
        map.setTntRelativePositions(tntPositions);
        map.setSize(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        plugin.getConfigManager().saveMap(map);

        player.sendMessage("§aScan terminé ! §e" + blockCount + " blocs §aenregistrés dont §e" + tntPositions.size() + " TNT§a.");
        player.sendMessage("§7Taille de la map: §e" + (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1));
        player.sendMessage("§7N'oubliez pas: §e/tntrun setspawn " + mapName + " §7et §e/tntrun hub setposition " + mapName);
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
        if (args.length < 2) {
            // Join any available game
            if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
                player.sendMessage("§cVous êtes déjà dans une partie !");
                return;
            }
            if (!plugin.getGameManager().joinAnyGame(player)) {
                player.sendMessage("§cAucune partie disponible.");
            }
            return;
        }

        if (plugin.getGameManager().getPlayerGame(player.getUniqueId()) != null) {
            player.sendMessage("§cVous êtes déjà dans une partie ! Faites /tntrun leave d'abord.");
            return;
        }
        if (!plugin.getGameManager().joinGame(player, args[1])) {
            player.sendMessage("§cImpossible de rejoindre. (Map inexistante, pas encore scannée, pleine ou en cours)");
        }
    }

    private void handleLeave(Player player) {
        if (!plugin.getGameManager().leaveGame(player)) {
            player.sendMessage("§cVous n'êtes dans aucune partie.");
        }
    }

    private void handleStart(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 2) { player.sendMessage("§cUsage: /tntrun start <instanceId>"); return; }
        Game game = plugin.getGameManager().getGame(args[1]);
        if (game == null) { player.sendMessage("§cAucune instance trouvée avec cet ID."); return; }
        if (game.isRunning()) { player.sendMessage("§cCette partie est déjà en cours."); return; }
        player.sendMessage("§aForce start !");
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

    private void handleHub(Player player, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("setposition")) {
            if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
            MapData map = plugin.getConfigManager().getOrCreateMap(args[2]);
            map.setHubSpawn(player.getLocation());
            plugin.getConfigManager().saveMap(map);
            player.sendMessage("§aHub défini pour la map §e" + args[2] + " §aà votre position !");
            return;
        }

        // Try to leave current game and go to hub
        String currentGame = plugin.getGameManager().getPlayerGame(player.getUniqueId());
        if (currentGame != null) {
            var instance = plugin.getGameManager().getInstance(currentGame);
            if (instance != null && instance.getTemplate().getHubSpawn() != null) {
                plugin.getGameManager().leaveGame(player);
                player.teleport(instance.getTemplate().getHubSpawn());
                player.sendMessage("§aTéléporté au hub !");
                return;
            }
        }

        // Find any hub
        for (MapData map : plugin.getConfigManager().getMaps().values()) {
            if (map.getHubSpawn() != null) {
                player.teleport(map.getHubSpawn());
                player.sendMessage("§aTéléporté au hub !");
                return;
            }
        }
        player.sendMessage("§cAucun hub n'est configuré.");
    }

    private void handleNCPSpawn(Player player, String[] args) {
        if (!player.hasPermission("bettertntrun.admin")) { player.sendMessage("§cPermission refusée."); return; }
        if (args.length < 3) { player.sendMessage("§cUsage: /tntrun NCPspawn <direction> <skin>"); return; }

        float direction;
        try {
            direction = switch (args[1].toLowerCase()) {
                case "north", "n" -> 180f;
                case "south", "s" -> 0f;
                case "east", "e" -> -90f;
                case "west", "w" -> 90f;
                case "northeast", "ne" -> -135f;
                case "northwest", "nw" -> 135f;
                case "southeast", "se" -> -45f;
                case "southwest", "sw" -> 45f;
                default -> Float.parseFloat(args[1]);
            };
        } catch (NumberFormatException e) {
            player.sendMessage("§cDirection invalide !");
            return;
        }

        String skinName = args[2];
        plugin.getNpcManager().spawnNPC(player.getLocation(), direction, skinName);
        player.sendMessage("§aNPC spawné ! (direction: §e" + args[1] + "§a, skin: §e" + skinName + "§a)");
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l═══ BetterTntRun ═══");
        player.sendMessage("§e/tntrun join [map] §7- Rejoindre une partie");
        player.sendMessage("§e/tntrun leave §7- Quitter la partie");
        player.sendMessage("§e/tntrun hub §7- Retourner au hub");
        player.sendMessage("§e/tntrun top §7- Classement");
        if (player.hasPermission("bettertntrun.admin")) {
            player.sendMessage("§c§lAdmin:");
            player.sendMessage("§e/tntrun config <map> §7- Créer une map");
            player.sendMessage("§e/tntrun setpos1/setpos2 <map> §7- Définir la zone");
            player.sendMessage("§e/tntrun setspawn <map> §7- Spawn (après pos1/pos2)");
            player.sendMessage("§e/tntrun scan <map> §7- Scanner et sauvegarder les blocs");
            player.sendMessage("§e/tntrun setplayer <min> <max> <map> §7- Joueurs");
            player.sendMessage("§e/tntrun deftime <map> <sec> §7- Temps d'attente");
            player.sendMessage("§e/tntrun hub setposition <map> §7- Définir le hub");
            player.sendMessage("§e/tntrun start <instanceId> §7- Forcer le démarrage");
            player.sendMessage("§e/tntrun NCPspawn <dir> <skin> §7- Spawner un NPC");
        }
    }
}
