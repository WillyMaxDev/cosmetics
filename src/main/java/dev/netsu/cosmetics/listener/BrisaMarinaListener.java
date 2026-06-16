package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class BrisaMarinaListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();

    // Duración de todas las animaciones de partículas: 3 segundos = 60 ticks
    private static final int DURACION_TICKS = 60;

    public BrisaMarinaListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        CosmeticData data = getBrisaData();
        if (data == null || !swordHasCosmetico(weapon, data)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Location targetLoc = event.getEntity().getLocation()
                .add(0, event.getEntity().getHeight() / 2.0, 0);

        if (isCritical(player)) {
            spawnCriticoMarino(player, targetLoc);
        } else {
            spawnOlaAgua(player, targetLoc);
        }
    }

    // ─── OLA DE AGUA (golpe normal) ───────────────────────────────────────────

    /**
     * Anillo de agua que se expande desde el centro del golpe.
     * Las partículas duran exactamente 3 segundos (60 ticks) antes de detenerse.
     */
    private void spawnOlaAgua(Player player, Location center) {
        player.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH, 0.7f, 1.2f);
        player.getWorld().playSound(center, Sound.BLOCK_WATER_AMBIENT, 0.4f, 1.5f);

        new BukkitRunnable() {
            double radio = 0.1;
            int ticks = 0;

            @Override
            public void run() {
                // Paramos exactamente a los 60 ticks (3 segundos)
                if (ticks++ >= DURACION_TICKS) {
                    cancel();
                    return;
                }

                int puntos = 16;
                for (int i = 0; i < puntos; i++) {
                    double angle = Math.toRadians(i * (360.0 / puntos));
                    Location loc = center.clone().add(
                            Math.cos(angle) * radio,
                            0,
                            Math.sin(angle) * radio
                    );

                    if (isBlockColliding(loc)) continue;

                    try {
                        player.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 1, 0, 0.04, 0, 0.008);
                        if (ticks % 2 == 0) {
                            player.getWorld().spawnParticle(Particle.SPLASH, loc, 1, 0.04, 0, 0.04, 0.015);
                        }
                    } catch (Exception ignored) {}
                }

                // El radio crece más lento para que dure los 3 segundos
                radio += 0.08;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ─── CRÍTICO MARINO ───────────────────────────────────────────────────────

    /**
     * Crítico: anillo más grande y con burbujas, SIN ninguna partícula instantánea
     * de tipo "warden" ni burst brusco. Todo dura exactamente 3 segundos (60 ticks).
     */
    private void spawnCriticoMarino(Player player, Location center) {
        player.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.8f, 0.9f);
        player.getWorld().playSound(center, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.6f, 1.3f);
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_SPLASH, 0.5f, 1.6f);

        // Anillo principal (más puntos y más grande que el normal)
        new BukkitRunnable() {
            double radio = 0.1;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= DURACION_TICKS) {
                    cancel();
                    return;
                }

                int puntos = 20;
                for (int i = 0; i < puntos; i++) {
                    double angle = Math.toRadians(i * (360.0 / puntos));
                    Location loc = center.clone().add(
                            Math.cos(angle) * radio,
                            0,
                            Math.sin(angle) * radio
                    );

                    if (isBlockColliding(loc)) continue;

                    try {
                        player.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 1, 0, 0.04, 0, 0.01);
                        if (ticks % 2 == 0) {
                            player.getWorld().spawnParticle(Particle.SPLASH, loc, 1, 0.05, 0, 0.05, 0.02);
                        }
                    } catch (Exception ignored) {}
                }

                // Burbujas dispersas alrededor del anillo
                for (int i = 0; i < 8; i++) {
                    double angle = Math.toRadians(i * (360.0 / 8));
                    Location scatter = center.clone().add(
                            Math.cos(angle) * radio * 1.2,
                            RNG.nextDouble() * 0.4,
                            Math.sin(angle) * radio * 1.2
                    );

                    if (isBlockColliding(scatter)) continue;

                    try {
                        player.getWorld().spawnParticle(Particle.BUBBLE_POP, scatter, 1, 0.04, 0.06, 0.04, 0.06);
                    } catch (Exception ignored) {}
                }

                radio += 0.08;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ─── UTILIDADES ──────────────────────────────────────────────────────────

    private boolean isBlockColliding(Location loc) {
        Block block = loc.getBlock();
        return block.getType().isSolid();
    }

    private boolean isCritical(Player player) {
        return player.getFallDistance() > 0
                && !player.isOnGround()
                && player.getVelocity().getY() < 0
                && !player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
    }

    private boolean swordHasCosmetico(ItemStack item, CosmeticData data) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "cosmetic_" + data.id);
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private CosmeticData getBrisaData() {
        for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
            if (d.tipo.equalsIgnoreCase("BRISA_MARINA")) return d;
        }
        return null;
    }
}
