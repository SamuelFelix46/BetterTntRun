package com.bettertntrun.game;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.models.MapData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Game {

    public enum GameState { WAITING, STARTING, RUNNING, ENDING }

    private final BetterTntRun plugin;
    private final MapData mapData;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private GameState state = GameState.WAITING;
    private BossBar bossBar;
    private BukkitRunnable countdownTask;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public Game(BetterTntRun plugin, MapData mapData) {
        this.plugin = plugin;
        this.mapData = mapData;
        this.bossBar = Bukkit.createBossBar("§eTNTRun - " + mapData.getName(), BarColor.YELLOW, BarStyle.SOLID);
    }

    public void addPlayer(Player player) {
        if (state != GameState.WAITING && state != GameState.STARTING) return;

        players.add(player.getUniqueId());
        player.teleport(mapData.getSpawn());
        player.setGameMode(GameMode.ADVENTURE);
        bossBar.addPlayer(player);

        broadcast("§a" + player.getName() + " §ea rejoint la partie §7(" + players.size() + "/" + mapData.getMaxPlayers() + ")");

        if (players.size() >= mapData.getMaxPlayers()) {
            startGame();
        } else if (players.size() >= mapData.getMinPlayers() && state == GameState.WAITING) {
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
        player.setGameMode(GameMode.SURVIVAL);

        broadcast("§c" + player.getName() + " §ea quitté la partie §7(" + players.size() + "/" + mapData.getMaxPlayers() + ")");

        if (state == GameState.STARTING && players.size() < mapData.getMinPlayers()) {
            cancelCountdown();
        }

        if (state == GameState.RUNNING) checkWin();
        updateScoreboards();
    }

    public void eliminatePlayer(Player player) {
        alivePlayers.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        player.teleport(mapData.getSpawn());
        player.setGameMode(GameMode.SPECTATOR);

        broadcast("§c" + player.getName() + " §ea été éliminé ! §7(" + alivePlayers.size() + " restants)");
        updateScoreboards();
        checkWin();
    }

    private void startCountdown() {
        state = GameState.STARTING;
        final int waitTime = mapData.getWaitTime();

        countdownTask = new BukkitRunnable() {
            int timer = waitTime;
            @Override
            public void run() {
                if (timer <= 0) {
                    if (players.size() >= mapData.getMinPlayers()) {
                        startGame();
                    } else {
                        broadcast("§cPas assez de joueurs ! Partie annulée.");
                        cancelGame();
                    }
                    cancel();
                    return;
                }

                bossBar.setProgress((double) timer / waitTime);
                bossBar.setTitle("§eDémarrage dans §c" + timer + "s");

                if (timer <= 5 || timer == 10 || timer == 15 || timer == 30) {
                    broadcast("§eLa partie commence dans §c" + timer + " §esecondes !");
                }
                timer--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        state = GameState.WAITING;
        bossBar.setTitle("§eTNTRun - " + mapData.getName());
        bossBar.setProgress(1.0);
    }

    private void startGame() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        state = GameState.RUNNING;

        alivePlayers.addAll(players);
        bossBar.setTitle("§c§lTNTRun - EN JEU");
        bossBar.setColor(BarColor.RED);
        bossBar.setProgress(1.0);

        // TP all players to random positions in zone
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(getRandomLocation());
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        // 3 second countdown before TNT activation
        broadcast("§e§lPréparez-vous !");
        new BukkitRunnable() {
            int countdown = 3;
            @Override
            public void run() {
                if (countdown <= 0) {
                    broadcast("§c§lC'EST PARTI ! Les blocs vont disparaître !");
                    cancel();
                    return;
                }
                broadcast("§6" + countdown + "...");
                countdown--;
            }
        }.runTaskTimer(plugin, 20L, 20L);

        updateScoreboards();
    }

    private void checkWin() {
        if (state != GameState.RUNNING) return;
        if (alivePlayers.size() <= 1) {
            state = GameState.ENDING;
            if (alivePlayers.size() == 1) {
                UUID winnerUUID = alivePlayers.iterator().next();
                Player winner = Bukkit.getPlayer(winnerUUID);
                if (winner != null) {
                    broadcast("§6§l★ §eGG §b" + winner.getName() + " §ea gagné la partie ! §6§l★");
                    plugin.getConfigManager().addWin(winnerUUID, winner.getName());
                }
            }

            new BukkitRunnable() {
                @Override
                public void run() { endGame(); }
            }.runTaskLater(plugin, 60L);
        }
    }

    private void cancelGame() {
        state = GameState.ENDING;
        endGame();
    }

    private void endGame() {
        for (UUID uuid : new HashSet<>(players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                bossBar.removePlayer(p);
                removeScoreboard(p);
                p.setGameMode(GameMode.SURVIVAL);
                if (mapData.getHubSpawn() != null) {
                    p.teleport(mapData.getHubSpawn());
                } else if (mapData.getSpawn() != null) {
                    p.teleport(mapData.getSpawn());
                }
            }
            plugin.getGameManager().removePlayerMapping(uuid);
        }
        players.clear();
        alivePlayers.clear();
        spectators.clear();
        state = GameState.WAITING;
        bossBar.removeAll();
        bossBar = Bukkit.createBossBar("§eTNTRun - " + mapData.getName(), BarColor.YELLOW, BarStyle.SOLID);
    }

    public void forceStop() { endGame(); }

    private Location getRandomLocation() {
        Location p1 = mapData.getPos1();
        Location p2 = mapData.getPos2();

        double x = ThreadLocalRandom.current().nextDouble(Math.min(p1.getX(), p2.getX()), Math.max(p1.getX(), p2.getX()));
        double z = ThreadLocalRandom.current().nextDouble(Math.min(p1.getZ(), p2.getZ()), Math.max(p1.getZ(), p2.getZ()));
        double y = Math.max(p1.getY(), p2.getY());

        return new Location(p1.getWorld(), x, y, z);
    }

    private void broadcast(String message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    // ===== Scoreboard =====

    private void updateScoreboards() {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) setScoreboard(p);
        }
    }

    private void setScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("tntrun", Criteria.DUMMY, "§6§lTNT Run");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.getScore("§eMap: §f" + mapData.getName()).setScore(5);
        obj.getScore("§eJoueurs: §f" + alivePlayers.size() + "/" + players.size()).setScore(4);
        obj.getScore("§eStatut: §f" + getStateText()).setScore(3);
        obj.getScore("§7------------------").setScore(2);
        obj.getScore("§ebettertntrun.com").setScore(1);

        player.setScoreboard(board);
        playerScoreboards.put(player.getUniqueId(), board);
    }

    private void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playerScoreboards.remove(player.getUniqueId());
    }

    private String getStateText() {
        return switch (state) {
            case WAITING -> "§aEn attente";
            case STARTING -> "§eDémarrage...";
            case RUNNING -> "§cEn jeu";
            case ENDING -> "§7Terminé";
        };
    }

    // ===== Getters =====
    public boolean isRunning() { return state == GameState.RUNNING; }
    public boolean isFull() { return players.size() >= mapData.getMaxPlayers(); }
    public boolean isEmpty() { return players.isEmpty(); }
    public boolean isAlive(UUID uuid) { return alivePlayers.contains(uuid); }
    public boolean isInGame(UUID uuid) { return players.contains(uuid); }
    public GameState getState() { return state; }
    public MapData getMapData() { return mapData; }
}
