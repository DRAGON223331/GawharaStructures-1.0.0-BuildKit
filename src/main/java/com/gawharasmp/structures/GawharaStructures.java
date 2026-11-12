package com.gawharasmp.structures;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class GawharaStructures extends JavaPlugin implements Listener, TabCompleter {

    private static final String PREFIX = "&8[&bGawharaStructures&8] &r";
    private final Random random = new Random();
    private final List<Map<String, Object>> records = new ArrayList<>();
    private final List<ReservedPosition> reservedPositions = new ArrayList<>();
    private final ConcurrentLinkedQueue<GawharaStructurePopulator.GeneratedRecord> pendingRecords = new ConcurrentLinkedQueue<>();
    private File structuresFile;

    private record ReservedPosition(String world, int x, int z) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadStructureRecords();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("gstructures") != null) {
            getCommand("gstructures").setExecutor(this::onCommand);
            getCommand("gstructures").setTabCompleter(this);
        }
        for (World world : Bukkit.getWorlds()) registerWorldPopulator(world);
        Bukkit.getScheduler().runTaskTimer(this, this::flushPendingRecords, 100L, 100L);
        getLogger().info("GawharaStructures enabled. Active worlds: world, world_nether, world_the_end. The spawn world is denied.");
    }

    @Override
    public void onDisable() {
        flushPendingRecords();
        saveStructureRecords();
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        registerWorldPopulator(event.getWorld());
    }

    private void registerWorldPopulator(World world) {
        // Remove an older instance on reload to prevent duplicate generation attempts.
        world.getPopulators().removeIf(populator -> populator instanceof GawharaStructurePopulator);
        GawharaStructurePopulator.Profile profile = createProfile(world);
        if (profile == null) return;
        world.getPopulators().add(new GawharaStructurePopulator(profile, pendingRecords, this::reserveStructure));
    }

    private GawharaStructurePopulator.Profile createProfile(World world) {
        if (!getConfig().getBoolean("generation.enabled", true) || !isWorldAllowed(world)) return null;
        String path = "worlds." + world.getName();
        String theme = getConfig().getString(path + ".theme", "overworld");
        List<GawharaStructurePopulator.StructureSpec> specs = new ArrayList<>();
        for (String id : enabledPool(world)) {
            String lootTable = getConfig().getString("structures." + id + ".loot-table", defaultLootTable(id));
            specs.add(new GawharaStructurePopulator.StructureSpec(id, lootTable));
        }
        if (specs.isEmpty()) return null;

        Map<String, List<GawharaStructurePopulator.LootEntry>> lootTables = new HashMap<>();
        ConfigurationSection lootSection = getConfig().getConfigurationSection("loot-tables");
        if (lootSection != null) {
            for (String table : lootSection.getKeys(false)) {
                List<GawharaStructurePopulator.LootEntry> entries = new ArrayList<>();
                for (Map<?, ?> entry : getConfig().getMapList("loot-tables." + table)) {
                    Object rawMaterial = entry.get("material");
                    if (rawMaterial == null) continue;
                    Material material = Material.matchMaterial(String.valueOf(rawMaterial));
                    if (material == null || material.isAir()) continue;
                    entries.add(new GawharaStructurePopulator.LootEntry(material,
                            Math.max(0, parseInt(entry.get("weight"), 1)),
                            Math.max(1, parseInt(entry.get("min"), 1)),
                            Math.max(1, parseInt(entry.get("max"), 1))));
                }
                lootTables.put(table, List.copyOf(entries));
            }
        }
        Location spawn = world.getSpawnLocation();
        return new GawharaStructurePopulator.Profile(
                world.getName(), theme,
                Math.max(0.0, Math.min(1.0, getConfig().getDouble(path + ".chance-per-chunk", getConfig().getDouble("generation.chance-per-chunk", 0.025)))),
                spawn.getX(), spawn.getZ(),
                getConfig().getDouble("generation.min-distance-from-spawn." + world.getName(), 350.0),
                getConfig().getDouble("generation.min-distance-between-structures", 180.0),
                getConfig().getBoolean("generation.require-flat-ground", true),
                getConfig().getInt("generation.max-ground-variation", 3),
                Math.max(1, getConfig().getInt("loot.items-per-chest.min", 3)),
                Math.max(1, getConfig().getInt("loot.items-per-chest.max", 5)),
                List.copyOf(specs), Map.copyOf(lootTables));
    }

    private String defaultLootTable(String id) {
        return switch (id) {
            case "forest_shrine", "buried_ruins" -> "overworld_rare";
            case "ruined_watchtower" -> "overworld_common";
            case "soul_vault" -> "nether_rare";
            case "magma_cache" -> "nether_common";
            case "void_shrine" -> "end_rare";
            case "ender_observatory" -> "end_common";
            default -> "overworld_common";
        };
    }

    private synchronized boolean reserveStructure(String world, int x, int z, double minimumDistance) {
        double minimumSquared = minimumDistance * minimumDistance;
        for (ReservedPosition position : reservedPositions) {
            if (!position.world().equals(world)) continue;
            long dx = (long) x - position.x();
            long dz = (long) z - position.z();
            if ((double) dx * dx + (double) dz * dz < minimumSquared) return false;
        }
        reservedPositions.add(new ReservedPosition(world, x, z));
        return true;
    }

    private synchronized void trimReservedPositions(int maximum) {
        while (reservedPositions.size() > maximum) reservedPositions.remove(0);
    }

    private void flushPendingRecords() {
        GawharaStructurePopulator.GeneratedRecord record;
        boolean changed = false;
        while ((record = pendingRecords.poll()) != null) {
            Map<String, Object> map = new HashMap<>();
            map.put("world", record.world());
            map.put("x", record.x());
            map.put("y", record.y());
            map.put("z", record.z());
            map.put("type", record.type());
            map.put("created", record.created());
            records.add(map);
            changed = true;
        }
        int max = Math.max(100, getConfig().getInt("storage.max-records", 10000));
        while (records.size() > max) records.remove(0);
        trimReservedPositions(max);
        if (changed) saveStructureRecords();
    }

    private boolean isWorldAllowed(World world) {
        String worldName = world.getName();
        // Hard whitelist requested for GawharaSMP: only these three named worlds can generate.
        if (!Set.of("world", "world_nether", "world_the_end").contains(worldName.toLowerCase(Locale.ROOT))) return false;
        if (worldName.equalsIgnoreCase("spawn")) return false;
        List<String> blacklist = getConfig().getStringList("generation.blacklisted-worlds");
        for (String name : blacklist) {
            if (name.equalsIgnoreCase(worldName)) return false;
        }
        ConfigurationSection section = getConfig().getConfigurationSection("worlds." + worldName);
        return section != null && section.getBoolean("enabled", false);
    }

    private List<String> enabledPool(World world) {
        List<String> configured = getConfig().getStringList("worlds." + world.getName() + ".structures");
        return configured.stream()
                .filter(id -> getConfig().getBoolean("structures." + id + ".enabled", true))
                .filter(this::isKnownStructure)
                .collect(Collectors.toList());
    }

    private boolean isKnownStructure(String id) {
        return Set.of("forest_shrine", "ruined_watchtower", "buried_ruins", "soul_vault",
                "magma_cache", "void_shrine", "ender_observatory").contains(id.toLowerCase(Locale.ROOT));
    }

    private boolean isStructureAllowedForWorld(World world, String id) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "world" -> Set.of("forest_shrine", "ruined_watchtower", "buried_ruins").contains(id);
            case "world_nether" -> Set.of("soul_vault", "magma_cache").contains(id);
            case "world_the_end" -> Set.of("void_shrine", "ender_observatory").contains(id);
            default -> false;
        };
    }

    private boolean isNearWorldSpawn(World world, int x, int z) {
        double radius = getConfig().getDouble("generation.min-distance-from-spawn." + world.getName(), 350.0);
        Location spawn = world.getSpawnLocation();
        double dx = x - spawn.getX();
        double dz = z - spawn.getZ();
        return (dx * dx + dz * dz) < radius * radius;
    }

    private boolean isNearExistingStructure(World world, int x, int z) {
        double minDistance = getConfig().getDouble("generation.min-distance-between-structures", 180.0);
        double minDistanceSquared = minDistance * minDistance;
        for (Map<String, Object> record : records) {
            if (!world.getName().equals(String.valueOf(record.get("world")))) continue;
            int rx = number(record.get("x"));
            int rz = number(record.get("z"));
            double dx = x - rx;
            double dz = z - rz;
            if (dx * dx + dz * dz < minDistanceSquared) return true;
        }
        return false;
    }

    private int findSurfaceY(World world, int x, int z, String theme, int hintY) {
        int min = world.getMinHeight() + 5;
        int max = world.getMaxHeight() - 8;
        int start;
        int end;
        if ("nether".equalsIgnoreCase(theme)) {
            start = hintY > 0 ? Math.min(hintY - 1, max) : Math.min(90, max);
            end = min;
        } else {
            start = Math.min(world.getHighestBlockYAt(x, z), max);
            end = Math.max(min, start - 24);
        }

        for (int y = start; y >= end; y--) {
            Material ground = world.getBlockAt(x, y, z).getType();
            if (!ground.isSolid() || ground.isAir() || ground == Material.BEDROCK
                    || ground == Material.MAGMA_BLOCK || ground == Material.CACTUS
                    || ground.name().endsWith("LEAVES") || ground.name().contains("LOG")
                    || ground == Material.WATER || ground == Material.LAVA) continue;
            boolean clear = true;
            for (int dy = 1; dy <= 4; dy++) {
                if (!world.getBlockAt(x, y + dy, z).getType().isAir()) {
                    clear = false;
                    break;
                }
            }
            if (clear) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    private boolean isFlatEnough(World world, int centerGroundY, int x, int z) {
        int maxVariation = getConfig().getInt("generation.max-ground-variation", 3);
        int minY = centerGroundY;
        int maxY = centerGroundY;
        for (int dx : new int[]{-3, 0, 3}) {
            for (int dz : new int[]{-3, 0, 3}) {
                int y = world.getHighestBlockYAt(x + dx, z + dz);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        return maxY - minY <= maxVariation;
    }

    private void placeStructure(World world, int centerX, int floorY, int centerZ,
                                String id, int chunkX, int chunkZ) {
        String normalized = id.toLowerCase(Locale.ROOT);
        Random structureRandom = new Random(random.nextLong());
        switch (normalized) {
            case "forest_shrine" -> buildForestShrine(world, centerX, floorY, centerZ, structureRandom, chunkX, chunkZ);
            case "ruined_watchtower" -> buildWatchtower(world, centerX, floorY, centerZ, structureRandom, chunkX, chunkZ);
            case "buried_ruins" -> buildBuriedRuins(world, centerX, floorY, centerZ, structureRandom, chunkX, chunkZ);
            case "soul_vault" -> buildSoulVault(world, centerX, floorY, centerZ, structureRandom, chunkX, chunkZ);
            case "magma_cache" -> buildMagmaCache(world, centerX, floorY, centerZ, structureRandom, chunkX, chunkZ);
            case "void_shrine" -> buildVoidShrine(world, centerX, floorY, centerZ, structureRandom, chunkX, chunkZ);
            case "ender_observatory" -> buildEnderObservatory(world, centerX, floorY, centerZ, structureRandom, chunkX, chunkZ);
            default -> {
                return;
            }
        }
        addRecord(world, centerX, floorY, centerZ, normalized);
        getLogger().info("Generated " + normalized + " in " + world.getName() + " at " + centerX + ", " + floorY + ", " + centerZ);
    }

    private void buildForestShrine(World w, int x, int y, int z, Random r, int cx, int cz) {
        platform(w, x, y, z, 3, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS, cx, cz);
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3}) {
            column(w, x + dx, y + 1, z + dz, 3, pick(r, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS), cx, cz);
            put(w, x + dx, y + 4, z + dz, Material.LANTERN, cx, cz);
        }
        put(w, x - 1, y + 1, z, Material.MOSSY_STONE_BRICK_WALL, cx, cz);
        put(w, x + 1, y + 1, z, Material.MOSSY_STONE_BRICK_WALL, cx, cz);
        put(w, x, y + 1, z - 1, Material.MOSSY_STONE_BRICK_WALL, cx, cz);
        placeTreasureChest(w, x, y + 1, z + 1, "overworld_rare", r, cx, cz);
    }

    private void buildWatchtower(World w, int x, int y, int z, Random r, int cx, int cz) {
        platform(w, x, y, z, 2, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, cx, cz);
        Material log = pick(r, Material.SPRUCE_LOG, Material.DARK_OAK_LOG);
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) column(w, x + dx, y + 1, z + dz, 5, log, cx, cz);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2) put(w, x + dx, y + 5, z + dz, Material.SPRUCE_PLANKS, cx, cz);
        }
        put(w, x, y + 5, z, Material.CAMPFIRE, cx, cz);
        placeTreasureChest(w, x + 1, y + 1, z + 1, "overworld_common", r, cx, cz);
    }

    private void buildBuriedRuins(World w, int x, int y, int z, Random r, int cx, int cz) {
        platform(w, x, y, z, 3, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, cx, cz);
        for (int dx = -2; dx <= 2; dx++) {
            if (Math.abs(dx) == 2) {
                put(w, x + dx, y + 1, z - 2, Material.STONE_BRICK_WALL, cx, cz);
                put(w, x + dx, y + 1, z + 2, Material.STONE_BRICK_WALL, cx, cz);
            }
        }
        put(w, x - 2, y + 1, z, Material.MOSSY_STONE_BRICKS, cx, cz);
        put(w, x + 2, y + 1, z, Material.CRACKED_STONE_BRICKS, cx, cz);
        placeTreasureChest(w, x, y + 1, z, "overworld_rare", r, cx, cz);
    }

    private void buildSoulVault(World w, int x, int y, int z, Random r, int cx, int cz) {
        platform(w, x, y, z, 3, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, cx, cz);
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3}) {
            column(w, x + dx, y + 1, z + dz, 3, Material.POLISHED_BASALT, cx, cz);
            put(w, x + dx, y + 4, z + dz, Material.SOUL_LANTERN, cx, cz);
        }
        put(w, x - 2, y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL, cx, cz);
        put(w, x + 2, y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL, cx, cz);
        placeTreasureChest(w, x, y + 1, z, "nether_rare", r, cx, cz);
    }

    private void buildMagmaCache(World w, int x, int y, int z, Random r, int cx, int cz) {
        platform(w, x, y, z, 2, Material.BASALT, Material.BLACKSTONE, cx, cz);
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) {
            column(w, x + dx, y + 1, z + dz, 2, Material.NETHER_BRICKS, cx, cz);
            put(w, x + dx, y + 3, z + dz, Material.MAGMA_BLOCK, cx, cz);
        }
        put(w, x - 1, y + 1, z - 1, Material.IRON_CHAIN, cx, cz);
        put(w, x + 1, y + 1, z + 1, Material.IRON_CHAIN, cx, cz);
        placeTreasureChest(w, x, y + 1, z, "nether_common", r, cx, cz);
    }

    private void buildVoidShrine(World w, int x, int y, int z, Random r, int cx, int cz) {
        platform(w, x, y, z, 3, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, cx, cz);
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3}) {
            column(w, x + dx, y + 1, z + dz, 3, Material.PURPUR_PILLAR, cx, cz);
            put(w, x + dx, y + 4, z + dz, Material.END_ROD, cx, cz);
        }
        put(w, x - 1, y + 1, z, Material.OBSIDIAN, cx, cz);
        put(w, x + 1, y + 1, z, Material.OBSIDIAN, cx, cz);
        placeTreasureChest(w, x, y + 1, z + 1, "end_rare", r, cx, cz);
    }

    private void buildEnderObservatory(World w, int x, int y, int z, Random r, int cx, int cz) {
        platform(w, x, y, z, 2, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS, cx, cz);
        column(w, x, y + 1, z, 4, Material.PURPUR_PILLAR, cx, cz);
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) {
            put(w, x + dx, y + 1, z + dz, Material.OBSIDIAN, cx, cz);
            put(w, x + dx, y + 2, z + dz, Material.END_ROD, cx, cz);
        }
        placeTreasureChest(w, x, y + 1, z + 1, "end_common", r, cx, cz);
    }

    private void platform(World w, int x, int y, int z, int radius, Material border,
                          Material inside, int cx, int cz) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Material material = Math.abs(dx) == radius || Math.abs(dz) == radius ? border : inside;
                put(w, x + dx, y, z + dz, material, cx, cz);
            }
        }
    }

    private void column(World w, int x, int y, int z, int height, Material material, int cx, int cz) {
        for (int dy = 0; dy < height; dy++) put(w, x, y + dy, z, material, cx, cz);
    }

    private void put(World w, int x, int y, int z, Material material, int chunkX, int chunkZ) {
        if ((x >> 4) != chunkX || (z >> 4) != chunkZ) return;
        if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 1) return;
        w.getBlockAt(x, y, z).setType(material, false);
    }

    private Material pick(Random r, Material a, Material b) {
        return r.nextBoolean() ? a : b;
    }

    private void placeTreasureChest(World world, int x, int y, int z, String lootTable,
                                    Random r, int chunkX, int chunkZ) {
        if ((x >> 4) != chunkX || (z >> 4) != chunkZ) return;
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return;
        Inventory inventory = chest.getBlockInventory();
        List<Map<?, ?>> entries = getConfig().getMapList("loot-tables." + lootTable);
        if (entries.isEmpty()) return;

        int minItems = getConfig().getInt("loot.items-per-chest.min", 3);
        int maxItems = Math.max(minItems, getConfig().getInt("loot.items-per-chest.max", 5));
        int amount = minItems + r.nextInt(Math.max(1, maxItems - minItems + 1));
        for (int i = 0; i < amount; i++) {
            Map<?, ?> entry = weightedEntry(entries, r);
            if (entry == null) continue;
            Object rawMaterial = entry.get("material");
            if (rawMaterial == null) continue;
            Material material = Material.matchMaterial(String.valueOf(rawMaterial));
            if (material == null || material.isAir()) continue;
            int min = Math.max(1, parseInt(entry.get("min"), 1));
            int max = Math.max(min, parseInt(entry.get("max"), min));
            ItemStack item = new ItemStack(material, min + r.nextInt(max - min + 1));
            int slot = r.nextInt(inventory.getSize());
            if (inventory.getItem(slot) == null) inventory.setItem(slot, item);
            else inventory.addItem(item);
        }
        chest.update(true, false);
    }

    private Map<?, ?> weightedEntry(List<Map<?, ?>> entries, Random r) {
        int total = 0;
        for (Map<?, ?> entry : entries) total += Math.max(0, parseInt(entry.get("weight"), 1));
        if (total <= 0) return entries.get(r.nextInt(entries.size()));
        int selected = r.nextInt(total);
        for (Map<?, ?> entry : entries) {
            selected -= Math.max(0, parseInt(entry.get("weight"), 1));
            if (selected < 0) return entry;
        }
        return entries.get(entries.size() - 1);
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private void addRecord(World world, int x, int y, int z, String id) {
        Map<String, Object> record = new HashMap<>();
        record.put("world", world.getName());
        record.put("x", x);
        record.put("y", y);
        record.put("z", z);
        record.put("type", id);
        record.put("created", System.currentTimeMillis());
        records.add(record);
        int max = Math.max(100, getConfig().getInt("storage.max-records", 10000));
        while (records.size() > max) records.remove(0);
        trimReservedPositions(max);
        saveStructureRecords();
    }

    private void loadStructureRecords() {
        structuresFile = new File(getDataFolder(), "structures.yml");
        if (!structuresFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(structuresFile);
        records.clear();
        for (Map<?, ?> map : data.getMapList("records")) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            records.add(copy);
            if (copy.containsKey("world")) {
                reservedPositions.add(new ReservedPosition(String.valueOf(copy.get("world")),
                        number(copy.get("x")), number(copy.get("z"))));
            }
        }
    }

    private void saveStructureRecords() {
        if (structuresFile == null) return;
        YamlConfiguration data = new YamlConfiguration();
        data.set("records", records);
        try {
            data.save(structuresFile);
        } catch (IOException exception) {
            getLogger().warning("Could not save structures.yml: " + exception.getMessage());
        }
    }

    private int number(Object value) {
        return parseInt(value, 0);
    }

    private boolean manualGenerate(Player player, String id) {
        World world = player.getWorld();
        if (!isWorldAllowed(world)) {
            player.sendMessage(color("&cStructures are disabled in this world. Allowed worlds: world, world_nether, world_the_end."));
            return false;
        }
        String normalizedId = id.toLowerCase(Locale.ROOT);
        if (!isKnownStructure(normalizedId)) {
            player.sendMessage(color("&cUnknown structure. Use /gstructures list."));
            return false;
        }
        if (!isStructureAllowedForWorld(world, normalizedId)) {
            player.sendMessage(color("&cThis structure does not belong to this dimension."));
            return false;
        }
        Location location = player.getLocation();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        // Center manual builds within the player's current chunk to keep every block in the same chunk.
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        if (isNearWorldSpawn(world, centerX, centerZ)) {
            player.sendMessage(color("&cYou cannot generate structures this close to this world's spawn."));
            return false;
        }
        String theme = getConfig().getString("worlds." + world.getName() + ".theme", "overworld");
        int floorY = findSurfaceY(world, centerX, centerZ, theme, location.getBlockY());
        if (floorY == Integer.MIN_VALUE) {
            player.sendMessage(color("&cNo safe ground found near this chunk center."));
            return false;
        }
        if (getConfig().getBoolean("generation.require-flat-ground", true)
                && !"nether".equalsIgnoreCase(theme)
                && !isFlatEnough(world, floorY - 1, centerX, centerZ)) {
            player.sendMessage(color("&cThe selected area is too uneven for this structure."));
            return false;
        }
        if (isNearExistingStructure(world, centerX, centerZ)) {
            player.sendMessage(color("&eA generated structure is already nearby."));
            return false;
        }
        double minimumDistance = getConfig().getDouble("generation.min-distance-between-structures", 180.0);
        if (!reserveStructure(world.getName(), centerX, centerZ, minimumDistance)) {
            player.sendMessage(color("&eA generated structure is already nearby."));
            return false;
        }
        placeStructure(world, centerX, floorY, centerZ, normalizedId, chunkX, chunkZ);
        player.sendMessage(color("&aGenerated &b" + id + " &aat &f" + centerX + ", " + floorY + ", " + centerZ));
        return true;
    }

    private void locateNearest(Player player) {
        Map<String, Object> nearest = null;
        double best = Double.MAX_VALUE;
        Location current = player.getLocation();
        for (Map<String, Object> record : records) {
            if (!current.getWorld().getName().equals(String.valueOf(record.get("world")))) continue;
            int x = number(record.get("x"));
            int z = number(record.get("z"));
            double dx = current.getX() - x;
            double dz = current.getZ() - z;
            double distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                nearest = record;
            }
        }
        if (nearest == null) {
            player.sendMessage(color("&eNo recorded structures found in this world yet."));
            return;
        }
        String type = String.valueOf(nearest.get("type"));
        String display = color(getConfig().getString("structures." + type + ".display-name", type));
        int x = number(nearest.get("x"));
        int y = number(nearest.get("y"));
        int z = number(nearest.get("z"));
        player.sendMessage(color("&8&m--------------------------------"));
        player.sendMessage(color("&b&lNEAREST TREASURE STRUCTURE"));
        player.sendMessage(color("&7Type: &f") + display);
        player.sendMessage(color("&7World: &f" + current.getWorld().getName()));
        player.sendMessage(color("&7Coordinates: &b" + x + "&7, &b" + y + "&7, &b" + z));
        player.sendMessage(color("&7Distance: &f" + (int) Math.sqrt(best) + " blocks"));
        player.sendMessage(color("&8&m--------------------------------"));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(color(PREFIX + "&b/gstructures locate &7- Find the nearest recorded structure"));
            sender.sendMessage(color(PREFIX + "&b/gstructures list &7- Show structure types"));
            sender.sendMessage(color(PREFIX + "&b/gstructures generate <type> &7- Generate near you (admin)"));
            sender.sendMessage(color(PREFIX + "&b/gstructures reload &7- Reload config (admin)"));
            return true;
        }

        if (args[0].equalsIgnoreCase("locate")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color(PREFIX + "&cThis command can only be used in-game."));
                return true;
            }
            locateNearest(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(color("&8&m---------------- &b&lSTRUCTURES &8&m----------------"));
            for (String id : List.of("forest_shrine", "ruined_watchtower", "buried_ruins", "soul_vault", "magma_cache", "void_shrine", "ender_observatory")) {
                sender.sendMessage(color("&b" + id + " &8- &7" + getConfig().getString("structures." + id + ".display-name", id)));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gawharastructures.admin")) {
                sender.sendMessage(color(PREFIX + "&cYou do not have permission."));
                return true;
            }
            reloadConfig();
            for (World world : Bukkit.getWorlds()) registerWorldPopulator(world);
            sender.sendMessage(color(PREFIX + "&aConfiguration reloaded."));
            return true;
        }

        if (args[0].equalsIgnoreCase("generate")) {
            if (!sender.hasPermission("gawharastructures.admin")) {
                sender.sendMessage(color(PREFIX + "&cYou do not have permission."));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color(PREFIX + "&cRun this command in-game while standing near the desired chunk."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(color(PREFIX + "&cUsage: /gstructures generate <type>"));
                return true;
            }
            manualGenerate(player, args[1]);
            return true;
        }

        sender.sendMessage(color(PREFIX + "&cUnknown subcommand. Use /gstructures help."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("help", "locate", "list", "generate", "reload").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
            return List.of("forest_shrine", "ruined_watchtower", "buried_ruins", "soul_vault", "magma_cache", "void_shrine", "ender_observatory").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
