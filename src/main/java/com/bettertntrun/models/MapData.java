package com.bettertntrun.models;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Template map data. Blocks are stored as relative positions to the map origin.
 * The spawn is also stored as a relative offset.
 */
public class MapData {
    private final String name;
    private Location hubSpawn;
    private double spawnOffsetX;
    private double spawnOffsetY;
    private double spawnOffsetZ;
    private float spawnYaw;
    private float spawnPitch;
    private boolean spawnSet = false;
    private List<TemplateBlockData> templateBlocks = new ArrayList<>();
    private List<int[]> tntRelativePositions = new ArrayList<>();
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int minPlayers;
    private int maxPlayers;
    private int waitTime;

    public MapData(String name) {
        this.name = name;
        this.minPlayers = 2;
        this.maxPlayers = 8;
        this.waitTime = 30;
    }

    public String getName() {
        return name;
    }

    public Location getHubSpawn() {
        return hubSpawn;
    }

    public void setHubSpawn(Location hubSpawn) {
        this.hubSpawn = hubSpawn;
    }

    public double getSpawnOffsetX() {
        return spawnOffsetX;
    }

    public double getSpawnOffsetY() {
        return spawnOffsetY;
    }

    public double getSpawnOffsetZ() {
        return spawnOffsetZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public boolean isSpawnSet() {
        return spawnSet;
    }

    public void setSpawnOffset(double x, double y, double z, float yaw, float pitch) {
        this.spawnOffsetX = x;
        this.spawnOffsetY = y;
        this.spawnOffsetZ = z;
        this.spawnYaw = yaw;
        this.spawnPitch = pitch;
        this.spawnSet = true;
    }

    public List<TemplateBlockData> getTemplateBlocks() {
        return templateBlocks;
    }

    public void setTemplateBlocks(List<TemplateBlockData> templateBlocks) {
        this.templateBlocks = templateBlocks;
    }

    public List<int[]> getTntRelativePositions() {
        return tntRelativePositions;
    }

    public void setTntRelativePositions(List<int[]> tntRelativePositions) {
        this.tntRelativePositions = tntRelativePositions;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public void setSize(int x, int y, int z) {
        this.sizeX = x;
        this.sizeY = y;
        this.sizeZ = z;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }
}
