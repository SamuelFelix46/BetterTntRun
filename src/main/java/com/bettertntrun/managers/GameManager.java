package com.bettertntrun.managers;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.game.Game;
import com.bettertntrun.models.MapData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final BetterTntRun plugin;
    private final Map<String, Game> activeGames = new HashMap<>();
    private final Map<UUID, String> playerGameMap = new HashMap<>();

    public GameManager(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    public boolean joinGame(Player player, String mapName) {
        if (playerGameMap.containsKey(player.getUniqueId())) return false;

        MapData mapData = plugin.getConfigManager().getMap(mapName);
        if (mapData == null || mapData.getSpawn() == null || mapData.getPos1() == null || mapData.getPos2() == null) return false;

        Game game = activeGames.computeIfAbsent(mapName.toLowerCase(), k -> new Game(plugin, mapData));
        if (game.isFull() || game.isRunning()) return false;

        game.addPlayer(player);
        playerGameMap.put(player.getUniqueId(), mapName.toLowerCase());
        return true;
    }

    public boolean leaveGame(Player player) {
        String mapName = playerGameMap.remove(player.getUniqueId());
        if (mapName == null) return false;

        Game game = activeGames.get(mapName);
        if (game != null) {
            game.removePlayer(player);
            if (game.isEmpty()) activeGames.remove(mapName);
        }
        return true;
    }

    public Game getGame(String mapName) { return activeGames.get(mapName.toLowerCase()); }
    public String getPlayerGame(UUID uuid) { return playerGameMap.get(uuid); }
    public void removePlayerMapping(UUID uuid) { playerGameMap.remove(uuid); }

    public void stopAllGames() {
        for (Game game : activeGames.values()) game.forceStop();
        activeGames.clear();
        playerGameMap.clear();
    }
}
