package com.bettertntrun.models;

import org.bukkit.Location;

public class MapData {
    private String name;
    private Location spawn;
    private Location pos1;
    private Location pos2;
    private Location hubSpawn;
    private int minPlayers;
    private int maxPlayers;
    private int waitTime;

    public MapData(String name) {
        this.name = name;
        this.minPlayers = 2;
        this.maxPlayers = 8;
        this.waitTime = 30;
    }

    public String getName() { return name; }
    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }
    public Location getHubSpawn() { return hubSpawn; }
    public void setHubSpawn(Location hubSpawn) { this.hubSpawn = hubSpawn; }
    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public int getWaitTime() { return waitTime; }
    public void setWaitTime(int waitTime) { this.waitTime = waitTime; }
}
