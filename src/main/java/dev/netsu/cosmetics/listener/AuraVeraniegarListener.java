package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.util.ColorUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.TropicalFish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AuraVeraniegarListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();

    private final Map<UUID, Long> lastMoved      = new HashMap<>();
    private final Map<UUID, Integer> lastEffectSlot = new HashMap<>();

    private static int fishCounter = 0;

    private static final DyeColor[] DYE_COLORS     = DyeColor.values();
    private static final TropicalFish.Pattern[] PATTERNS = TropicalFish.Pattern.values();

    private CosmeticData cachedData = null;
    private NamespacedKey cachedKey = null;

    public AuraVeraniegarListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
        startMainTask();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        UUID uuid = event.getPlayer().getUniqueId();
        lastMoved.put(uuid, System.currentTimeMillis());
        lastEffectSlot.put(uuid, 0);
    }

    @EventHandler
    public void onFishDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof TropicalFish fish && fish.isInvulnerable())
            event.setCancelled(true);
    }

    private void startMainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (getAuraData() == null) return;
                long now = System.currentTimeMillis();

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (!chestplateHasCosmetico(player)) continue;

                    long quietoMs = now - lastMoved.getOrDefault(player.getUniqueId(), now);
                    if (quietoMs < 5000L) continue;

                    int slot = (int) (quietoMs / 5000L);
                    int lastSlot = lastEffectSlot.getOrDefault(player.getUniqueId(), 0);

                    if (slot > lastSlot) {
                        lastEffectSlot.put(player.getUniqueId(), slot);
                        if (slot % 2 == 0) spawnPezTropical(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnPezTropical(Player player) {
        double angle = RNG.nextDouble() * Math.PI * 2;
        double dist  = 1.0 + RNG.nextDouble();
        Location loc = player.getLocation().add(
            Math.cos(angle) * dist,
            0.5 + RNG.nextDouble() * 0.5,
            Math.sin(angle) * dist
        );

        fishCounter++;
        boolean easterEgg = (fishCounter % 50 == 0);

        player.getWorld().spawn(loc, TropicalFish.class, entity -> {
            entity.setAI(true);
            entity.setGravity(true);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setPattern(PATTERNS[RNG.nextInt(PATTERNS.length)]);
            entity.setBodyColor(DYE_COLORS[RNG.nextInt(DYE_COLORS.length)]);
            entity.setPatternColor(DYE_COLORS[RNG.nextInt(DYE_COLORS.length)]);

            if (easterEgg) {
                entity.customName(ColorUtil.toComponent("&7Plugin creado por &fWilly_Max"));
                entity.setCustomNameVisible(true);
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!entity.isDead()) {
                        entity.getWorld().spawnParticle(Particle.SPLASH, entity.getLocation(), 5, 0.2, 0.1, 0.2, 0.05);
                        entity.remove();
                    }
                }
            }.runTaskLater(plugin, 100L);
        });
    }

    private boolean chestplateHasCosmetico(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType().isAir() || !chest.hasItemMeta()) return false;
        if (cachedKey == null) {
            CosmeticData d = getAuraData();
            if (d == null) return false;
            cachedKey = new NamespacedKey(plugin, "cosmetic_" + d.id);
        }
        return chest.getItemMeta().getPersistentDataContainer().has(cachedKey, PersistentDataType.BYTE);
    }

    private CosmeticData getAuraData() {
        if (cachedData == null) {
            for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
                if (d.tipo.equalsIgnoreCase("AURA_VERANIEGA")) {
                    cachedData = d;
                    break;
                }
            }
        }
        return cachedData;
    }
}
