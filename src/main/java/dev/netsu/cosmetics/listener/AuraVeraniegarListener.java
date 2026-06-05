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
import java.util.concurrent.atomic.AtomicInteger;

public class AuraVeraniegarListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();

    private final Map<UUID, Long> lastMoved = new HashMap<>();
    private final Map<UUID, Integer> lastEffectSlot = new HashMap<>();
    private final AtomicInteger fishCounter = new AtomicInteger(0);

    public AuraVeraniegarListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
        startMainTask();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) {
            UUID uuid = event.getPlayer().getUniqueId();
            lastMoved.put(uuid, System.currentTimeMillis());
            lastEffectSlot.put(uuid, 0);
        }
    }

    @EventHandler
    public void onFishDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof TropicalFish fish && fish.isInvulnerable()) {
            event.setCancelled(true);
        }
    }

    private void startMainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                CosmeticData data = getAuraData();
                if (data == null) return;
                long now = System.currentTimeMillis();

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (!chestplateHasCosmetico(player, data)) continue;

                    long quietoMs = now - lastMoved.getOrDefault(player.getUniqueId(), now);
                    if (quietoMs < 5000L) continue;

                    int slot = (int) (quietoMs / 5000L);
                    int lastSlot = lastEffectSlot.getOrDefault(player.getUniqueId(), 0);

                    if (slot > lastSlot) {
                        lastEffectSlot.put(player.getUniqueId(), slot);
                        boolean conPez = (slot % 2 == 0);
                        spawnEfecto(player, conPez);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void spawnEfecto(Player player, boolean conPez) {
        Location center = player.getLocation().add(0, 0.1, 0);

        player.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH, 1.0f, 0.75f);
        player.getWorld().playSound(center, Sound.ENTITY_DOLPHIN_SPLASH, 0.7f, 1.2f);

        player.getWorld().spawnParticle(Particle.SPLASH, center, 60, 0.55, 0.05, 0.55, 0.18);
        player.getWorld().spawnParticle(Particle.FALLING_WATER, center, 35, 0.45, 0.1, 0.45, 0.1);

        new BukkitRunnable() {
            int frame = 0;

            @Override
            public void run() {
                if (frame++ > 10) {
                    cancel();
                    if (conPez) spawnPezTropical(player);
                    return;
                }
                double height = frame * 0.5;
                double radio = Math.max(0.05, 0.55 - frame * 0.045);

                for (int i = 0; i < 28; i++) {
                    double angle = Math.toRadians(i * (360.0 / 28) + frame * 10);
                    Location loc = center.clone().add(Math.cos(angle) * radio, height, Math.sin(angle) * radio);
                    try {
                        player.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 1, 0.02, 0.04, 0.02, 0.03);
                    } catch (Exception ignored) {}
                }
                for (int i = 0; i < 10; i++) {
                    Location loc = center.clone().add(
                            (RNG.nextDouble() - 0.5) * radio * 0.6,
                            height + RNG.nextDouble() * 0.15,
                            (RNG.nextDouble() - 0.5) * radio * 0.6
                    );
                    try {
                        player.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 1, 0.01, 0.05, 0.01, 0.04);
                    } catch (Exception ignored) {}
                }
                for (int i = 0; i < 6; i++) {
                    Location loc = center.clone().add(
                            (RNG.nextDouble() - 0.5) * 0.6,
                            height + RNG.nextDouble() * 0.3,
                            (RNG.nextDouble() - 0.5) * 0.6
                    );
                    try {
                        player.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 1, 0.03, 0.06, 0.03, 0.015);
                    } catch (Exception ignored) {}
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnPezTropical(Player player) {
        double angle = RNG.nextDouble() * Math.PI * 2;
        double dist = 1.0 + RNG.nextDouble();
        Location loc = player.getLocation().add(
                Math.cos(angle) * dist,
                0.5 + RNG.nextDouble() * 0.5,
                Math.sin(angle) * dist
        );

        int count = fishCounter.incrementAndGet();
        boolean easterEgg = (count % 50 == 0);

        player.getWorld().spawn(loc, TropicalFish.class, entity -> {
            entity.setAI(true);
            entity.setGravity(true);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            TropicalFish.Pattern[] patterns = TropicalFish.Pattern.values();
            entity.setPattern(patterns[RNG.nextInt(patterns.length)]);
            entity.setBodyColor(randomDyeColor());
            entity.setPatternColor(randomDyeColor());

            if (easterEgg) {
                entity.customName(ColorUtil.toComponent("&7Plugin creado por &fWilly_Max"));
                entity.setCustomNameVisible(true);
            }

            TropicalFish fish = entity;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!fish.isDead()) {
                        fish.getWorld().spawnParticle(Particle.SPLASH, fish.getLocation(), 5, 0.2, 0.1, 0.2, 0.05);
                        fish.remove();
                    }
                }
            }.runTaskLater(plugin, 100L);
        });
    }

    private DyeColor randomDyeColor() {
        DyeColor[] colors = DyeColor.values();
        return colors[RNG.nextInt(colors.length)];
    }

    private boolean chestplateHasCosmetico(Player player, CosmeticData data) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType().isAir() || !chest.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "cosmetic_" + data.id);
        return chest.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private CosmeticData getAuraData() {
        for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
            if (d.tipo.equalsIgnoreCase("AURA_VERANIEGA")) return d;
        }
        return null;
    }
}
