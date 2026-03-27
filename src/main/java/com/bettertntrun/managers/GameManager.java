package com.bettertntrun.managers;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.game.Game;
import com.bettertntrun.models.MapData;
import com.bettertntrun.models.MapInstance;
import com.bettertntrun.models.PlayerInventorySnapshot;
import com.bettertntrun.models.TemplateBlockData;
import com.bettertntrun.world.VoidChunkGenerator;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private final BetterTntRun plugin;
    private static final BlockData FULL_LIGHT_BLOCK = Bukkit.createBlockData("minecraft:light[level=15]");
    private final Map<String, Game> activeGames = new HashMap<>();
    private final Map<UUID, String> playerGameMap = new HashMap<>();
    private final Map<String, MapInstance> activeInstances = new HashMap<>();
    private final Set<Integer> usedSlots = new HashSet<>();
    private final Map<UUID, PlayerInventorySnapshot> playerSnapshots = new HashMap<>();
    
    // Pool of free (idle) instances ready for reuse
    private final Map<String, MapInstance> freeInstances = new HashMap<>();
    
    private World gameWorld;

    private static final String GAME_WORLD_NAME = "tntrun_games";
    private static final int SLOT_SPACING = 500;
    private static final int BASE_Y = 100;

    public GameManager(BetterTntRun plugin) {
        this.plugin = plugin;
        createOrLoadGameWorld();
    }

    private void createOrLoadGameWorld() {
        gameWorld = Bukkit.getWorld(GAME_WORLD_NAME);
        if (gameWorld == null) {
            WorldCreator creator = new WorldCreator(GAME_WORLD_NAME);
            creator.generator(new VoidChunkGenerator());
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            gameWorld = creator.createWorld();
        }
        if (gameWorld != null) {
            gameWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            gameWorld.setTime(6000L);
            gameWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            gameWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            gameWorld.setDifficulty(Difficulty.PEACEFUL);
            gameWorld.setSpawnFlags(false, false);
        }
    }

    public World getGameWorld() { return gameWorld; }

    // ============================
    // SMART MATCHMAKING ALGORITHM
    // ============================

    /**
     * Smart join: NPC uses a scoring algorithm to pick the best game for the player.
     * Criteria: player count (more players = better), wait time, map variety.
     */
    public boolean joinBestGame(Player player) {
        if (playerGameMap.containsKey(player.getUniqueId())) return false;

        // Score all available games and pick the best one
        Game bestGame = null;
        String bestInstanceId = null;
        double bestScore = -1;

        for (Map.Entry<String, Game> entry : activeGames.entrySet()) {
            Game game = entry.getValue();
            if (game.isFull() || game.isRunning() || game.isEnding()) continue;

            double score = calculateGameScore(game, entry.getKey());
            if (score > bestScore) {
                bestScore = score;
                bestGame = game;
                bestInstanceId = entry.getKey();
            }
        }

        if (bestGame != null) {
            bestGame.addPlayer(player);
            playerGameMap.put(player.getUniqueId(), bestInstanceId);
            return true;
        }

        // No available game with space — try reusing a free instance
        for (Map.Entry<String, MapData> mapEntry : plugin.getConfigManager().getMaps().entrySet()) {
            MapData template = mapEntry.getValue();
            if (template.getTemplateBlocks().isEmpty()) continue;

            String instanceId = reuseOrCreateInstance(template);
            if (instanceId != null) {
                Game game = activeGames.get(instanceId);
                game.addPlayer(player);
                playerGameMap.put(player.getUniqueId(), instanceId);
                return true;
            }
        }

        return false;
    }

    /**
     * Calculate a score for a game instance for matchmaking.
     * Higher score = better choice for the player.
     */
    private double calculateGameScore(Game game, String instanceId) {
        double score = 0;
        MapInstance inst = activeInstances.get(instanceId);
        if (inst == null) return 0;

        int playerCount = game.getPlayerCount();
        int maxPlayers = inst.getTemplate().getMaxPlayers();
        int minPlayers = inst.getTemplate().getMinPlayers();

        // More players = more fun (weight: 40)
        score += (double) playerCount / maxPlayers * 40.0;

        // Close to starting = urgent/exciting (weight: 30)
        if (playerCount >= minPlayers) {
            score += 30; // Game is about to start or counting down
        } else {
            // How close to min players (weight: 20)
            score += (double) playerCount / minPlayers * 20.0;
        }

        // Penalize empty games slightly (prefer games with at least 1 player)
        if (playerCount == 0) {
            score -= 5;
        }

        // Time factor: how long has this instance been waiting? (weight: 10)
        long waitingTicks = game.getWaitingTicks();
        // After 60 seconds of waiting, give bonus so players don't wait forever
        if (waitingTicks > 1200) { // 60 seconds
            score += Math.min(10, (waitingTicks - 1200) / 200.0);
        }

        return score;
    }

    /**
     * Join any available game (legacy, used by /tntrun join).
     */
    public boolean joinAnyGame(Player player) {
        return joinBestGame(player);
    }

    /**
     * Join a specific map template.
     */
    public boolean joinGame(Player player, String mapName) {
        if (playerGameMap.containsKey(player.getUniqueId())) return false;

        MapData template = plugin.getConfigManager().getMap(mapName);
        if (template == null || template.getTemplateBlocks().isEmpty()) return false;

        // Check existing instances of this template
        for (Map.Entry<String, Game> entry : activeGames.entrySet()) {
            MapInstance inst = activeInstances.get(entry.getKey());
            if (inst != null && inst.getTemplateName().equalsIgnoreCase(mapName)) {
                Game game = entry.getValue();
                if (!game.isFull() && !game.isRunning() && !game.isEnding()) {
                    game.addPlayer(player);
                    playerGameMap.put(player.getUniqueId(), entry.getKey());
                    return true;
                }
            }
        }

        // Reuse or create
        String instanceId = reuseOrCreateInstance(template);
        if (instanceId != null) {
            Game game = activeGames.get(instanceId);
            game.addPlayer(player);
            playerGameMap.put(player.getUniqueId(), instanceId);
            return true;
        }

        return false;
    }

    // ============================
    // INSTANCE REUSE SYSTEM
    // ============================

    /**
     * Try to reuse a free (idle) instance of the same template, or create a new one.
     */
    private String reuseOrCreateInstance(MapData template) {
        // Look for a free instance of the same template
        for (Iterator<Map.Entry<String, MapInstance>> it = freeInstances.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, MapInstance> entry = it.next();
            MapInstance freeInst = entry.getValue();
            if (freeInst.getTemplateName().equalsIgnoreCase(template.getName())) {
                String instanceId = entry.getKey();
                it.remove();

                // Restore TNT blocks
                restoreInstance(freeInst);

                // Re-create game for this instance
                activeInstances.put(instanceId, freeInst);
                Game game = new Game(plugin, freeInst);
                activeGames.put(instanceId, game);

                plugin.getLogger().info("Reused free instance: " + instanceId);
                return instanceId;
            }
        }

        // No free instance — create new
        return createNewInstance(template);
    }

    /**
     * Create a new map instance in the void world.
     */
    private String createNewInstance(MapData template) {
        if (gameWorld == null) return null;

        int slot = findNextSlot();
        int originX = slot * SLOT_SPACING;
        int originY = BASE_Y;
        int originZ = 0;

        pasteTemplate(template, originX, originY, originZ);

        MapInstance instance = new MapInstance(template.getName(), slot, gameWorld, originX, originY, originZ, template);
        String instanceId = template.getName().toLowerCase() + "_" + slot;

        activeInstances.put(instanceId, instance);
        usedSlots.add(slot);

        Game game = new Game(plugin, instance);
        activeGames.put(instanceId, game);

        plugin.getLogger().info("Created new instance: " + instanceId + " at slot " + slot + " (X=" + originX + ")");
        return instanceId;
    }

    /**
     * Paste template blocks + add light blocks to prevent darkness.
     */
    private void pasteTemplate(MapData template, int originX, int originY, int originZ) {
        for (TemplateBlockData blockData : template.getTemplateBlocks()) {
            try {
                Material material = Bukkit.createBlockData(blockData.blockData()).getMaterial();
                gameWorld.getBlockAt(originX + blockData.relX(), originY + blockData.relY(), originZ + blockData.relZ())
                        .setType(material, false);
            } catch (Exception ignored) {
            }
        }

        for (TemplateBlockData blockData : template.getTemplateBlocks()) {
            try {
                gameWorld.getBlockAt(originX + blockData.relX(), originY + blockData.relY(), originZ + blockData.relZ())
                        .setBlockData(Bukkit.createBlockData(blockData.blockData()), false);
            } catch (Exception ignored) {
            }
        }

        // Auto-place LIGHT blocks for lighting every 4 blocks on top of the map
        int sizeX = template.getSizeX();
        int sizeY = template.getSizeY();
        int sizeZ = template.getSizeZ();

        for (int x = 0; x < sizeX; x += 3) {
            for (int z = 0; z < sizeZ; z += 3) {
                // Place light above the highest block in this column
                int lightY = sizeY + 2; // 2 blocks above the top of the map
                int absX = originX + x;
                int absY = originY + lightY;
                int absZ = originZ + z;
                if (gameWorld.getBlockAt(absX, absY, absZ).getType() == Material.AIR) {
                    gameWorld.getBlockAt(absX, absY, absZ).setBlockData(FULL_LIGHT_BLOCK, false);
                }
                
                // Also place light blocks below the map for under-lighting
                int belowY = originY - 1;
                if (x % 6 == 0 && z % 6 == 0) { // Less frequent below
                    if (gameWorld.getBlockAt(absX, belowY, absZ).getType() == Material.AIR) {
                        gameWorld.getBlockAt(absX, belowY, absZ).setBlockData(FULL_LIGHT_BLOCK, false);
                    }
                }
            }
        }
    }

    /**
     * Clear all blocks of an instance (including light blocks).
     */
    private void clearInstance(MapInstance instance) {
        MapData template = instance.getTemplate();
        int sizeX = template.getSizeX();
        int sizeY = template.getSizeY();
        int sizeZ = template.getSizeZ();
        
        // Clear template blocks
        for (TemplateBlockData blockData : template.getTemplateBlocks()) {
            try {
                gameWorld.getBlockAt(
                                instance.getOriginX() + blockData.relX(),
                                instance.getOriginY() + blockData.relY(),
                                instance.getOriginZ() + blockData.relZ())
                        .setType(Material.AIR);
            } catch (Exception ignored) {}
        }
        
        // Clear light blocks above and below
        for (int x = 0; x < sizeX; x += 3) {
            for (int z = 0; z < sizeZ; z += 3) {
                int absX = instance.getOriginX() + x;
                int absZ = instance.getOriginZ() + z;
                int lightY = instance.getOriginY() + sizeY + 2;
                if (gameWorld.getBlockAt(absX, lightY, absZ).getType() == Material.LIGHT) {
                    gameWorld.getBlockAt(absX, lightY, absZ).setType(Material.AIR);
                }
                if (x % 6 == 0 && z % 6 == 0) {
                    int belowY = instance.getOriginY() - 1;
                    if (gameWorld.getBlockAt(absX, belowY, absZ).getType() == Material.LIGHT) {
                        gameWorld.getBlockAt(absX, belowY, absZ).setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Restore TNT blocks for an instance.
     */
    public void restoreInstance(MapInstance instance) {
        MapData template = instance.getTemplate();
        for (int[] pos : template.getTntRelativePositions()) {
            gameWorld.getBlockAt(
                instance.getOriginX() + pos[0],
                instance.getOriginY() + pos[1],
                instance.getOriginZ() + pos[2]
            ).setType(Material.TNT);
        }
    }

    public boolean leaveGame(Player player) {
        String instanceId = playerGameMap.remove(player.getUniqueId());
        if (instanceId == null) return false;

        Game game = activeGames.get(instanceId);
        if (game != null) {
            game.removePlayer(player);
        }
        return true;
    }

    public void preparePlayerForGame(Player player) {
        playerSnapshots.computeIfAbsent(player.getUniqueId(), ignored -> PlayerInventorySnapshot.capture(player));

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.updateInventory();
    }

    public void restorePlayerAfterGame(Player player) {
        PlayerInventorySnapshot snapshot = playerSnapshots.remove(player.getUniqueId());
        if (snapshot != null) {
            snapshot.restore(player);
        }
    }

    /**
     * Release an instance back to the pool for reuse (instead of destroying it).
     * TNT is restored and the instance becomes available for new games.
     */
    public void freeInstance(String instanceId) {
        Game game = activeGames.remove(instanceId);
        MapInstance instance = activeInstances.remove(instanceId);
        
        if (instance != null) {
            // Restore TNT so it's ready for reuse
            restoreInstance(instance);
            // Move to free pool instead of destroying
            freeInstances.put(instanceId, instance);
            plugin.getLogger().info("Instance " + instanceId + " moved to free pool (reusable)");
        }
    }

    // ============================
    // LONE PLAYER REDISTRIBUTION
    // ============================

    /**
     * Check for players who are alone on a map for too long.
     * Move them to a better game or prioritize their map via NPC algorithm.
     */
    public void checkLonePlayers() {
        for (Map.Entry<String, Game> entry : new HashMap<>(activeGames).entrySet()) {
            Game game = entry.getValue();
            if (!game.isWaiting()) continue;
            if (game.getPlayerCount() != 1) continue;

            // Player is alone and waiting - check how long
            long waitTicks = game.getWaitingTicks();
            if (waitTicks < 600) continue; // Less than 30 seconds, patience

            // Try to find a better game to move this player to
            UUID lonePlayerUUID = game.getFirstPlayerUUID();
            if (lonePlayerUUID == null) continue;
            Player lonePlayer = Bukkit.getPlayer(lonePlayerUUID);
            if (lonePlayer == null) continue;

            // Find another game with more players
            String bestInstanceId = null;
            double bestScore = -1;

            for (Map.Entry<String, Game> other : activeGames.entrySet()) {
                if (other.getKey().equals(entry.getKey())) continue;
                Game otherGame = other.getValue();
                if (otherGame.isFull() || otherGame.isRunning() || otherGame.isEnding()) continue;
                if (otherGame.getPlayerCount() == 0) continue;

                double score = calculateGameScore(otherGame, other.getKey());
                if (score > bestScore) {
                    bestScore = score;
                    bestInstanceId = other.getKey();
                }
            }

            if (bestInstanceId != null && bestScore > 5) {
                // Move player to the better game
                game.removePlayer(lonePlayer);
                playerGameMap.remove(lonePlayerUUID);

                Game betterGame = activeGames.get(bestInstanceId);
                betterGame.addPlayer(lonePlayer);
                playerGameMap.put(lonePlayerUUID, bestInstanceId);
                lonePlayer.sendMessage("§e§lVous avez été déplacé vers une partie avec plus de joueurs !");
            }
        }
    }

    private int findNextSlot() {
        int slot = 0;
        while (usedSlots.contains(slot)) slot++;
        return slot;
    }

    public Game getGame(String instanceId) { return activeGames.get(instanceId.toLowerCase()); }
    public MapInstance getInstance(String instanceId) { return activeInstances.get(instanceId); }
    public String getPlayerGame(UUID uuid) { return playerGameMap.get(uuid); }
    public void removePlayerMapping(UUID uuid) { playerGameMap.remove(uuid); }

    public Map<String, Game> getActiveGames() { return activeGames; }
    public Map<String, MapInstance> getActiveInstances() { return activeInstances; }
    public Map<String, MapInstance> getFreeInstances() { return freeInstances; }

    /**
     * Get total instances (active + free pool).
     */
    public int getTotalInstanceCount() {
        return activeInstances.size() + freeInstances.size();
    }

    public void stopAllGames() {
        for (Map.Entry<String, Game> entry : new HashMap<>(activeGames).entrySet()) {
            entry.getValue().forceStop();
        }
        // Clear everything including free pool
        for (MapInstance inst : freeInstances.values()) {
            clearInstance(inst);
            usedSlots.remove(inst.getSlotIndex());
        }
        for (MapInstance inst : activeInstances.values()) {
            clearInstance(inst);
            usedSlots.remove(inst.getSlotIndex());
        }
        activeGames.clear();
        activeInstances.clear();
        playerGameMap.clear();
        freeInstances.clear();
        usedSlots.clear();
        playerSnapshots.clear();
    }
}
