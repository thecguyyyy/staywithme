package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

final class LocalArmorEquipFallback {
    private final FriendEntity friend;

    LocalArmorEquipFallback(FriendEntity friend) {
        this.friend = friend;
    }

    boolean canEquip(String target) {
        LocalArmorRequest request = this.localArmorRequest(target);
        return request != null && this.canEquip(request);
    }

    boolean equip(String target) {
        LocalArmorRequest request = this.localArmorRequest(target);
        if (request == null || !this.canEquip(request)) {
            return false;
        }
        for (Item item : request.items()) {
            if (!(item instanceof ArmorItem armorItem)) {
                return false;
            }
            EquipmentSlot equipmentSlot = armorItem.getType().getSlot();
            if (this.friend.getItemBySlot(equipmentSlot).is(item)) {
                continue;
            }
            int inventorySlot = this.findInventorySlot(item);
            if (inventorySlot < 0) {
                return false;
            }
            ItemStack armorStack = this.friend.getInventoryProvider().getItem(inventorySlot).copy();
            ItemStack currentlyEquipped = this.friend.getItemBySlot(equipmentSlot).copy();
            this.friend.setItemSlot(equipmentSlot, armorStack);
            this.friend.getInventoryProvider().setItem(inventorySlot, currentlyEquipped);
        }
        return this.isEquipped(request);
    }

    boolean isEquipped(String target) {
        LocalArmorRequest request = this.localArmorRequest(target);
        return request != null && this.isEquipped(request);
    }

    private boolean canEquip(LocalArmorRequest request) {
        if (this.isEquipped(request)) {
            return true;
        }
        for (Item item : request.items()) {
            if (!(item instanceof ArmorItem armorItem)) {
                return false;
            }
            EquipmentSlot slot = armorItem.getType().getSlot();
            if (this.friend.getItemBySlot(slot).is(item)) {
                continue;
            }
            if (this.findInventorySlot(item) < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isEquipped(LocalArmorRequest request) {
        for (Item item : request.items()) {
            if (!(item instanceof ArmorItem armorItem)
                    || !this.friend.getItemBySlot(armorItem.getType().getSlot()).is(item)) {
                return false;
            }
        }
        return true;
    }

    private int findInventorySlot(Item item) {
        if (item == null) {
            return -1;
        }
        return this.friend.getInventoryProvider().findFirstSlot(stack -> stack.is(item));
    }

    private LocalArmorRequest localArmorRequest(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return null;
        }
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return switch (normalized) {
            case "leather" -> new LocalArmorRequest("leather", new Item[]{
                    Items.LEATHER_HELMET,
                    Items.LEATHER_CHESTPLATE,
                    Items.LEATHER_LEGGINGS,
                    Items.LEATHER_BOOTS
            });
            case "iron" -> new LocalArmorRequest("iron", new Item[]{
                    Items.IRON_HELMET,
                    Items.IRON_CHESTPLATE,
                    Items.IRON_LEGGINGS,
                    Items.IRON_BOOTS
            });
            case "gold", "golden" -> new LocalArmorRequest("golden", new Item[]{
                    Items.GOLDEN_HELMET,
                    Items.GOLDEN_CHESTPLATE,
                    Items.GOLDEN_LEGGINGS,
                    Items.GOLDEN_BOOTS
            });
            case "diamond" -> new LocalArmorRequest("diamond", new Item[]{
                    Items.DIAMOND_HELMET,
                    Items.DIAMOND_CHESTPLATE,
                    Items.DIAMOND_LEGGINGS,
                    Items.DIAMOND_BOOTS
            });
            case "netherite" -> new LocalArmorRequest("netherite", new Item[]{
                    Items.NETHERITE_HELMET,
                    Items.NETHERITE_CHESTPLATE,
                    Items.NETHERITE_LEGGINGS,
                    Items.NETHERITE_BOOTS
            });
            default -> this.localArmorItemRequest(normalized);
        };
    }

    private LocalArmorRequest localArmorItemRequest(String normalizedTarget) {
        ResourceLocation id = ResourceLocation.tryParse(normalizedTarget.contains(":")
                ? normalizedTarget
                : "minecraft:" + normalizedTarget);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (!(item instanceof ArmorItem)) {
            return null;
        }
        String normalized = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
        return new LocalArmorRequest(normalized, new Item[]{item});
    }

    private record LocalArmorRequest(String normalizedTarget, Item[] items) {
    }
}
