package de.sldk.mc.collectors;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

public final class FoliaEntityCollector implements Listener {

    private static final ConcurrentMap<Plugin, Holder> INSTANCES = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final ConcurrentMap<String, ConcurrentMap<EntityType, AtomicLong>> entityCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<VillagerSnapshot, AtomicLong>> villagerCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SnapshotPresence<EntitySnapshot>> trackedEntities = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SnapshotPresence<VillagerSnapshot>> trackedVillagers = new ConcurrentHashMap<>();

    public FoliaEntityCollector(Plugin plugin) {
        this.plugin = plugin;
    }

    public static FoliaEntityCollector acquire(Plugin plugin) {
        return INSTANCES.compute(plugin, (ignored, existing) -> {
            if (existing != null) {
                existing.references.incrementAndGet();
                return existing;
            }

            FoliaEntityCollector collector = new FoliaEntityCollector(plugin);
            collector.start();
            return new Holder(collector);
        }).collector;
    }

    public static void release(Plugin plugin) {
        INSTANCES.computeIfPresent(plugin, (ignored, existing) -> {
            if (existing.references.decrementAndGet() == 0) {
                existing.collector.stop();
                return null;
            }
            return existing;
        });
    }

    public Map<EntityType, Long> getEntityCounts(String worldName) {
        return snapshot(entityCounts.get(worldName));
    }

