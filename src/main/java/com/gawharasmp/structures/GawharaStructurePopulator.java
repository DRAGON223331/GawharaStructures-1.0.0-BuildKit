package com.gawharasmp.structures;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Paper's modern, thread-safe chunk populator. It only edits the supplied LimitedRegion
 * and never calls Bukkit's live World API from the generation thread.
 */
final class GawharaStructurePopulator extends BlockPopulator {

    record LootEntry(Material material, int weight, int min, int max) {}
    record StructureSpec(String id, String lootTable) {}
    record GeneratedRecord(String world, int x, int y, int z, String type, long created) {}
    record Profile(String worldName, String theme, double chance, double spawnX, double spawnZ,
                   double minSpawnDistance, double minStructureDistance, boolean requireFlat,
                   int maxGroundVariation, int minItems, int maxItems,
                   List<StructureSpec> structures, Map<String, List<LootEntry>> lootTables) {}

    interface ReservationIndex {
        boolean reserve(String world, int x, int z, double minimumDistance);
    }

    private final Profile profile;
    private final ConcurrentLinkedQueue<GeneratedRecord> generatedRecords;
    private final ReservationIndex reservationIndex;

    GawharaStructurePopulator(Profile profile,
                              ConcurrentLinkedQueue<GeneratedRecord> generatedRecords,
                              ReservationIndex reservationIndex) {
        this.profile = profile;
        this.generatedRecords = generatedRecords;
        this.reservationIndex = reservationIndex;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (!profile.worldName().equals(worldInfo.getName())) return;
        if (random.nextDouble() >= profile.chance()) return;
        if (profile.structures().isEmpty()) return;

        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        double dx = centerX - profile.spawnX();
        double dz = centerZ - profile.spawnZ();
        if (dx * dx + dz * dz < profile.minSpawnDistance() * profile.minSpawnDistance()) return;

        int floorY = findSurfaceY(worldInfo, region, centerX, centerZ, -1);
        if (floorY == Integer.MIN_VALUE) return;
        if (profile.requireFlat() && !"nether".equalsIgnoreCase(profile.theme())
                && !isFlat(region, centerX, floorY - 1, centerZ, profile.maxGroundVariation())) return;

        StructureSpec spec = profile.structures().get(random.nextInt(profile.structures().size()));
        if (!reservationIndex.reserve(profile.worldName(), centerX, centerZ, profile.minStructureDistance())) return;

        boolean built = buildStructure(region, worldInfo, centerX, floorY, centerZ, spec, random, chunkX, chunkZ);
        if (built) {
            generatedRecords.add(new GeneratedRecord(profile.worldName(), centerX, floorY, centerZ,
                    spec.id(), System.currentTimeMillis()));
        }
    }

