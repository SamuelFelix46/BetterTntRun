package com.bettertntrun.models;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * A runtime instance of a map template pasted into the void world.
 */
public class MapInstance {
    private final String templateName;
    private final int slotIndex;
    private final World world;
    private final int originX, originY, originZ;
    private final MapData template;

    public MapInstance(String templateName, int slotIndex, World world, int originX, int originY, int originZ, MapData template) {
        this.templateName = templateName;
        this.slotIndex = slotIndex;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.template = template;
    }

    public String getTemplateName() { return templateName; }
    public int getSlotIndex() { return slotIndex; }
    public World getWorld() { return world; }
    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
    public int getOriginZ() { return originZ; }
    public MapData getTemplate() { return template; }

    /**
     * Get the spawn location for this instance (absolute in void world).
     */
    public Location getSpawnLocation() {
        if (!template.isSpawnSet()) return new Location(world, originX + 0.5, originY + 5, originZ + 0.5);
        return new Location(world,
                originX + template.getSpawnOffsetX(),
                originY + template.getSpawnOffsetY(),
                originZ + template.getSpawnOffsetZ(),
                template.getSpawnYaw(),
                template.getSpawnPitch());
    }

    /**
     * Get the Y level below which a player is eliminated (25 blocks below lowest map block).
     */
    public int getEliminationY() {
        return originY - 25;
    }
}
