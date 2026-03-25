package com.bettertntrun.models;

import java.util.UUID;

public class PlayerData {
    private UUID uuid;
    private String name;
    private int wins;

    public PlayerData(UUID uuid, String name, int wins) {
        this.uuid = uuid;
        this.name = name;
        this.wins = wins;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getWins() { return wins; }
    public void addWin() { this.wins++; }
}
