package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.util.WorldGuardHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EstelarSolarListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();

    // Proyectiles con el cosmetico activo
    private final Set<UUID> trackedArrows = new HashSet<>();
    // Timestamp de la última vez que se emitió la partícula de cola (cada 3s)
    private final Map<UUID, Long> lastTailParticle = new HashMap<>();

    // Restauración de bloques (igual que CaminoSoleado)
    private final Map<Location, BlockData> originalBlock = new HashMap<>();
    private final Map<Location, BlockData> originalAbove = new HashMap<>();
    private final Map<Location, BlockData> originalAbove2 = new HashMap<>();
    private final Map<Location, Long> blockTimestamps = new HashMap<>();

    private static final Set<Material> BLOQUEADOS = new HashSet<>(Arrays.asList(
            Material.BEDROCK, Material.WATER, Material.LAVA,
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY,
            Material.END_PORTAL_FRAME, Material.BARRIER
    ));

    private static final Set<Material> TALL_TOP = new HashSet<>(Arrays.asList(
            Material.TALL_GRASS, Material.LARGE_FERN,
            Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.PITCHER_PLANT, Material.TALL_SEAGRASS
    ));

    // Bloques solares para el suelo (mismo estilo que CaminoSoleado)
    private static final List<Material> BLOQUES_SOLAR = Arrays.asList(
            Material.SAND,
            Material.SANDSTONE,
            Material.SMOOTH_SANDSTONE,
            Material.CUT_SANDSTONE
    );

    public EstelarSolarListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
        startSpiralAndTailTask();
        startRestoreTask();
    }

    // ─── LANZAMIENTO ───────────────────────────────────────────────────────────

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        // Acepta Arrow y Spectral Arrow (multishot también son Arrow)
        if (!(proj instanceof Arrow)) return;
        if (!(proj.getShooter() instanceof Player player)) return;

        ItemStack weapon = getWeaponWithCosmetic(player);
        if (weapon == null) return;

        trackedArrows.add(proj.getUniqueId());
        lastTailParticle.put(proj.getUniqueId(), System.currentTimeMillis());
    }

    // ─── IMPACTO ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj instanceof Arrow)) return;

        UUID id = proj.getUniqueId();
        if (!trackedArrows.remove(id)) return;
        lastTailParticle.remove(id);

        Entity hitEntity = event.getHitEntity();
        Block hitBlock = event.getHitBlock();

        if (hitEntity instanceof LivingEntity living) {
            Location center = living.getLocation().add(0, 0, 0);
            applyBlocksAroundEntity(living, center);
            spawnImpactParticles(proj.getWorld(), center.clone().add(0, living.getHeight() / 2.0, 0));
        } else if (hitBlock != null) {
            spawnImpactParticles(proj.getWorld(), hitBlock.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    // ─── TAREA PRINCIPAL: espiral continua + partícula de cola cada 3s ─────────

    private void startSpiralAndTailTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (trackedArrows.isEmpty()) return;

                long now = System.currentTimeMillis();

                for (World world : plugin.getServer().getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (!(entity instanceof Arrow arrow)) continue;
                        UUID arrowId = arrow.getUniqueId();
                        if (!trackedArrows.contains(arrowId)) continue;

                        // Si la flecha ya tocó algo (isDead/onGround) limpiamos
                        if (arrow.isDead() || arrow.isOnGround()) {
                            trackedArrows.remove(arrowId);
                            lastTailParticle.remove(arrowId);
                            continue;
                        }

                        // Espiral de partículas alrededor del proyectil (continua)
                        spawnSpiralAroundArrow(arrow, now);

                        // Partícula de cola cada 3 segundos (dura 3 segundos)
                        long lastTail = lastTailParticle.getOrDefault(arrowId, now);
                        if (now - lastTail >= 3000L) {
                            lastTailParticle.put(arrowId, now);
                            spawnTailBurst(arrow);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Espiral de partículas END_ROD alrededor del proyectil, girando continuamente.
     * El ángulo se basa en el tiempo actual para que sea fluido y no dependa de ticks.
     */
    private void spawnSpiralAroundArrow(Arrow arrow, long now) {
        Location loc = arrow.getLocation();
        // La dirección del movimiento de la flecha nos sirve para orientar la espiral
        org.bukkit.util.Vector vel = arrow.getVelocity().clone().normalize();

        // Calculamos dos vectores perpendiculares al movimiento de la flecha
        org.bukkit.util.Vector perp1 = getPerp1(vel);
        org.bukkit.util.Vector perp2 = vel.clone().crossProduct(perp1).normalize();

        double rotation = (now / 40.0) % (2 * Math.PI); // velocidad de rotación suave
        int points = 8;
        double radius = 0.25;

        for (int i = 0; i < points; i++) {
            double angle = rotation + (i * (2 * Math.PI / points));
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(
                    perp1.clone().multiply(cos).add(perp2.clone().multiply(sin))
            );

            try {
                arrow.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Burst de partícula dorada (FLAME / LAVA) en la cola de la flecha, que dura 3 segundos.
     * Se genera al spawnearse y luego va desapareciendo sola (lifetime natural de la partícula).
     */
    private void spawnTailBurst(Arrow arrow) {
        Location loc = arrow.getLocation();

        // Burst inicial
        try {
            arrow.getWorld().spawnParticle(Particle.FLAME, loc, 6, 0.05, 0.05, 0.05, 0.01);
            arrow.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.08, 0.08, 0.08, 0.005);
        } catch (Exception ignored) {}

        // Las partículas de Minecraft duran ~40-60 ticks de forma natural;
        // para que duren exactamente 3s mantenemos un rastro ligero durante 60 ticks
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 60 || arrow.isDead() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                Location currentLoc = arrow.getLocation();
                try {
                    currentLoc.getWorld().spawnParticle(Particle.FLAME, currentLoc, 1, 0.03, 0.03, 0.03, 0.003);
                } catch (Exception ignored) {}
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ─── BLOQUES TEMPORALES AL IMPACTAR EN ENTIDAD ────────────────────────────

    /**
     * Cambia entre 4 y 6 bloques en un radio 3x3 alrededor del jugador impactado.
     * El bloque bajo el jugador (centro) ES OBLIGATORIO.
     * El resto se selecciona aleatoriamente hasta un total de 4-6.
     */
    private void applyBlocksAroundEntity(LivingEntity entity, Location groundLoc) {
        // Bloque justo debajo del jugador
        Location center = groundLoc.getBlock().getLocation().add(0, -1, 0);

        long now = System.currentTimeMillis();
        int totalTarget = 4 + RNG.nextInt(3); // 4, 5 o 6 bloques

        // Construimos la lista: primero el centro, luego los demás en orden aleatorio
        List<Location> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // centro lo añadimos primero
                candidates.add(center.clone().add(dx, 0, dz));
            }
        }
        Collections.shuffle(candidates, RNG);

        List<Location> toChange = new ArrayList<>();
        toChange.add(center); // CENTRO OBLIGATORIO
        int added = 1;
        for (Location cand : candidates) {
            if (added >= totalTarget) break;
            toChange.add(cand);
            added++;
        }

        for (Location loc : toChange) {
            Block under = loc.getBlock();
            if (originalBlock.containsKey(loc)) continue; // ya cambiado
            if (!canReplace(under)) continue;
            if (entity instanceof Player player && !WorldGuardHelper.canBuild(player, loc)) continue;

            Material replacement = BLOQUES_SOLAR.get(RNG.nextInt(BLOQUES_SOLAR.size()));
            BlockData newBd = Bukkit.createBlockData(replacement);
            originalBlock.put(loc, under.getBlockData());
            blockTimestamps.put(loc, now);

            // Manejar plantas altas encima del bloque
            Block above1 = under.getRelative(0, 1, 0);
            if (!above1.getType().isAir() && !above1.getType().isSolid()) {
                originalAbove.put(loc, above1.getBlockData());
                Block above2 = under.getRelative(0, 2, 0);
                if (TALL_TOP.contains(above2.getType())) {
                    originalAbove2.put(loc, above2.getBlockData());
                    above2.setType(Material.AIR, false);
                }
                above1.setType(Material.AIR, false);
            }

            under.setBlockData(newBd, false);

            // Partícula cool visual al cambiar el bloque
            Location pLoc = loc.clone().add(0.5, 1.0, 0.5);
            try {
                loc.getWorld().spawnParticle(Particle.BLOCK, pLoc, 6, 0.3, 0.05, 0.3, 0, newBd);
            } catch (Exception ignored) {}
        }
    }

    // ─── PARTÍCULAS DE IMPACTO GENÉRICO (según gogul) ─────────────────────────────────────

    private void spawnImpactParticles(World world, Location loc) {
        try {
            world.spawnParticle(Particle.END_ROD, loc, 12, 0.3, 0.3, 0.3, 0.05);
            world.spawnParticle(Particle.FLAME, loc, 8, 0.2, 0.2, 0.2, 0.03);
        } catch (Exception ignored) {}
    }

    // ─── TAREA DE RESTAURACIÓN DE BLOQUES ────────────────────────────────────

    private void startRestoreTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                List<Location> toRemove = new ArrayList<>();
                for (Map.Entry<Location, Long> entry : blockTimestamps.entrySet()) {
                    if (now - entry.getValue() >= 3000L) {
                        toRemove.add(entry.getKey());
                    }
                }
                for (Location loc : toRemove) {
                    restoreBlock(loc);
                    blockTimestamps.remove(loc);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void restoreBlock(Location loc) {
        BlockData above2 = originalAbove2.remove(loc);
        if (above2 != null) {
            try { loc.getBlock().getRelative(0, 2, 0).setBlockData(above2, false); } catch (Exception ignored) {}
        }
        BlockData above = originalAbove.remove(loc);
        if (above != null) {
            try { loc.getBlock().getRelative(0, 1, 0).setBlockData(above, false); } catch (Exception ignored) {}
        }
        BlockData bd = originalBlock.remove(loc);
        if (bd != null) {
            try { loc.getBlock().setBlockData(bd, false); } catch (Exception ignored) {}
        }
    }

    // ─── UTILIDADES GUAYS ───────────────────────────────────────────────────────────

    private boolean canReplace(Block block) {
        Material mat = block.getType();
        if (BLOQUEADOS.contains(mat)) return false;
        String name = mat.name();
        if (name.contains("PORTAL") || name.contains("GATEWAY")) return false;
        return mat.isSolid();
    }

    /**
     * Busca el arco o ballesta en mano del jugador que tenga el cosmetico aplicado.
     * Devuelve null si no lo tiene.
     */
    private ItemStack getWeaponWithCosmetic(Player player) {
        CosmeticData data = getEstelarSolarData();
        if (data == null) return null;

        // Mano principal (arco / ballesta)
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isBowOrCrossbow(main) && itemHasCosmetico(main, data)) return main;

        // Mano secundaria (por si acaso)
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isBowOrCrossbow(off) && itemHasCosmetico(off, data)) return off;

        return null;
    }

    private boolean isBowOrCrossbow(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return item.getType() == Material.BOW || item.getType() == Material.CROSSBOW;
    }

    private boolean itemHasCosmetico(ItemStack item, CosmeticData data) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "cosmetic_" + data.id);
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private CosmeticData getEstelarSolarData() {
        for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
            if (d.tipo.equalsIgnoreCase("ESTELA_SOLAR")) return d;
        }
        return null;
    }

    /**
     * Calcula un vector perpendicular a `v` para crear la base de la espiral, como cuando 67 se enamoró de tung.
     */
    private org.bukkit.util.Vector getPerp1(org.bukkit.util.Vector v) {
        org.bukkit.util.Vector arbitrary = Math.abs(v.getX()) < 0.9
                ? new org.bukkit.util.Vector(1, 0, 0)
                : new org.bukkit.util.Vector(0, 1, 0);
        return v.clone().crossProduct(arbitrary).normalize();
    }
}
