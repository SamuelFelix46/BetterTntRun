package com.bettertntrun.managers;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.models.MapData;
import com.bettertntrun.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigManager {

    private final BetterTntRun plugin;
    private File mapsFile;
    private FileConfiguration mapsConfig;
    private File statsFile;
    private FileConfiguration statsConfig;
    private File npcsFile;
    private FileConfiguration npcsConfig;
    private final Map<String, MapData> maps = new HashMap<>();

    public ConfigManager(BetterTntRun plugin) {
        this.plugin = plugin;
        setupFiles();
        loadMaps();
    }

    private void setupFiles() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        mapsFile = new File(plugin.getDataFolder(), "maps.yml");
        if (!mapsFile.exists()) { try { mapsFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }
        mapsConfig = YamlConfiguration.loadConfiguration(mapsFile);

        statsFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!statsFile.exists()) { try { statsFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);

        npcsFile = new File(plugin.getDataFolder(), "npcs.yml");
        if (!npcsFile.exists()) { try { npcsFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
    }

    // ===== MAPS =====

    private void loadMaps() {
        if (mapsConfig.getConfigurationSection("maps") == null) return;
        for (String key : mapsConfig.getConfigurationSection("maps").getKeys(false)) {
            String path = "maps." + key;
            MapData map = new MapData(key);

            if (mapsConfig.contains(path + ".spawn")) map.setSpawn(deserializeLocation(path + ".spawn"));
            if (mapsConfig.contains(path + ".pos1")) map.setPos1(deserializeLocation(path + ".pos1"));
            if (mapsConfig.contains(path + ".pos2")) map.setPos2(deserializeLocation(path + ".pos2"));
            if (mapsConfig.contains(path + ".hubSpawn")) map.setHubSpawn(deserializeLocation(path + ".hubSpawn"));
            map.setMinPlayers(mapsConfig.getInt(path + ".minPlayers", 2));
            map.setMaxPlayers(mapsConfig.getInt(path + ".maxPlayers", 8));
            map.setWaitTime(mapsConfig.getInt(path + ".waitTime", 30));

            maps.put(key.toLowerCase(), map);
        }
    }

    public void saveMap(MapData map) {
        String path = "maps." + map.getName();
        if (map.getSpawn() != null) serializeLocation(path + ".spawn", map.getSpawn());
        if (map.getPos1() != null) serializeLocation(path + ".pos1", map.getPos1());
        if (map.getPos2() != null) serializeLocation(path + ".pos2", map.getPos2());
        if (map.getHubSpawn() != null) serializeLocation(path + ".hubSpawn", map.getHubSpawn());
        mapsConfig.set(path + ".minPlayers", map.getMinPlayers());
        mapsConfig.set(path + ".maxPlayers", map.getMaxPlayers());
        mapsConfig.set(path + ".waitTime", map.getWaitTime());
        maps.put(map.getName().toLowerCase(), map);
        saveMapsConfig();
    }

    public MapData getMap(String name) { return maps.get(name.toLowerCase()); }
    public Map<String, MapData> getMaps() { return maps; }

    public MapData getOrCreateMap(String name) {
        MapData map = maps.get(name.toLowerCase());
        if (map == null) {
            map = new MapData(name);
            maps.put(name.toLowerCase(), map);
        }
        return map;
    }

    // ===== STATS =====

    public void addWin(UUID uuid, String playerName) {
        String path = "players." + uuid.toString();
        int wins = statsConfig.getInt(path + ".wins", 0) + 1;
        statsConfig.set(path + ".name", playerName);
        statsConfig.set(path + ".wins", wins);
        saveStatsConfig();
    }

    public List<PlayerData> getTopPlayers(int limit) {
        List<PlayerData> list = new ArrayList<>();
        if (statsConfig.getConfigurationSection("players") == null) return list;
        for (String key : statsConfig.getConfigurationSection("players").getKeys(false)) {
            String path = "players." + key;
            list.add(new PlayerData(UUID.fromString(key),
                    statsConfig.getString(path + ".name", "Unknown"),
                    statsConfig.getInt(path + ".wins", 0)));
        }
        list.sort((a, b) -> Integer.compare(b.getWins(), a.getWins()));
        return list.subList(0, Math.min(limit, list.size()));
    }

    // ===== NPCs =====

    public void saveNPC(String id, String mapName, Location location, float direction) {
        String path = "npcs." + id;
        serializeLocation(path + ".location", location);
        npcsConfig.set(path + ".mapName", mapName);
        npcsConfig.set(path + ".direction", direction);
        saveNpcsConfig();
    }

    public void removeNPCConfig(String id) {
        npcsConfig.set("npcs." + id, null);
        saveNpcsConfig();
    }

    public Map<String, Object[]> loadNPCs() {
        Map<String, Object[]> result = new HashMap<>();
        if (npcsConfig.getConfigurationSection("npcs") == null) return result;
        for (String id : npcsConfig.getConfigurationSection("npcs").getKeys(false)) {
            String path = "npcs." + id;
            Location loc = deserializeLocation(path + ".location");
            String mapName = npcsConfig.getString(path + ".mapName");
            float direction = (float) npcsConfig.getDouble(path + ".direction", 0);
            if (loc != null && mapName != null) {
                result.put(id, new Object[]{loc, mapName, direction});
            }
        }
        return result;
    }

    // ===== Serialization =====

    private void serializeLocation(String path, Location loc) {
        mapsConfig.set(path + ".world", loc.getWorld().getName());
        mapsConfig.set(path + ".x", loc.getX());
        mapsConfig.set(path + ".y", loc.getY());
        mapsConfig.set(path + ".z", loc.getZ());
        mapsConfig.set(path + ".yaw", loc.getYaw());
        mapsConfig.set(path + ".pitch", loc.getPitch());

        // Also save to npcsConfig if path starts with npcs
        if (path.startsWith("npcs.")) {
            npcsConfig.set(path + ".world", loc.getWorld().getName());
            npcsConfig.set(path + ".x", loc.getX());
            npcsConfig.set(path + ".y", loc.getY());
            npcsConfig.set(path + ".z", loc.getZ());
            npcsConfig.set(path + ".yaw", loc.getYaw());
            npcsConfig.set(path + ".pitch", loc.getPitch());
        }
    }

    private Location deserializeLocation(String path) {
        FileConfiguration cfg = path.startsWith("npcs.") ? npcsConfig : mapsConfig;
        if (!cfg.contains(path + ".world")) return null;
        World world = Bukkit.getWorld(cfg.getString(path + ".world"));
        if (world == null) return null;
        return new Location(world,
                cfg.getDouble(path + ".x"),
                cfg.getDouble(path + ".y"),
                cfg.getDouble(path + ".z"),
                (float) cfg.getDouble(path + ".yaw", 0),
                (float) cfg.getDouble(path + ".pitch", 0));
    }

    private void saveMapsConfig() {
        try { mapsConfig.save(mapsFile); } catch (IOException e) { e.printStackTrace(); }
    }
    private void saveStatsConfig() {
        try { statsConfig.save(statsFile); } catch (IOException e) { e.printStackTrace(); }
    }
    private void saveNpcsConfig() {
        try { npcsConfig.save(npcsFile); } catch (IOException e) { e.printStackTrace(); }
    }
}
