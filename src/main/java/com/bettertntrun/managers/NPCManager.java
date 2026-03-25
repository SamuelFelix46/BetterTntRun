package com.bettertntrun.managers;

import com.bettertntrun.BetterTntRun;
import com.bettertntrun.models.MapData;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class NPCManager {

    private final BetterTntRun plugin;
    private final Map<UUID, String> npcMapLinks = new HashMap<>(); // Entity UUID -> mapName
    private final Map<UUID, Float> npcDirections = new HashMap<>(); // Entity UUID -> default yaw
    private final Map<String, UUID> npcConfigIds = new HashMap<>(); // config id -> entity UUID

    public NPCManager(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    public void spawnNPC(String mapName, Location location, float direction) {
        ArmorStand npc = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        npc.setCustomName("§6§l[TNTRun] §e" + mapName);
        npc.setCustomNameVisible(true);
        npc.setGravity(false);
        npc.setInvulnerable(true);
        npc.setCanPickupItems(false);
        npc.setArms(true);
        npc.setBasePlate(false);

        Location npcLoc = npc.getLocation();
        npcLoc.setYaw(direction);
        npc.teleport(npcLoc);

        npc.setMetadata("tntrun_npc", new FixedMetadataValue(plugin, mapName));

        npcMapLinks.put(npc.getUniqueId(), mapName);
        npcDirections.put(npc.getUniqueId(), direction);

        String configId = UUID.randomUUID().toString().substring(0, 8);
        npcConfigIds.put(configId, npc.getUniqueId());
        plugin.getConfigManager().saveNPC(configId, mapName, location, direction);

        startLookTask(npc);
    }

    private void startLookTask(ArmorStand npc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (npc.isDead() || !npc.isValid()) {
                    cancel();
                    return;
                }

                Player nearest = null;
                double nearestDist = 5.0;

                for (Entity entity : npc.getNearbyEntities(5, 5, 5)) {
                    if (entity instanceof Player player) {
                        double dist = player.getLocation().distance(npc.getLocation());
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = player;
                        }
                    }
                }

                Location npcLoc = npc.getLocation();
                if (nearest != null) {
                    Location playerLoc = nearest.getLocation();
                    double dx = playerLoc.getX() - npcLoc.getX();
                    double dz = playerLoc.getZ() - npcLoc.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    npcLoc.setYaw(yaw);
                } else {
                    Float defaultYaw = npcDirections.get(npc.getUniqueId());
                    if (defaultYaw != null) npcLoc.setYaw(defaultYaw);
                }
                npc.teleport(npcLoc);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    public String getLinkedMap(UUID entityUUID) {
        return npcMapLinks.get(entityUUID);
    }

    public boolean isNPC(Entity entity) {
        return entity.hasMetadata("tntrun_npc");
    }

    public void loadAllNPCs() {
        Map<String, Object[]> npcs = plugin.getConfigManager().loadNPCs();
        for (Map.Entry<String, Object[]> entry : npcs.entrySet()) {
            Location loc = (Location) entry.getValue()[0];
            String mapName = (String) entry.getValue()[1];
            float direction = (float) entry.getValue()[2];

            if (loc.getWorld() == null) continue;

            // Remove existing NPCs at that location
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 1, 1, 1)) {
                if (entity instanceof ArmorStand && entity.hasMetadata("tntrun_npc")) {
                    entity.remove();
                }
            }

            ArmorStand npc = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            npc.setCustomName("§6§l[TNTRun] §e" + mapName);
            npc.setCustomNameVisible(true);
            npc.setGravity(false);
            npc.setInvulnerable(true);
            npc.setCanPickupItems(false);
            npc.setArms(true);
            npc.setBasePlate(false);

            Location npcLoc = npc.getLocation();
            npcLoc.setYaw(direction);
            npc.teleport(npcLoc);

            npc.setMetadata("tntrun_npc", new FixedMetadataValue(plugin, mapName));
            npcMapLinks.put(npc.getUniqueId(), mapName);
            npcDirections.put(npc.getUniqueId(), direction);
            npcConfigIds.put(entry.getKey(), npc.getUniqueId());

            startLookTask(npc);
        }
    }

    public void removeAllNPCs() {
        for (UUID uuid : npcMapLinks.keySet()) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null) entity.remove();
        }
        npcMapLinks.clear();
        npcDirections.clear();
    }
}
