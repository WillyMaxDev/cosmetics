package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.cosmetic.CosmeticManager;
import dev.netsu.cosmetics.util.ColorUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CosmeticApplyListener implements Listener {

    private final NetsuCosmetics plugin;

    public CosmeticApplyListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
    }

    private void msg(Audience audience, String key, String... replacements) {
        String text = plugin.getMessages().get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2)
            text = text.replace(replacements[i], replacements[i + 1]);
        audience.sendMessage(ColorUtil.toComponent(plugin.getMessages().prefix() + " " + text));
    }

    public NamespacedKey cosmeticKey(String cosmeticId) {
        return new NamespacedKey(plugin, "cosmetic_" + cosmeticId);
    }

    public boolean itemTieneCosmetico(ItemStack item, String cosmeticId) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(cosmeticKey(cosmeticId), PersistentDataType.BYTE);
    }

    private boolean isBoots(Material mat)      { return mat.name().endsWith("_BOOTS"); }
    private boolean isChestplate(Material mat) { return mat.name().endsWith("_CHESTPLATE"); }
    private boolean isSword(Material mat)      { return mat.name().endsWith("_SWORD"); }
    private boolean isBowOrCrossbow(Material mat) { return mat == Material.BOW || mat == Material.CROSSBOW; }

    private boolean itemValidoParaCosmetic(CosmeticData data, Material mat) {
        return switch (data.itemAplicable) {
            case "BOOTS"           -> isBoots(mat);
            case "CHESTPLATE"      -> isChestplate(mat);
            case "SWORD"           -> isSword(mat);
            case "BOW"             -> mat == Material.BOW;
            case "CROSSBOW"        -> mat == Material.CROSSBOW;
            case "BOW_OR_CROSSBOW" -> isBowOrCrossbow(mat);
            default -> mat.name().equalsIgnoreCase(data.itemAplicable);
        };
    }

    private boolean isHelmetSlot(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return false;
        if (!event.getClickedInventory().equals(event.getWhoClicked().getInventory())) return false;
        return event.getSlot() == 39;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {}

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack item = event.getItem();
        if (item != null && plugin.getCosmeticManager().isCosmeticSkull(item))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        CosmeticManager cm = plugin.getCosmeticManager();
        ItemStack cursor  = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        if (cursor != null && !cursor.getType().isAir() && cm.isCosmeticSkull(cursor)) {

            if (isHelmetSlot(event)) {
                event.setCancelled(true);
                ItemStack skull = cursor.clone();
                event.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                player.getInventory().addItem(skull).values()
                    .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                return;
            }

            CosmeticData data = cm.getCosmeticFromSkull(cursor);
            if (data == null) return;

            if (clicked != null && !clicked.getType().isAir()) {
                if (!itemValidoParaCosmetic(data, clicked.getType())) {
                    msg(player, "cosmetico_item_incorrecto");
                    event.setCancelled(true);
                    return;
                }
                if (itemTieneCosmetico(clicked, data.id)) {
                    msg(player, "cosmetico_ya_aplicado");
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);
                ItemMeta meta = clicked.getItemMeta();
                if (meta == null) return;
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                for (String line : data.loreAplicado)
                    lore.add(ColorUtil.toComponent(line.replace("{nombre}", data.nombreDisplay)));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(cosmeticKey(data.id), PersistentDataType.BYTE, (byte) 1);
                clicked.setItemMeta(meta);
                cursor.setAmount(0);
                event.setCursor(new ItemStack(Material.AIR));
                msg(player, "cosmetico_aplicado", "{nombre}", data.nombreDisplay);
                return;
            }

            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                return;
            }
            return;
        }

        if (clicked != null && !clicked.getType().isAir() && cm.isCosmeticSkull(clicked)) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                return;
            }
            if (isHelmetSlot(event)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
