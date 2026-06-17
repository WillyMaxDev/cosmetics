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

    private static final double TWO_PI       = 2 * Math.PI;
    private static final double SPIRAL_SPEED = 1.0 / 40.0;
    private static final int    SPIRAL_POINTS = 8;
    private static final double SPIRAL_RADIUS = 0.25;
    private static final double SPIRAL_STEP   = TWO_PI / SPIRAL_POINTS;
    private static final long   TAIL_INTERVAL_MS  = 3000L;
    private static final long   BLOCK_RESTORE_MS  = 3000L;
    private static final int    BLOCKS_MIN   = 6;
    private static final int    BLOCKS_EXTRA = 5;

    private static final Material[] BLOQUES_SOLAR = {
        Material.SAND, Material.SANDSTONE,
        Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE
    };

    private static final Set<Material> BLOQUEADOS = EnumSet.of(
        Material.BEDROCK, Material.WATER, Material.LAVA,
        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
        Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY,
        Material.END_PORTAL_FRAME, Material.BARRIER
    );

    private static final Set<Material> TALL_TOP = EnumSet.of(
        Material.TALL_GRASS, Material.LARGE_FERN,
        Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
        Material.PITCHER_PLANT, Material.TALL_SEAGRASS
    );

    private static final class ArrowData {
        final Arrow arrow;
        long lastTailMs;
        ArrowData(Arrow arrow, long now) {
            this.arrow = arrow;
            this.lastTailMs = now;
        }
    }

    private final Map<UUID, ArrowData> trackedArrows = new HashMap<>();

    private final Map<Location, BlockData> originalBlock  = new HashMap<>();
    private final Map<Location, BlockData> originalAbove  = new HashMap<>();
    private final Map<Location, BlockData> originalAbove2 = new HashMap<>();
    private final Map<Location, Long>      blockTimestamps = new HashMap<>();
    private final Set<Location>            protectedBlocks = new HashSet<>();

    private CosmeticData cachedData = null;
    private final NamespacedKey cosmeticKey;

    private final Vector   reuseVel    = new Vector();
    private final Vector   reusePerp1  = new Vector();
    private final Vector   reusePerp2  = new Vector();
    private final Vector   reuseOffset = new Vector();
    private final Location reuseLoc    = new Location(null, 0, 0, 0);

    public EstelarSolarListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
        this.cosmeticKey = new NamespacedKey(plugin, "cosmetic_estela_solar");
        startSpiralTask();
        startRestoreTask();
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj instanceof Arrow arrow)) return;
        if (!(proj.getShooter() instanceof Player player)) return;
        if (!playerHasCosmetic(player)) return;
        trackedArrows.put(arrow.getUniqueId(), new ArrowData(arrow, System.currentTimeMillis()));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (!(proj instanceof Arrow)) return;
        ArrowData removed = trackedArrows.remove(proj.getUniqueId());
        if (removed == null) return;

        Entity hitEntity = event.getHitEntity();
        Block  hitBlock  = event.getHitBlock();

        if (hitEntity instanceof LivingEntity living) {
            applyBlocksAroundEntity(living);
            spawnImpactParticles(proj.getWorld(), living.getLocation().add(0, living.getHeight() / 2.0, 0));
        } else if (hitBlock != null) {
            spawnImpactParticles(proj.getWorld(), hitBlock.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (protectedBlocks.contains(event.getBlock().getLocation()))
            event.setCancelled(true);
    }

    private void startSpiralTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (trackedArrows.isEmpty()) return;

                long now = System.currentTimeMillis();
                double rotation = (now * SPIRAL_SPEED) % TWO_PI;

                Iterator<ArrowData> it = trackedArrows.values().iterator();
                while (it.hasNext()) {
                    ArrowData data = it.next();
                    Arrow arrow = data.arrow;

                    if (arrow.isDead() || arrow.isOnGround()) {
                        it.remove();
                        continue;
                    }

                    spawnSpiralAroundArrow(arrow, rotation);

                    if (now - data.lastTailMs >= TAIL_INTERVAL_MS) {
                        data.lastTailMs = now;
                        spawnTailBurst(arrow);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnSpiralAroundArrow(Arrow arrow, double rotation) {
        Vector vel = arrow.getVelocity();
        if (vel.lengthSquared() < 0.0001) return;
        reuseVel.setX(vel.getX()); reuseVel.setY(vel.getY()); reuseVel.setZ(vel.getZ());
        reuseVel.normalize();

        computePerp1(reuseVel, reusePerp1);
        reusePerp2.setX(reuseVel.getX()); reusePerp2.setY(reuseVel.getY()); reusePerp2.setZ(reuseVel.getZ());
        reusePerp2.crossProduct(reusePerp1).normalize();

        Location arrowLoc = arrow.getLocation();
        World world = arrowLoc.getWorld();

        for (int i = 0; i < SPIRAL_POINTS; i++) {
            double angle = rotation + (i * SPIRAL_STEP);
            double cos = Math.cos(angle) * SPIRAL_RADIUS;
            double sin = Math.sin(angle) * SPIRAL_RADIUS;

            reuseOffset.setX(reusePerp1.getX() * cos + reusePerp2.getX() * sin);
            reuseOffset.setY(reusePerp1.getY() * cos + reusePerp2.getY() * sin);
            reuseOffset.setZ(reusePerp1.getZ() * cos + reusePerp2.getZ() * sin);

            reuseLoc.setWorld(world);
            reuseLoc.setX(arrowLoc.getX() + reuseOffset.getX());
            reuseLoc.setY(arrowLoc.getY() + reuseOffset.getY());
            reuseLoc.setZ(arrowLoc.getZ() + reuseOffset.getZ());

            world.spawnParticle(Particle.END_ROD, reuseLoc, 1, 0, 0, 0, 0);
        }
    }

    private void spawnTailBurst(Arrow arrow) {
        Location loc = arrow.getLocation();
        World world = loc.getWorld();
        world.spawnParticle(Particle.FLAME,   loc, 6, 0.05, 0.05, 0.05, 0.01);
        world.spawnParticle(Particle.END_ROD, loc, 3, 0.08, 0.08, 0.08, 0.005);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 60 || arrow.isDead() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                Location l = arrow.getLocation();
                l.getWorld().spawnParticle(Particle.FLAME, l, 1, 0.03, 0.03, 0.03, 0.003);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyBlocksAroundEntity(LivingEntity entity) {
        Location entityLoc = entity.getLocation();
        Location surfaceCenter = findSurfaceBelowXZ(
            entityLoc.getWorld(), entityLoc.getBlockX(), entityLoc.getBlockZ(), entityLoc.getBlockY()
        );
        if (surfaceCenter == null) return;

        double heightAboveSurface = entityLoc.getY() - (surfaceCenter.getY() + 1.0);
        if (heightAboveSurface > 2.0) return;

        long now = System.currentTimeMillis();
        int totalTarget = BLOCKS_MIN + RNG.nextInt(BLOCKS_EXTRA);

        List<Location> candidates = new ArrayList<>(24);
        int ex = entityLoc.getBlockX();
        int ez = entityLoc.getBlockZ();
        int ey = entityLoc.getBlockY();
        World world = entityLoc.getWorld();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                Location surfLoc = findSurfaceBelowXZ(world, ex + dx, ez + dz, ey);
                if (surfLoc != null) candidates.add(surfLoc);
            }
        }
        Collections.shuffle(candidates, RNG);

        List<Location> toChange = new ArrayList<>(totalTarget);
        toChange.add(surfaceCenter);
        for (int i = 0; i < candidates.size() && toChange.size() < totalTarget; i++)
            toChange.add(candidates.get(i));

        for (Location loc : toChange) {
            Block under = loc.getBlock();
            if (originalBlock.containsKey(loc)) continue;
            if (!canReplace(under)) continue;
            if (entity instanceof Player player && !WorldGuardHelper.canBuild(player, loc)) continue;

            Material replacement = BLOQUES_SOLAR[RNG.nextInt(BLOQUES_SOLAR.length)];
            BlockData newBd = replacement.createBlockData();
            originalBlock.put(loc, under.getBlockData());
            blockTimestamps.put(loc, now);
            protectedBlocks.add(loc);

            Block above1 = under.getRelative(0, 1, 0);
            Material above1Mat = above1.getType();
            if (!above1Mat.isAir() && !above1Mat.isSolid()) {
                originalAbove.put(loc, above1.getBlockData());
                Block above2 = under.getRelative(0, 2, 0);
                if (TALL_TOP.contains(above2.getType())) {
                    originalAbove2.put(loc, above2.getBlockData());
                    above2.setType(Material.AIR, false);
                }
                above1.setType(Material.AIR, false);
            }

            under.setBlockData(newBd, false);
            world.spawnParticle(Particle.BLOCK,
                loc.getX() + 0.5, loc.getY() + 1.0, loc.getZ() + 0.5,
                6, 0.3, 0.05, 0.3, 0, newBd);
        }
    }

    private Location findSurfaceBelowXZ(World world, int x, int z, int startY) {
        if (world == null) return null;
        int minY = world.getMinHeight();
        for (int y = startY; y >= minY; y--) {
            Material mat = world.getBlockAt(x, y, z).getType();
            if (mat.isSolid() && !BLOQUEADOS.contains(mat))
                return new Location(world, x, y, z);
        }
        return null;
    }

    private void spawnImpactParticles(World world, Location loc) {
        world.spawnParticle(Particle.END_ROD, loc, 12, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.FLAME,   loc,  8, 0.2, 0.2, 0.2, 0.03);
    }

    private void startRestoreTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (blockTimestamps.isEmpty()) return;
                long now = System.currentTimeMillis();
                blockTimestamps.entrySet().removeIf(entry -> {
                    if (now - entry.getValue() >= BLOCK_RESTORE_MS) {
                        Location loc = entry.getKey();
                        restoreBlock(loc);
                        protectedBlocks.remove(loc);
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void restoreBlock(Location loc) {
        BlockData above2 = originalAbove2.remove(loc);
        if (above2 != null) loc.getBlock().getRelative(0, 2, 0).setBlockData(above2, false);
        BlockData above = originalAbove.remove(loc);
        if (above != null) loc.getBlock().getRelative(0, 1, 0).setBlockData(above, false);
        BlockData bd = originalBlock.remove(loc);
        if (bd != null) loc.getBlock().setBlockData(bd, false);
    }

    private boolean canReplace(Block block) {
        Material mat = block.getType();
        if (!mat.isSolid() || BLOQUEADOS.contains(mat)) return false;
        String name = mat.name();
        return !name.contains("PORTAL") && !name.contains("GATEWAY");
    }

    private boolean playerHasCosmetic(Player player) {
        if (getCachedData() == null) return false;
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isBowOrCrossbow(main) && itemHasCosmetico(main)) return true;
        ItemStack off = player.getInventory().getItemInOffHand();
        return isBowOrCrossbow(off) && itemHasCosmetico(off);
    }

    private boolean isBowOrCrossbow(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        Material m = item.getType();
        return m == Material.BOW || m == Material.CROSSBOW;
    }

    private boolean itemHasCosmetico(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(cosmeticKey, PersistentDataType.BYTE);
    }

    private CosmeticData getCachedData() {
        if (cachedData == null) {
            for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
                if (d.tipo.equalsIgnoreCase("ESTELA_SOLAR")) {
                    cachedData = d;
                    break;
                }
            }
        }
        return cachedData;
    }

    private void computePerp1(Vector v, Vector out) {
        if (Math.abs(v.getX()) < 0.9) {
            out.setX(0); out.setY(-v.getZ()); out.setZ(v.getY());
        } else {
            out.setX(v.getY()); out.setY(-v.getX()); out.setZ(0);
        }
        out.normalize();
    }
}