    public Map<VillagerSnapshot, Long> getVillagerCounts(String worldName) {
        return snapshot(villagerCounts.get(worldName));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdded(EntityAddToWorldEvent event) {
        track(event.getEntity(), event.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        untrack(event.getEntity(), event.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        String worldName = event.getEntity().getWorld().getName();
        untrack(event.getEntity(), worldName);
        event.getTransformedEntities().forEach(entity -> track(entity, entity.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        updateVillager(villager.getUniqueId(), new VillagerSnapshot(
                villager.getWorld().getName(),
                villager.getVillagerType(),
                event.getProfession(),
                villager.getVillagerLevel()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            updateVillager(villager.getUniqueId(), VillagerSnapshot.from(villager.getWorld().getName(), villager));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerReplenishTrade(VillagerReplenishTradeEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            updateVillager(villager.getUniqueId(), VillagerSnapshot.from(villager.getWorld().getName(), villager));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        String worldName = event.getWorld().getName();

        entityCounts.remove(worldName);
        villagerCounts.remove(worldName);

        trackedEntities.entrySet().removeIf(entry -> purgeEntityWorld(entry.getValue(), worldName));
        trackedVillagers.entrySet().removeIf(entry -> purgeVillagerWorld(entry.getValue(), worldName));
    }

    private void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        bootstrapLoadedChunks();
    }

    private void stop() {
        HandlerList.unregisterAll(this);
        entityCounts.clear();
        villagerCounts.clear();
        trackedEntities.clear();
        trackedVillagers.clear();
    }

    private void bootstrapLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ,
                        () -> bootstrapChunk(world, chunkX, chunkZ));
            }
        }
    }

    private void bootstrapChunk(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ, false);
        if (chunk == null || !chunk.isLoaded()) {
            return;
        }

        String worldName = world.getName();
        for (Entity entity : chunk.getEntities()) {
            track(entity, worldName);
        }
    }

    private void track(Entity entity, String worldName) {
        EntitySnapshot entitySnapshot = new EntitySnapshot(worldName, entity.getType());
        trackedEntities.compute(entity.getUniqueId(), (ignored, current) -> addPresence(current, entitySnapshot, this::incrementEntityCount));

        if (entity instanceof Villager villager) {
            VillagerSnapshot villagerSnapshot = VillagerSnapshot.from(worldName, villager);
            trackedVillagers.compute(entity.getUniqueId(),
                    (ignored, current) -> addPresence(current, villagerSnapshot, this::incrementVillagerCount));
        }
    }

    private void untrack(Entity entity, String worldName) {
        EntitySnapshot entitySnapshot = new EntitySnapshot(worldName, entity.getType());
        trackedEntities.computeIfPresent(entity.getUniqueId(),
                (ignored, current) -> removePresence(current, entitySnapshot, this::decrementEntityCount));

        if (entity instanceof Villager villager) {
            trackedVillagers.computeIfPresent(entity.getUniqueId(),
                    (ignored, current) -> removeVillagerPresence(current, worldName, villager, this::decrementVillagerCount));
        }
    }

    private void updateVillager(UUID entityId, VillagerSnapshot snapshot) {
        trackedVillagers.compute(entityId,
                (ignored, current) -> replacePresence(current, snapshot, this::incrementVillagerCount, this::decrementVillagerCount));
    }

    private SnapshotPresence<EntitySnapshot> addPresence(
            SnapshotPresence<EntitySnapshot> current,
            EntitySnapshot snapshot,
            Consumer<EntitySnapshot> onFirstSeen
    ) {
        SnapshotPresence<EntitySnapshot> presence = current == null ? new SnapshotPresence<>() : current;
        if (presence.add(snapshot)) {
            onFirstSeen.accept(snapshot);
        }
        return presence;
    }

    private SnapshotPresence<VillagerSnapshot> addPresence(
            SnapshotPresence<VillagerSnapshot> current,
            VillagerSnapshot snapshot,
            Consumer<VillagerSnapshot> onFirstSeen
    ) {
        SnapshotPresence<VillagerSnapshot> presence = current == null ? new SnapshotPresence<>() : current;
        if (presence.add(snapshot)) {
            onFirstSeen.accept(snapshot);
        }
        return presence;
    }

    private SnapshotPresence<EntitySnapshot> removePresence(
            SnapshotPresence<EntitySnapshot> current,
            EntitySnapshot snapshot,
            Consumer<EntitySnapshot> onLastSeen
    ) {
        if (current.remove(snapshot)) {
            onLastSeen.accept(snapshot);
        }
        return current.isEmpty() ? null : current;
    }

    private SnapshotPresence<VillagerSnapshot> removeVillagerPresence(
            SnapshotPresence<VillagerSnapshot> current,
            String worldName,
            Villager villager,
            Consumer<VillagerSnapshot> onLastSeen
    ) {
        VillagerSnapshot snapshot = VillagerSnapshot.from(worldName, villager);
        if (!current.remove(snapshot)) {
            current.removeIf(candidate -> candidate.worldName().equals(worldName), onLastSeen);
        } else {
            onLastSeen.accept(snapshot);
        }
        return current.isEmpty() ? null : current;
    }

    private SnapshotPresence<VillagerSnapshot> replacePresence(
            SnapshotPresence<VillagerSnapshot> current,
            VillagerSnapshot snapshot,
            Consumer<VillagerSnapshot> onFirstSeen,
            Consumer<VillagerSnapshot> onLastSeen
    ) {
        SnapshotPresence<VillagerSnapshot> presence = current == null ? new SnapshotPresence<>() : current;
        presence.replaceWith(snapshot, onFirstSeen, onLastSeen);
        return presence;
    }

    private boolean purgeEntityWorld(SnapshotPresence<EntitySnapshot> presence, String worldName) {
        presence.removeIf(snapshot -> snapshot.worldName().equals(worldName), this::decrementEntityCount);
        return presence.isEmpty();
    }

    private boolean purgeVillagerWorld(SnapshotPresence<VillagerSnapshot> presence, String worldName) {
        presence.removeIf(snapshot -> snapshot.worldName().equals(worldName), this::decrementVillagerCount);
        return presence.isEmpty();
    }

    private void incrementEntityCount(EntitySnapshot snapshot) {
        increment(entityCounts, snapshot.worldName(), snapshot.type());
    }

    private void decrementEntityCount(EntitySnapshot snapshot) {
        decrement(entityCounts, snapshot.worldName(), snapshot.type());
    }

    private void incrementVillagerCount(VillagerSnapshot snapshot) {
        increment(villagerCounts, snapshot.worldName(), snapshot);
    }

    private void decrementVillagerCount(VillagerSnapshot snapshot) {
        decrement(villagerCounts, snapshot.worldName(), snapshot);
    }

    private static <K> void increment(ConcurrentMap<String, ConcurrentMap<K, AtomicLong>> counts, String worldName, K key) {
        counts.computeIfAbsent(worldName, ignored -> new ConcurrentHashMap<>())
                .compute(key, (ignored, current) -> {
                    if (current == null) {
                        return new AtomicLong(1);
                    }
                    current.incrementAndGet();
                    return current;
                });
    }

    private static <K> void decrement(ConcurrentMap<String, ConcurrentMap<K, AtomicLong>> counts, String worldName, K key) {
        ConcurrentMap<K, AtomicLong> worldCounts = counts.get(worldName);
        if (worldCounts == null) {
            return;
        }

        worldCounts.computeIfPresent(key, (ignored, current) -> current.decrementAndGet() <= 0 ? null : current);
        if (worldCounts.isEmpty()) {
            counts.remove(worldName, worldCounts);
        }
    }

    private static <K> Map<K, Long> snapshot(Map<K, AtomicLong> worldCounts) {
        if (worldCounts == null || worldCounts.isEmpty()) {
            return Collections.emptyMap();
        }

        return worldCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }

    private interface WorldScopedSnapshot {
        String worldName();
    }

    private record Holder(FoliaEntityCollector collector, AtomicInteger references) {
        private Holder(FoliaEntityCollector collector) {
            this(collector, new AtomicInteger(1));
        }
    }

    private record EntitySnapshot(String worldName, EntityType type) implements WorldScopedSnapshot {
    }

    public record VillagerSnapshot(
            String worldName,
            Villager.Type type,
            Villager.Profession profession,
            int level
    ) implements WorldScopedSnapshot {
        public static VillagerSnapshot from(String worldName, Villager villager) {
            return new VillagerSnapshot(worldName,
                    villager.getVillagerType(),
                    villager.getProfession(),
                    villager.getVillagerLevel());
        }
    }

    private static final class SnapshotPresence<S> {
        private final Map<S, Integer> snapshots = new HashMap<>();

        boolean add(S snapshot) {
            int previous = snapshots.getOrDefault(snapshot, 0);
            snapshots.put(snapshot, previous + 1);
            return previous == 0;
        }

        boolean remove(S snapshot) {
            Integer previous = snapshots.get(snapshot);
            if (previous == null) {
                return false;
            }

            if (previous == 1) {
                snapshots.remove(snapshot);
                return true;
            }

            snapshots.put(snapshot, previous - 1);
            return false;
        }

        void removeIf(HasMatch<S> matcher, Consumer<? super S> onLastSeen) {
            Iterator<Map.Entry<S, Integer>> iterator = snapshots.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<S, Integer> entry = iterator.next();
                if (!matcher.matches(entry.getKey())) {
                    continue;
                }

                if (entry.getValue() == 1) {
                    iterator.remove();
                    onLastSeen.accept(entry.getKey());
                } else {
                    entry.setValue(entry.getValue() - 1);
                }
                return;
            }
        }

        void replaceWith(S snapshot, Consumer<? super S> onFirstSeen, Consumer<? super S> onLastSeen) {
            if (snapshots.isEmpty()) {
                snapshots.put(snapshot, 1);
                onFirstSeen.accept(snapshot);
                return;
            }

            int totalPresence = snapshots.values().stream().mapToInt(Integer::intValue).sum();
            boolean alreadyPresent = snapshots.containsKey(snapshot);

            Iterator<Map.Entry<S, Integer>> iterator = snapshots.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<S, Integer> entry = iterator.next();
                if (entry.getKey().equals(snapshot)) {
                    continue;
                }
                iterator.remove();
                onLastSeen.accept(entry.getKey());
            }

            snapshots.put(snapshot, totalPresence);
            if (!alreadyPresent) {
                onFirstSeen.accept(snapshot);
            }
        }

        boolean isEmpty() {
            return snapshots.isEmpty();
        }
    }

    @FunctionalInterface
    private interface HasMatch<T> {
        boolean matches(T value);
    }
}
