package com.bettertntrun.managers;

import com.bettertntrun.BetterTntRun;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Manages TNTRun NPCs as ArmorStands with leather armor and TNT head.
 * No Citizens dependency required.
 */
public class NPCManager {

    private static final double LOOK_RADIUS = 5.0;
    private final BetterTntRun plugin;
    private final Map<String, UUID> configToArmorStandUUID = new HashMap<>();

    public NPCManager(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawn a TNTRun NPC ArmorStand at the given location.
     */
    public void spawnNPC(Location location, float direction, String skinName) {
        Location spawnLoc = location.clone();
        spawnLoc.setYaw(direction);

        ArmorStand armorStand = (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        configureArmorStand(armorStand, skinName);

        // Set facing direction
        Location asLoc = armorStand.getLocation();
        asLoc.setYaw(direction);
        armorStand.teleport(asLoc);

        // Save to config
        String configId = UUID.randomUUID().toString().substring(0, 8);
        configToArmorStandUUID.put(configId, armorStand.getUniqueId());
        plugin.getConfigManager().saveNPC(configId, location, direction, skinName);

        // Start look-at-player task
        startLookTask(armorStand, direction);

        // Add custom name above
        armorStand.setCustomName("§6§l[TNTRun] §eCliquez pour jouer");
        armorStand.setCustomNameVisible(true);
    }

    /**
     * Configure the armor stand with leather armor and TNT head.
     */
    private void configureArmorStand(ArmorStand armorStand, String skinName) {
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        armorStand.setCanPickupItems(false);
        armorStand.setBasePlate(false);
        armorStand.setArms(true);
        armorStand.setVisible(true);

        // Mark as TNTRun NPC via scoreboard tag
        armorStand.addScoreboardTag("tntrun_npc");

        // TNT block as helmet
        ItemStack tntHead = new ItemStack(Material.TNT);
        armorStand.getEquipment().setHelmet(tntHead);

        // Dyed leather armor (red/orange theme)
        Color armorColor = Color.fromRGB(255, 85, 0); // Orange-red

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        chestMeta.setColor(armorColor);
        chestplate.setItemMeta(chestMeta);
        armorStand.getEquipment().setChestplate(chestplate);

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta legMeta = (LeatherArmorMeta) leggings.getItemMeta();
        legMeta.setColor(armorColor);
        leggings.setItemMeta(legMeta);
        armorStand.getEquipment().setLeggings(leggings);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bootMeta = (LeatherArmorMeta) boots.getItemMeta();
        bootMeta.setColor(armorColor);
        boots.setItemMeta(bootMeta);
        armorStand.getEquipment().setBoots(boots);
    }

    /**
     * Make the ArmorStand look at the nearest player, or face default direction.
     */
    private void startLookTask(ArmorStand armorStand, float defaultYaw) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (armorStand == null || armorStand.isDead() || !armorStand.isValid()) {
                    cancel();
                    return;
                }

                Player nearest = null;
                double nearestDistSquared = LOOK_RADIUS * LOOK_RADIUS;

                for (Entity nearby : armorStand.getNearbyEntities(LOOK_RADIUS, LOOK_RADIUS, LOOK_RADIUS)) {
                    if (nearby instanceof Player player) {
                        double distSquared = player.getLocation().distanceSquared(armorStand.getLocation());
                        if (distSquared <= nearestDistSquared) {
                            nearestDistSquared = distSquared;
                            nearest = player;
                        }
                    }
                }

                Location asLoc = armorStand.getLocation();
                if (nearest != null) {
                    Location playerLoc = nearest.getLocation();
                    double dx = playerLoc.getX() - asLoc.getX();
                    double dz = playerLoc.getZ() - asLoc.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    asLoc.setYaw(yaw);
                } else {
                    asLoc.setYaw(defaultYaw);
                }
                armorStand.teleport(asLoc);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * Check if an entity is a TNTRun NPC ArmorStand.
     */
    public boolean isNPC(Entity entity) {
        return entity instanceof ArmorStand && entity.getScoreboardTags().contains("tntrun_npc");
    }

    /**
     * Load all NPCs from config on startup.
     */
    public void loadAllNPCs() {
        Map<String, Object[]> npcs = plugin.getConfigManager().loadNPCs();

        for (Map.Entry<String, Object[]> entry : npcs.entrySet()) {
            Location loc = (Location) entry.getValue()[0];
            float direction = (float) entry.getValue()[1];
            String skinName = (String) entry.getValue()[2];

            if (loc == null || loc.getWorld() == null) continue;

            // Check if an ArmorStand NPC already exists near this location
            boolean found = false;
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                if (isNPC(entity)) {
                    configToArmorStandUUID.put(entry.getKey(), entity.getUniqueId());
                    startLookTask((ArmorStand) entity, direction);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // Create new ArmorStand NPC
                Location spawnLoc = loc.clone();
                spawnLoc.setYaw(direction);
                ArmorStand armorStand = (ArmorStand) loc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
                configureArmorStand(armorStand, skinName);

                Location asLoc = armorStand.getLocation();
                asLoc.setYaw(direction);
                armorStand.teleport(asLoc);

                armorStand.setCustomName("§6§l[TNTRun] §eCliquez pour jouer");
                armorStand.setCustomNameVisible(true);

                configToArmorStandUUID.put(entry.getKey(), armorStand.getUniqueId());
                startLookTask(armorStand, direction);
            }
        }
        plugin.getLogger().info("Loaded " + configToArmorStandUUID.size() + " TNTRun NPC(s) (ArmorStand)");
    }

    /**
     * Remove all NPC ArmorStands on disable.
     */
    public void removeAllNPCs() {
        for (UUID uuid : configToArmorStandUUID.values()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        configToArmorStandUUID.clear();
    }
}
