package com.bettertntrun.managers;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.models.MapData;
import com.bettertntrun.models.PlayerData;
import com.bettertntrun.models.TemplateBlockData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ConfigManager {

    private static final String BLOCK_ENTRY_SEPARATOR = "\n";
    private static final String BLOCK_FIELD_SEPARATOR = ";";

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
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        mapsFile = new File(plugin.getDataFolder(), "maps.yml");
        if (!mapsFile.exists()) {
            try {
                mapsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mapsConfig = YamlConfiguration.loadConfiguration(mapsFile);

        statsFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);

        npcsFile = new File(plugin.getDataFolder(), "npcs.yml");
        if (!npcsFile.exists()) {
            try {
                npcsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
    }

    private void loadMaps() {
        if (mapsConfig.getConfigurationSection("maps") == null) return;

        for (String key : mapsConfig.getConfigurationSection("maps").getKeys(false)) {
            String path = "maps." + key;
            MapData map = new MapData(key);

            if (mapsConfig.contains(path + ".hubSpawn")) {
                map.setHubSpawn(deserializeLocation(path + ".hubSpawn"));
            }
            map.setMinPlayers(mapsConfig.getInt(path + ".minPlayers", 2));
            map.setMaxPlayers(mapsConfig.getInt(path + ".maxPlayers", 8));
            map.setWaitTime(mapsConfig.getInt(path + ".waitTime", 30));

            if (mapsConfig.contains(path + ".spawnOffset.x")) {
                map.setSpawnOffset(
                        mapsConfig.getDouble(path + ".spawnOffset.x"),
                        mapsConfig.getDouble(path + ".spawnOffset.y"),
                        mapsConfig.getDouble(path + ".spawnOffset.z"),
                        (float) mapsConfig.getDouble(path + ".spawnOffset.yaw", 0),
                        (float) mapsConfig.getDouble(path + ".spawnOffset.pitch", 0)
                );
            }

            map.setSize(
                    mapsConfig.getInt(path + ".sizeX", 0),
                    mapsConfig.getInt(path + ".sizeY", 0),
                    mapsConfig.getInt(path + ".sizeZ", 0)
            );

            List<TemplateBlockData> templateBlocks = loadTemplateBlocks(path);
            map.setTemplateBlocks(templateBlocks);
            map.setTntRelativePositions(loadTntPositions(path, templateBlocks));

            maps.put(key.toLowerCase(), map);
        }
    }

    public void saveMap(MapData map) {
        String path = "maps." + map.getName();

        if (map.getHubSpawn() != null) {
            serializeLocation(path + ".hubSpawn", map.getHubSpawn());
        }
        mapsConfig.set(path + ".minPlayers", map.getMinPlayers());
        mapsConfig.set(path + ".maxPlayers", map.getMaxPlayers());
        mapsConfig.set(path + ".waitTime", map.getWaitTime());

        if (map.isSpawnSet()) {
            mapsConfig.set(path + ".spawnOffset.x", map.getSpawnOffsetX());
            mapsConfig.set(path + ".spawnOffset.y", map.getSpawnOffsetY());
            mapsConfig.set(path + ".spawnOffset.z", map.getSpawnOffsetZ());
            mapsConfig.set(path + ".spawnOffset.yaw", map.getSpawnYaw());
            mapsConfig.set(path + ".spawnOffset.pitch", map.getSpawnPitch());
        }

        mapsConfig.set(path + ".sizeX", map.getSizeX());
        mapsConfig.set(path + ".sizeY", map.getSizeY());
        mapsConfig.set(path + ".sizeZ", map.getSizeZ());

        mapsConfig.set(path + ".templateFormat", "gzip-base64-blockdata-v1");
        mapsConfig.set(path + ".templateBlocksCompressed", compressTemplateBlocks(map.getTemplateBlocks()));
        mapsConfig.set(path + ".templateBlocks", null);

        List<String> tntStrings = new ArrayList<>();
        for (int[] pos : map.getTntRelativePositions()) {
            tntStrings.add(pos[0] + "," + pos[1] + "," + pos[2]);
        }
        mapsConfig.set(path + ".tntRelativePositions", tntStrings);

        maps.put(map.getName().toLowerCase(), map);
        saveMapsConfig();
    }

    public MapData getMap(String name) {
        return maps.get(name.toLowerCase());
    }

    public Map<String, MapData> getMaps() {
        return maps;
    }

    public MapData getOrCreateMap(String name) {
        MapData map = maps.get(name.toLowerCase());
        if (map == null) {
            map = new MapData(name);
            maps.put(name.toLowerCase(), map);
        }
        return map;
    }

    public void addWin(UUID uuid, String playerName) {
        String path = "players." + uuid;
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
            list.add(new PlayerData(
                    UUID.fromString(key),
                    statsConfig.getString(path + ".name", "Unknown"),
                    statsConfig.getInt(path + ".wins", 0)
            ));
        }

        list.sort((a, b) -> Integer.compare(b.getWins(), a.getWins()));
        return list.subList(0, Math.min(limit, list.size()));
    }

    public void saveNPC(String id, Location location, float direction, String skinName) {
        String path = "npcs." + id;
        serializeNpcLocation(path + ".location", location);
        npcsConfig.set(path + ".direction", direction);
        npcsConfig.set(path + ".skin", skinName != null ? skinName : "");
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
            Location loc = deserializeNpcLocation(path + ".location");
            float direction = (float) npcsConfig.getDouble(path + ".direction", 0);
            String skinName = npcsConfig.getString(path + ".skin", "");
            if (loc != null) {
                result.put(id, new Object[]{loc, direction, skinName});
            }
        }
        return result;
    }

    private List<TemplateBlockData> loadTemplateBlocks(String path) {
        String compressed = mapsConfig.getString(path + ".templateBlocksCompressed", "");
        if (!compressed.isEmpty()) {
            return decompressTemplateBlocks(compressed);
        }

        List<String> legacy = mapsConfig.getStringList(path + ".templateBlocks");
        List<TemplateBlockData> blocks = new ArrayList<>(legacy.size());
        for (String entry : legacy) {
            String[] parts = entry.split(",", 4);
            if (parts.length < 4) continue;

            try {
                int relX = Integer.parseInt(parts[0]);
                int relY = Integer.parseInt(parts[1]);
                int relZ = Integer.parseInt(parts[2]);
                Material material = Material.getMaterial(parts[3]);
                if (material == null) {
                    continue;
                }
                String blockData = Bukkit.createBlockData(material).getAsString();
                blocks.add(new TemplateBlockData(relX, relY, relZ, blockData));
            } catch (Exception ignored) {
            }
        }
        return blocks;
    }

    private List<int[]> loadTntPositions(String path, List<TemplateBlockData> templateBlocks) {
        List<String> tntStrings = mapsConfig.getStringList(path + ".tntRelativePositions");
        List<int[]> tntPositions = new ArrayList<>();

        for (String entry : tntStrings) {
            String[] parts = entry.split(",");
            if (parts.length != 3) continue;

            try {
                tntPositions.add(new int[]{
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                });
            } catch (NumberFormatException ignored) {
            }
        }

        if (!tntPositions.isEmpty()) {
            return tntPositions;
        }

        for (TemplateBlockData block : templateBlocks) {
            if (block.blockData().startsWith("minecraft:tnt")) {
                tntPositions.add(new int[]{block.relX(), block.relY(), block.relZ()});
            }
        }
        return tntPositions;
    }

    private String compressTemplateBlocks(List<TemplateBlockData> blocks) {
        StringBuilder builder = new StringBuilder(blocks.size() * 32);
        for (TemplateBlockData block : blocks) {
            builder.append(block.relX()).append(BLOCK_FIELD_SEPARATOR)
                    .append(block.relY()).append(BLOCK_FIELD_SEPARATOR)
                    .append(block.relZ()).append(BLOCK_FIELD_SEPARATOR)
                    .append(block.blockData())
                    .append(BLOCK_ENTRY_SEPARATOR);
        }

        byte[] input = builder.toString().getBytes(StandardCharsets.UTF_8);
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteStream)) {
            gzipOutputStream.write(input);
            gzipOutputStream.finish();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to compress map data", e);
        }
    }

    private List<TemplateBlockData> decompressTemplateBlocks(String compressed) {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(compressed));
             GZIPInputStream gzipInputStream = new GZIPInputStream(byteStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            gzipInputStream.transferTo(outputStream);
            String decoded = outputStream.toString(StandardCharsets.UTF_8);
            List<TemplateBlockData> blocks = new ArrayList<>();

            for (String entry : decoded.split(BLOCK_ENTRY_SEPARATOR)) {
                if (entry.isEmpty()) {
                    continue;
                }

                String[] parts = entry.split(BLOCK_FIELD_SEPARATOR, 4);
                if (parts.length < 4) {
                    continue;
                }

                blocks.add(new TemplateBlockData(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        parts[3]
                ));
            }
            return blocks;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read compressed map data", e);
        }
    }

    private void serializeLocation(String path, Location loc) {
        mapsConfig.set(path + ".world", loc.getWorld().getName());
        mapsConfig.set(path + ".x", loc.getX());
        mapsConfig.set(path + ".y", loc.getY());
        mapsConfig.set(path + ".z", loc.getZ());
        mapsConfig.set(path + ".yaw", loc.getYaw());
        mapsConfig.set(path + ".pitch", loc.getPitch());
    }

    private void serializeNpcLocation(String path, Location loc) {
        npcsConfig.set(path + ".world", loc.getWorld().getName());
        npcsConfig.set(path + ".x", loc.getX());
        npcsConfig.set(path + ".y", loc.getY());
        npcsConfig.set(path + ".z", loc.getZ());
        npcsConfig.set(path + ".yaw", loc.getYaw());
        npcsConfig.set(path + ".pitch", loc.getPitch());
    }

    private Location deserializeLocation(String path) {
        if (!mapsConfig.contains(path + ".world")) return null;
        World world = Bukkit.getWorld(mapsConfig.getString(path + ".world"));
        if (world == null) return null;
        return new Location(
                world,
                mapsConfig.getDouble(path + ".x"),
                mapsConfig.getDouble(path + ".y"),
                mapsConfig.getDouble(path + ".z"),
                (float) mapsConfig.getDouble(path + ".yaw", 0),
                (float) mapsConfig.getDouble(path + ".pitch", 0)
        );
    }

    private Location deserializeNpcLocation(String path) {
        if (!npcsConfig.contains(path + ".world")) return null;
        World world = Bukkit.getWorld(npcsConfig.getString(path + ".world"));
        if (world == null) return null;
        return new Location(
                world,
                npcsConfig.getDouble(path + ".x"),
                npcsConfig.getDouble(path + ".y"),
                npcsConfig.getDouble(path + ".z"),
                (float) npcsConfig.getDouble(path + ".yaw", 0),
                (float) npcsConfig.getDouble(path + ".pitch", 0)
        );
    }

    private void saveMapsConfig() {
        try {
            mapsConfig.save(mapsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveStatsConfig() {
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveNpcsConfig() {
        try {
            npcsConfig.save(npcsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
