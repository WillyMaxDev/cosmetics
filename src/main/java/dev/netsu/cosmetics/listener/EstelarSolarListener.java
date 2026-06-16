package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.util.WorldGuardHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class EstelarSolarListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();

    private final Set<UUID> trackedArrows = new HashSet<>();
    private final Map<UUID, Long> lastTailParticle = new HashMap<>();

    private final Map<Location, BlockData> originalBlock = new HashMap<>();
    private final Map<Location, BlockData> originalAbove = new HashMap<>();
    private final Map<Location, BlockData> originalAbove2 = new HashMap<>();
    private final Map<Location, Long> blockTimestamps = new HashMap<>();
    private final Set<Location> protectedBlocks = new HashSet<>();

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

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj instanceof Arrow)) return;
        if (!(proj.getShooter() instanceof Player player)) return;

        ItemStack weapon = getWeaponWithCosmetic(player);
        if (weapon == null) return;

        trackedArrows.add(proj.getUniqueId());
        lastTailParticle.put(proj.getUniqueId(), System.currentTimeMillis());
    }

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
            applyBlocksAroundEntity(living);
            spawnImpactParticles(proj.getWorld(), living.getLocation().add(0, living.getHeight() / 2.0, 0));
        } else if (hitBlock != null) {
            spawnImpactParticles(proj.getWorld(), hitBlock.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (protectedBlocks.contains(loc)) {
            event.setCancelled(true);
        }
    }

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

                        if (arrow.isDead() || arrow.isOnGround()) {
                            trackedArrows.remove(arrowId);
                            lastTailParticle.remove(arrowId);
                            continue;
                        }

                        spawnSpiralAroundArrow(arrow, now);

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

    private void spawnSpiralAroundArrow(Arrow arrow, long now) {
        Location loc = arrow.getLocation();
        Vector vel = arrow.getVelocity();
        if (vel.lengthSquared() < 0.0001) return;
        vel = vel.clone().normalize();

        Vector perp1 = getPerp1(vel);
        Vector perp2 = vel.clone().crossProduct(perp1).normalize();

        double rotation = (now / 40.0) % (2 * Math.PI);
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

    private void spawnTailBurst(Arrow arrow) {
        Location loc = arrow.getLocation();
        try {
            arrow.getWorld().spawnParticle(Particle.FLAME, loc, 6, 0.05, 0.05, 0.05, 0.01);
            arrow.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.08, 0.08, 0.08, 0.005);
        } catch (Exception ignored) {}

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 60 || arrow.isDead() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                try {
                    arrow.getLocation().getWorld().spawnParticle(Particle.FLAME, arrow.getLocation(), 1, 0.03, 0.03, 0.03, 0.003);
                } catch (Exception ignored) {}
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyBlocksAroundEntity(LivingEntity entity) {
        Location entityLoc = entity.getLocation();
        double entityY = entityLoc.getY();

        Location surfaceCenter = findSurfaceBelow(entityLoc);
        if (surfaceCenter == null) return;

        double surfaceTopY = surfaceCenter.getY() + 1.0;
        double heightAboveSurface = entityY - surfaceTopY;

        if (heightAboveSurface > 2.0) return;

        long now = System.currentTimeMillis();
        int totalTarget = 6 + RNG.nextInt(5);

        List<Location> candidates = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                Location surfLoc = findSurfaceBelow(entityLoc.clone().add(dx, 0, dz));
                if (surfLoc != null) candidates.add(surfLoc);
            }
        }
        Collections.shuffle(candidates, RNG);

        List<Location> toChange = new ArrayList<>();
        toChange.add(surfaceCenter);
        int added = 1;
        for (Location cand : candidates) {
            if (added >= totalTarget) break;
            toChange.add(cand);
            added++;
        }

        for (Location loc : toChange) {
            Block under = loc.getBlock();
            if (originalBlock.containsKey(loc)) continue;
            if (!canReplace(under)) continue;
            if (entity instanceof Player player && !WorldGuardHelper.canBuild(player, loc)) continue;

            Material replacement = BLOQUES_SOLAR.get(RNG.nextInt(BLOQUES_SOLAR.size()));
            BlockData newBd = Bukkit.createBlockData(replacement);
            originalBlock.put(loc, under.getBlockData());
            blockTimestamps.put(loc, now);
            protectedBlocks.add(loc);

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

            Location pLoc = loc.clone().add(0.5, 1.0, 0.5);
            try {
                loc.getWorld().spawnParticle(Particle.BLOCK, pLoc, 6, 0.3, 0.05, 0.3, 0, newBd);
            } catch (Exception ignored) {}
        }
    }

    private Location findSurfaceBelow(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int startY = loc.getBlockY();

        for (int y = startY; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !BLOQUEADOS.contains(block.getType())) {
                return block.getLocation();
            }
        }
        return null;
    }

    private void spawnImpactParticles(World world, Location loc) {
        try {
            world.spawnParticle(Particle.END_ROD, loc, 12, 0.3, 0.3, 0.3, 0.05);
            world.spawnParticle(Particle.FLAME, loc, 8, 0.2, 0.2, 0.2, 0.03);
        } catch (Exception ignored) {}
    }

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
                    protectedBlocks.remove(loc);
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

    private boolean canReplace(Block block) {
        Material mat = block.getType();
        if (BLOQUEADOS.contains(mat)) return false;
        String name = mat.name();
        if (name.contains("PORTAL") || name.contains("GATEWAY")) return false;
        return mat.isSolid();
    }

    private ItemStack getWeaponWithCosmetic(Player player) {
        CosmeticData data = getEstelarSolarData();
        if (data == null) return null;

        ItemStack main = player.getInventory().getItemInMainHand();
        if (isBowOrCrossbow(main) && itemHasCosmetico(main, data)) return main;

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

    private Vector getPerp1(Vector v) {
        Vector arbitrary = Math.abs(v.getX()) < 0.9
                ? new Vector(1, 0, 0)
                : new Vector(0, 1, 0);
        return v.clone().crossProduct(arbitrary).normalize();
    }
}