    private int findSurfaceY(WorldInfo info, LimitedRegion region, int x, int z, int hintY) {
        int min = info.getMinHeight() + 5;
        int max = info.getMaxHeight() - 8;
        int start;
        int end;
        if ("nether".equalsIgnoreCase(profile.theme())) {
            start = hintY > 0 ? Math.min(hintY - 1, max) : Math.min(90, max);
            end = min;
        } else {
            start = Math.min(region.getHighestBlockYAt(x, z), max);
            end = Math.max(min, start - 24);
        }
        for (int y = start; y >= end; y--) {
            Material ground = region.getType(x, y, z);
            if (!ground.isSolid() || ground.isAir() || ground == Material.BEDROCK
                    || ground == Material.MAGMA_BLOCK || ground == Material.CACTUS
                    || ground.name().endsWith("LEAVES") || ground.name().contains("LOG")
                    || ground == Material.WATER || ground == Material.LAVA) continue;
            boolean clear = true;
            for (int dy = 1; dy <= 4; dy++) {
                if (!region.getType(x, y + dy, z).isAir()) {
                    clear = false;
                    break;
                }
            }
            if (clear) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    private boolean isFlat(LimitedRegion region, int centerGroundY, int x, int z, int maxVariation) {
        int minY = centerGroundY;
        int maxY = centerGroundY;
        for (int dx : new int[]{-3, 0, 3}) {
            for (int dz : new int[]{-3, 0, 3}) {
                int y = region.getHighestBlockYAt(x + dx, z + dz);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        return maxY - minY <= maxVariation;
    }

    private boolean buildStructure(LimitedRegion region, WorldInfo info, int x, int y, int z,
                                   StructureSpec spec, Random random, int chunkX, int chunkZ) {
        switch (spec.id()) {
            case "forest_shrine" -> {
                platform(region, x, y, z, 3, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS, chunkX, chunkZ);
                for (int ox : new int[]{-3, 3}) for (int oz : new int[]{-3, 3}) {
                    column(region, x + ox, y + 1, z + oz, 3,
                            random.nextBoolean() ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS, chunkX, chunkZ);
                    put(region, x + ox, y + 4, z + oz, Material.LANTERN, chunkX, chunkZ);
                }
                put(region, x - 1, y + 1, z, Material.MOSSY_STONE_BRICK_WALL, chunkX, chunkZ);
                put(region, x + 1, y + 1, z, Material.MOSSY_STONE_BRICK_WALL, chunkX, chunkZ);
                put(region, x, y + 1, z - 1, Material.MOSSY_STONE_BRICK_WALL, chunkX, chunkZ);
                treasure(region, x, y + 1, z + 1, spec.lootTable(), random, chunkX, chunkZ);
            }
            case "ruined_watchtower" -> {
                platform(region, x, y, z, 2, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, chunkX, chunkZ);
                Material log = random.nextBoolean() ? Material.SPRUCE_LOG : Material.DARK_OAK_LOG;
                for (int ox : new int[]{-2, 2}) for (int oz : new int[]{-2, 2})
                    column(region, x + ox, y + 1, z + oz, 5, log, chunkX, chunkZ);
                for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++)
                    if (Math.abs(ox) == 2 || Math.abs(oz) == 2)
                        put(region, x + ox, y + 5, z + oz, Material.SPRUCE_PLANKS, chunkX, chunkZ);
                put(region, x, y + 5, z, Material.CAMPFIRE, chunkX, chunkZ);
                treasure(region, x + 1, y + 1, z + 1, spec.lootTable(), random, chunkX, chunkZ);
            }
            case "buried_ruins" -> {
                platform(region, x, y, z, 3, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, chunkX, chunkZ);
                for (int ox = -2; ox <= 2; ox++) if (Math.abs(ox) == 2) {
                    put(region, x + ox, y + 1, z - 2, Material.STONE_BRICK_WALL, chunkX, chunkZ);
                    put(region, x + ox, y + 1, z + 2, Material.STONE_BRICK_WALL, chunkX, chunkZ);
                }
                put(region, x - 2, y + 1, z, Material.MOSSY_STONE_BRICKS, chunkX, chunkZ);
                put(region, x + 2, y + 1, z, Material.CRACKED_STONE_BRICKS, chunkX, chunkZ);
                treasure(region, x, y + 1, z, spec.lootTable(), random, chunkX, chunkZ);
            }
            case "soul_vault" -> {
                platform(region, x, y, z, 3, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, chunkX, chunkZ);
                for (int ox : new int[]{-3, 3}) for (int oz : new int[]{-3, 3}) {
                    column(region, x + ox, y + 1, z + oz, 3, Material.POLISHED_BASALT, chunkX, chunkZ);
                    put(region, x + ox, y + 4, z + oz, Material.SOUL_LANTERN, chunkX, chunkZ);
                }
                put(region, x - 2, y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL, chunkX, chunkZ);
                put(region, x + 2, y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL, chunkX, chunkZ);
                treasure(region, x, y + 1, z, spec.lootTable(), random, chunkX, chunkZ);
            }
            case "magma_cache" -> {
                platform(region, x, y, z, 2, Material.BASALT, Material.BLACKSTONE, chunkX, chunkZ);
                for (int ox : new int[]{-2, 2}) for (int oz : new int[]{-2, 2}) {
                    column(region, x + ox, y + 1, z + oz, 2, Material.NETHER_BRICKS, chunkX, chunkZ);
                    put(region, x + ox, y + 3, z + oz, Material.MAGMA_BLOCK, chunkX, chunkZ);
                }
                put(region, x - 1, y + 1, z - 1, Material.CHAIN, chunkX, chunkZ);
                put(region, x + 1, y + 1, z + 1, Material.CHAIN, chunkX, chunkZ);
                treasure(region, x, y + 1, z, spec.lootTable(), random, chunkX, chunkZ);
            }
            case "void_shrine" -> {
                platform(region, x, y, z, 3, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, chunkX, chunkZ);
                for (int ox : new int[]{-3, 3}) for (int oz : new int[]{-3, 3}) {
                    column(region, x + ox, y + 1, z + oz, 3, Material.PURPUR_PILLAR, chunkX, chunkZ);
                    put(region, x + ox, y + 4, z + oz, Material.END_ROD, chunkX, chunkZ);
                }
                put(region, x - 1, y + 1, z, Material.OBSIDIAN, chunkX, chunkZ);
                put(region, x + 1, y + 1, z, Material.OBSIDIAN, chunkX, chunkZ);
                treasure(region, x, y + 1, z + 1, spec.lootTable(), random, chunkX, chunkZ);
            }
            case "ender_observatory" -> {
                platform(region, x, y, z, 2, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, chunkX, chunkZ);
                column(region, x, y + 1, z, 4, Material.PURPUR_PILLAR, chunkX, chunkZ);
                for (int ox : new int[]{-2, 2}) for (int oz : new int[]{-2, 2}) {
                    put(region, x + ox, y + 1, z + oz, Material.OBSIDIAN, chunkX, chunkZ);
                    put(region, x + ox, y + 2, z + oz, Material.END_ROD, chunkX, chunkZ);
                }
                treasure(region, x, y + 1, z + 1, spec.lootTable(), random, chunkX, chunkZ);
            }
            default -> { return false; }
        }
        return true;
    }

    private void platform(LimitedRegion region, int x, int y, int z, int radius,
                          Material border, Material inside, int chunkX, int chunkZ) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            Material material = Math.abs(dx) == radius || Math.abs(dz) == radius ? border : inside;
            put(region, x + dx, y, z + dz, material, chunkX, chunkZ);
        }
    }

    private void column(LimitedRegion region, int x, int y, int z, int height, Material material, int cx, int cz) {
        for (int dy = 0; dy < height; dy++) put(region, x, y + dy, z, material, cx, cz);
    }

    private void put(LimitedRegion region, int x, int y, int z, Material material, int chunkX, int chunkZ) {
        if ((x >> 4) != chunkX || (z >> 4) != chunkZ) return;
        if (!region.isInRegion(x, y, z)) return;
        region.setType(x, y, z, material);
    }

    private void treasure(LimitedRegion region, int x, int y, int z, String table,
                          Random random, int chunkX, int chunkZ) {
        if ((x >> 4) != chunkX || (z >> 4) != chunkZ || !region.isInRegion(x, y, z)) return;
        region.setType(x, y, z, Material.CHEST);
        BlockState state = region.getBlockState(x, y, z);
        if (!(state instanceof Chest chest)) return;
        Inventory inventory = chest.getBlockInventory();
        List<LootEntry> entries = profile.lootTables().getOrDefault(table, List.of());
        if (entries.isEmpty()) {
            region.setBlockState(x, y, z, state);
            return;
        }
        int count = profile.minItems() + random.nextInt(Math.max(1, profile.maxItems() - profile.minItems() + 1));
        for (int i = 0; i < count; i++) {
            LootEntry entry = weighted(entries, random);
            if (entry == null) continue;
            int amount = entry.min() + random.nextInt(Math.max(1, entry.max() - entry.min() + 1));
            ItemStack item = new ItemStack(entry.material(), amount);
            int slot = random.nextInt(inventory.getSize());
            if (inventory.getItem(slot) == null) inventory.setItem(slot, item);
            else inventory.addItem(item);
        }
        region.setBlockState(x, y, z, state);
    }

    private LootEntry weighted(List<LootEntry> entries, Random random) {
        int total = entries.stream().mapToInt(e -> Math.max(0, e.weight())).sum();
        if (total <= 0) return entries.get(random.nextInt(entries.size()));
        int selected = random.nextInt(total);
        for (LootEntry entry : entries) {
            selected -= Math.max(0, entry.weight());
            if (selected < 0) return entry;
        }
        return entries.get(entries.size() - 1);
    }
}
