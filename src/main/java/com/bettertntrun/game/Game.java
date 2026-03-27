package com.bettertntrun.game;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.models.MapData;
import com.bettertntrun.models.MapInstance;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Game {

    public enum GameState { WAITING, STARTING, RUNNING, ENDING }

    private final BetterTntRun plugin;
    private final MapInstance mapInstance;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final java.util.Map<UUID, Scoreboard> playerScoreboards = new java.util.HashMap<>();
    private final Set<Location> scheduledBlocks = new HashSet<>();
    private GameState state = GameState.WAITING;
    private BossBar bossBar;
    private BukkitRunnable countdownTask;
    private BukkitRunnable tntMonitorTask;
    private boolean gracePeriod = false;
    private long waitingStartTimeMillis;

    public Game(BetterTntRun plugin, MapInstance mapInstance) {
        this.plugin = plugin;
        this.mapInstance = mapInstance;
        this.bossBar = Bukkit.createBossBar("\u00a7eTNTRun - " + mapInstance.getTemplateName(), BarColor.YELLOW, BarStyle.SOLID);
        this.waitingStartTimeMillis = System.currentTimeMillis();
    }

    public void addPlayer(Player player) {
        if (state != GameState.WAITING && state != GameState.STARTING) return;

        plugin.getGameManager().preparePlayerForGame(player);
        players.add(player.getUniqueId());
        player.teleport(mapInstance.getSpawnLocation());
        player.setGameMode(GameMode.ADVENTURE);
        bossBar.addPlayer(player);

        MapData template = mapInstance.getTemplate();
        broadcast("\u00a7a" + player.getName() + " \u00a7ea rejoint la partie \u00a77(" + players.size() + "/" + template.getMaxPlayers() + ")");

        if (players.size() >= template.getMaxPlayers()) {
            startGame();
        } else if (players.size() >= template.getMinPlayers() && state == GameState.WAITING) {
            startCountdown();
        }

        updateScoreboards();
    }

    public void removePlayer(Player player) {
        players.remove(player.getUniqueId());
        alivePlayers.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        bossBar.removePlayer(player);
        removeScoreboard(player);
        plugin.getGameManager().restorePlayerAfterGame(player);
        player.setGameMode(GameMode.SURVIVAL);

        MapData template = mapInstance.getTemplate();
        broadcast("\u00a7c" + player.getName() + " \u00a7ea quitte la partie \u00a77(" + players.size() + "/" + template.getMaxPlayers() + ")");

        if (state == GameState.STARTING && players.size() < template.getMinPlayers()) {
            cancelCountdown();
        }

        if (state == GameState.RUNNING) {
            checkWin();
        }
        updateScoreboards();

        if (isEmpty() && (state == GameState.WAITING || state == GameState.STARTING)) {
            String instanceId = mapInstance.getTemplateName().toLowerCase() + "_" + mapInstance.getSlotIndex();
            plugin.getGameManager().freeInstance(instanceId);
        }
    }

    public void eliminatePlayer(Player player) {
        alivePlayers.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        player.teleport(mapInstance.getSpawnLocation());
        player.setGameMode(GameMode.SPECTATOR);

        broadcast("\u00a7c" + player.getName() + " \u00a7ea ete elimine ! \u00a77(" + alivePlayers.size() + " restants)");
        updateScoreboards();
        checkWin();
    }

    private void startCountdown() {
        state = GameState.STARTING;
        final int waitTime = mapInstance.getTemplate().getWaitTime();

        countdownTask = new BukkitRunnable() {
            int timer = waitTime;

            @Override
            public void run() {
                if (timer <= 0) {
                    if (players.size() >= mapInstance.getTemplate().getMinPlayers()) {
                        startGame();
                    } else {
                        broadcast("\u00a7cPas assez de joueurs ! Partie annulee.");
                        cancelGame();
                    }
                    cancel();
                    return;
                }

                bossBar.setProgress((double) timer / waitTime);
                bossBar.setTitle("\u00a7eDemarrage dans \u00a7c" + timer + "s");

                if (timer <= 5 || timer == 10 || timer == 15 || timer == 30) {
                    broadcast("\u00a7eLa partie commence dans \u00a7c" + timer + " \u00a7esecondes !");
                }
                timer--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        state = GameState.WAITING;
        waitingStartTimeMillis = System.currentTimeMillis();
        bossBar.setTitle("\u00a7eTNTRun - " + mapInstance.getTemplateName());
        bossBar.setProgress(1.0);
    }

    private void startGame() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        state = GameState.RUNNING;
        gracePeriod = true;
        scheduledBlocks.clear();
        startTntMonitor();

        alivePlayers.addAll(players);
        bossBar.setTitle("\u00a7c\u00a7lTNTRun - EN JEU");
        bossBar.setColor(BarColor.RED);
        bossBar.setProgress(1.0);

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }

            Location tntLoc = getRandomTNTLocation();
            if (tntLoc != null) {
                player.teleport(tntLoc);
            } else {
                player.teleport(mapInstance.getSpawnLocation());
            }
            player.setGameMode(GameMode.ADVENTURE);
        }

        broadcast("\u00a7e\u00a7lTeleportation ! Preparez-vous...");

        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                if (countdown <= 0) {
                    gracePeriod = false;
                    broadcast("\u00a7c\u00a7lC'EST PARTI ! Les blocs vont disparaitre !");
                    for (UUID uuid : alivePlayers) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                    }
                    cancel();
                    return;
                }

                for (UUID uuid : players) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.sendTitle("\u00a76\u00a7l" + countdown, "\u00a7eLes blocs vont bientot disparaitre !", 0, 25, 5);
                    }
                }
                countdown--;
            }
        }.runTaskTimer(plugin, 20L, 20L);

        updateScoreboards();
    }

    public boolean isGracePeriod() {
        return gracePeriod;
    }

    private void checkWin() {
        if (state != GameState.RUNNING) return;

        if (alivePlayers.size() <= 1) {
            state = GameState.ENDING;
            if (alivePlayers.size() == 1) {
                UUID winnerUUID = alivePlayers.iterator().next();
                Player winner = Bukkit.getPlayer(winnerUUID);
                if (winner != null) {
                    broadcast("\u00a76\u00a7l* \u00a7eGG \u00a7b" + winner.getName() + " \u00a7ea gagne la partie ! \u00a76\u00a7l*");
                    plugin.getConfigManager().addWin(winnerUUID, winner.getName());
                }
            } else {
                broadcast("\u00a77Aucun gagnant cette fois !");
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    endGame();
                }
            }.runTaskLater(plugin, 60L);
        }
    }

    private void cancelGame() {
        state = GameState.ENDING;
        endGame();
    }

    private void endGame() {
        MapData template = mapInstance.getTemplate();
        stopTntMonitor();
        scheduledBlocks.clear();

        for (UUID uuid : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                bossBar.removePlayer(player);
                removeScoreboard(player);
                plugin.getGameManager().restorePlayerAfterGame(player);
                player.setGameMode(GameMode.SURVIVAL);
                if (template.getHubSpawn() != null) {
                    player.teleport(template.getHubSpawn());
                }
            }
            plugin.getGameManager().removePlayerMapping(uuid);
        }

        plugin.getGameManager().restoreInstance(mapInstance);

        players.clear();
        alivePlayers.clear();
        spectators.clear();
        gracePeriod = false;
        state = GameState.WAITING;
        waitingStartTimeMillis = System.currentTimeMillis();
        bossBar.removeAll();
        bossBar = Bukkit.createBossBar("\u00a7eTNTRun - " + mapInstance.getTemplateName(), BarColor.YELLOW, BarStyle.SOLID);

        String instanceId = mapInstance.getTemplateName().toLowerCase() + "_" + mapInstance.getSlotIndex();
        plugin.getGameManager().freeInstance(instanceId);
    }

    private Location getRandomTNTLocation() {
        List<int[]> tntPositions = mapInstance.getTemplate().getTntRelativePositions();
        if (tntPositions == null || tntPositions.isEmpty()) return null;

        List<int[]> shuffled = new ArrayList<>(tntPositions);
        Collections.shuffle(shuffled);

        for (int[] pos : shuffled) {
            int absX = mapInstance.getOriginX() + pos[0];
            int absY = mapInstance.getOriginY() + pos[1];
            int absZ = mapInstance.getOriginZ() + pos[2];

            Block block = mapInstance.getWorld().getBlockAt(absX, absY, absZ);
            if (block.getType() != Material.TNT) {
                continue;
            }

            Block above = mapInstance.getWorld().getBlockAt(absX, absY + 1, absZ);
            Block above2 = mapInstance.getWorld().getBlockAt(absX, absY + 2, absZ);
            if (above.getType() == Material.AIR && above2.getType() == Material.AIR) {
                return new Location(mapInstance.getWorld(), absX + 0.5, absY + 1, absZ + 0.5);
            }
        }
        return null;
    }

    public void forceStop() {
        endGame();
    }

    private void startTntMonitor() {
        stopTntMonitor();
        tntMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.RUNNING) {
                    cancel();
                    return;
                }

                if (gracePeriod) {
                    return;
                }

                for (UUID uuid : new HashSet<>(alivePlayers)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        continue;
                    }

                    for (Block block : getTouchedTntBlocks(player)) {
                        scheduleBlockBreak(block);
                    }
                }
            }
        };
        tntMonitorTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void stopTntMonitor() {
        if (tntMonitorTask != null) {
            tntMonitorTask.cancel();
            tntMonitorTask = null;
        }
    }

    private Set<Block> getTouchedTntBlocks(Player player) {
        Set<Block> touchedBlocks = new HashSet<>();
        Location feet = player.getLocation().clone().subtract(0, 0.15, 0);
        double[][] offsets = {
                {0.0, 0.0},
                {0.29, 0.29},
                {0.29, -0.29},
                {-0.29, 0.29},
                {-0.29, -0.29}
        };

        for (double[] offset : offsets) {
            Block block = feet.clone().add(offset[0], 0, offset[1]).getBlock();
            if (block.getType() == Material.TNT) {
                touchedBlocks.add(block);
            }
        }

        return touchedBlocks;
    }

    private void scheduleBlockBreak(Block block) {
        Location blockLocation = block.getLocation();
        if (!scheduledBlocks.add(blockLocation)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (state == GameState.RUNNING && !gracePeriod && block.getType() == Material.TNT) {
                        block.setType(Material.AIR);
                    }
                } finally {
                    scheduledBlocks.remove(blockLocation);
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    private void broadcast(String message) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void updateScoreboards() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                setScoreboard(player);
            }
        }
    }

    private void setScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("tntrun", Criteria.DUMMY, "\u00a76\u00a7lTNT Run");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        objective.getScore("\u00a7eMap: \u00a7f" + mapInstance.getTemplateName()).setScore(5);
        objective.getScore("\u00a7eJoueurs: \u00a7f" + alivePlayers.size() + "/" + players.size()).setScore(4);
        objective.getScore("\u00a7eStatut: \u00a7f" + getStateText()).setScore(3);
        objective.getScore("\u00a77------------------").setScore(2);
        objective.getScore("\u00a7ebettertntrun.com").setScore(1);

        player.setScoreboard(board);
        playerScoreboards.put(player.getUniqueId(), board);
    }

    private void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playerScoreboards.remove(player.getUniqueId());
    }

    private String getStateText() {
        return switch (state) {
            case WAITING -> "\u00a7aEn attente";
            case STARTING -> "\u00a7eDemarrage...";
            case RUNNING -> gracePeriod ? "\u00a76Preparation..." : "\u00a7cEn jeu";
            case ENDING -> "\u00a77Termine";
        };
    }

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    public boolean isWaiting() {
        return state == GameState.WAITING;
    }

    public boolean isEnding() {
        return state == GameState.ENDING;
    }

    public boolean isFull() {
        return players.size() >= mapInstance.getTemplate().getMaxPlayers();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public boolean isAlive(UUID uuid) {
        return alivePlayers.contains(uuid);
    }

    public boolean isInGame(UUID uuid) {
        return players.contains(uuid);
    }

    public GameState getState() {
        return state;
    }

    public MapInstance getMapInstance() {
        return mapInstance;
    }

    public int getEliminationY() {
        return mapInstance.getEliminationY();
    }

    public int getPlayerCount() {
        return players.size();
    }

    public long getWaitingTicks() {
        if (state != GameState.WAITING && state != GameState.STARTING) return 0;
        return Math.max(0L, (System.currentTimeMillis() - waitingStartTimeMillis) / 50L);
    }

    public UUID getFirstPlayerUUID() {
        return players.isEmpty() ? null : players.iterator().next();
    }
}
