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
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AuraVeraniegarListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();

    private final Map<UUID, Long> lastMoved = new HashMap<>();
    private final Map<UUID, Integer> lastEffectSlot = new HashMap<>();
    private final AtomicInteger fishCounter = new AtomicInteger(0);

    private static final double[][] SPAWN_POINTS = {
            { 0.0, 1.7, 0.55},
            { 0.3, 1.7, 0.45},
            {-0.3, 1.7, 0.45},
            { 0.0, 1.7, -0.55},
            { 0.3, 1.7, -0.45},
            {-0.3, 1.7, -0.45},
            { 0.55, 1.5, 0.0 },
            { 0.55, 1.5, 0.25},
            { 0.55, 1.5, -0.25},
            {-0.55, 1.5, 0.0 },
            {-0.55, 1.5, 0.25},
            {-0.55, 1.5, -0.25},
            { 0.35, 1.2, 0.4 },
            {-0.35, 1.2, 0.4 },
            { 0.35, 1.2, -0.4 },
            {-0.35, 1.2, -0.4 },
            { 0.0, 1.1, 0.5 },
            { 0.0, 1.1, -0.5 },
            { 0.5, 1.1, 0.0 },
            {-0.5, 1.1, 0.0 },
            { 0.4, 0.8, 0.3 },
            {-0.4, 0.8, 0.3 },
            { 0.4, 0.8, -0.3 },
            {-0.4, 0.8, -0.3 },
            { 0.0, 0.7, 0.45},
            { 0.0, 0.7, -0.45}
    };

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
                    if (!swordHasCosmetico(player, data)) continue;

                    Location loc = player.getLocation();
                    World world = player.getWorld();

                    if (RNG.nextInt(3) == 0) {
                        double[] point = SPAWN_POINTS[RNG.nextInt(SPAWN_POINTS.length)];
                        world.spawnParticle(Particle.SPLASH, loc.clone().add(point[0], point[1], point[2]), 1, 0, 0, 0, 0);
                    }

                    long quietoMs = now - lastMoved.getOrDefault(player.getUniqueId(), now);
                    if (quietoMs < 5000L) continue;

                    int slot = (int) (quietoMs / 5000L);
                    int lastSlot = lastEffectSlot.getOrDefault(player.getUniqueId(), 0);

                    if (slot > lastSlot) {
                        lastEffectSlot.put(player.getUniqueId(), slot);
                        triggerSplashEffect(player);
                        if (slot % 2 == 0) spawnPezTropical(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void triggerSplashEffect(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        world.spawnParticle(Particle.SPLASH, loc.clone().add(0, 0.1, 0), 30, 0.5, 0.2, 0.5, 0.1);
        world.spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0, 0.1, 0), 15, 0.4, 0.1, 0.4, 0.05);
        world.spawnParticle(Particle.FALLING_WATER, loc.clone().add(0, 0.1, 0), 20, 0.6, 0.3, 0.6, 0.08);

        world.playSound(loc, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.2f);
        world.playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 0.6f, 1.3f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 30) {
                    cancel();
                    return;
                }

                double progress = ticks / 30.0;
                double height = progress * 2.5;
                double radius = 0.3 + (progress * 0.8);
                int points = 16;

                for (int i = 0; i < points; i++) {
                    double angle = Math.toRadians(i * (360.0 / points) + (ticks * 5));
                    Location particleLoc = loc.clone().add(
                            Math.cos(angle) * radius,
                            height + 0.1,
                            Math.sin(angle) * radius
                    );

                    world.spawnParticle(Particle.SPLASH, particleLoc, 2, 0.1, 0.05, 0.1, 0.03);

                    if (ticks % 2 == 0) {
                        world.spawnParticle(Particle.BUBBLE_POP, particleLoc, 1, 0.05, 0.02, 0.05, 0.02);
                    }

                    if (ticks % 3 == 0) {
                        world.spawnParticle(Particle.FALLING_WATER, particleLoc, 1, 0.08, 0.03, 0.08, 0.05);
                    }
                }

                Location centerLoc = loc.clone().add(0, height + 0.1, 0);
                world.spawnParticle(Particle.SPLASH, centerLoc, 3, 0.2, 0.1, 0.2, 0.04);

                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.BUBBLE_POP, centerLoc, 2, 0.15, 0.08, 0.15, 0.03);
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

    private boolean swordHasCosmetico(Player player, CosmeticData data) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir() || !weapon.hasItemMeta()) return false;
        if (!weapon.getType().toString().contains("SWORD")) return false;

        NamespacedKey key = new NamespacedKey(plugin, "cosmetic_" + data.id);
        return weapon.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private CosmeticData getAuraData() {
        for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
            if (d.tipo.equalsIgnoreCase("AURA_VERANIEGA")) return d;
        }
        return null;
    }
}
