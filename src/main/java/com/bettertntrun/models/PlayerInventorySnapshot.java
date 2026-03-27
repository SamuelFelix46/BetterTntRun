package com.bettertntrun.models;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public record PlayerInventorySnapshot(
        ItemStack[] contents,
        ItemStack[] armorContents,
        ItemStack offHand,
        float experience,
        int level,
        int totalExperience,
        int foodLevel,
        float saturation,
        double health
) {

    public static PlayerInventorySnapshot capture(Player player) {
        return new PlayerInventorySnapshot(
                cloneItems(player.getInventory().getStorageContents()),
                cloneItems(player.getInventory().getArmorContents()),
                cloneItem(player.getInventory().getItemInOffHand()),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getHealth()
        );
    }

    public void restore(Player player) {
        player.getInventory().setStorageContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armorContents));
        player.getInventory().setItemInOffHand(cloneItem(offHand));
        player.setExp(experience);
        player.setLevel(level);
        player.setTotalExperience(totalExperience);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);

        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
        player.setHealth(Math.min(health, maxHealth));
        player.updateInventory();
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] clone = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clone[i] = cloneItem(items[i]);
        }
        return clone;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
