package com.bettertntrun.models;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final String name;
    private final int wins;

    public PlayerData(UUID uuid, String name, int wins) {
        this.uuid = uuid;
        this.name = name;
        this.wins = wins;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getWins() { return wins; }
}
