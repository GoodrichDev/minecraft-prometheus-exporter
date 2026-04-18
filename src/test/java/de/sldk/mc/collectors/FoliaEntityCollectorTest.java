package de.sldk.mc.collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FoliaEntityCollectorTest {

    private static final String WORLD_NAME = "world";

    private FoliaEntityCollector collector;
    private World world;

    @BeforeEach
    void beforeEachTest() {
        collector = new FoliaEntityCollector(mock(Plugin.class));
        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD_NAME);
    }

    @Test
    void givenOverlappingAddAndRemoveEventsExpectEntityCountToStayStable() {
        Entity pig = mockEntity(UUID.randomUUID(), EntityType.PIG);

        collector.onEntityAdded(new EntityAddToWorldEvent(pig, world));
        collector.onEntityAdded(new EntityAddToWorldEvent(pig, world));
        collector.onEntityRemoved(new EntityRemoveFromWorldEvent(pig, world));

        assertThat(collector.getEntityCounts(WORLD_NAME)).containsEntry(EntityType.PIG, 1L);

        collector.onEntityRemoved(new EntityRemoveFromWorldEvent(pig, world));

        assertThat(collector.getEntityCounts(WORLD_NAME)).doesNotContainKey(EntityType.PIG);
    }

    @Test
    void givenVillagerCareerChangeExpectGroupingToMove() {
        Villager villager = mockVillager(
                UUID.randomUUID(),
                Villager.Type.DESERT,
                Villager.Profession.FARMER,
                2);

        collector.onEntityAdded(new EntityAddToWorldEvent(villager, world));
        collector.onVillagerCareerChange(new VillagerCareerChangeEvent(
                villager,
                Villager.Profession.LIBRARIAN,
                VillagerCareerChangeEvent.ChangeReason.EMPLOYED));

        Map<FoliaEntityCollector.VillagerSnapshot, Long> villagers = collector.getVillagerCounts(WORLD_NAME);

        assertThat(villagers)
                .doesNotContainKey(new FoliaEntityCollector.VillagerSnapshot(
                        WORLD_NAME,
                        Villager.Type.DESERT,
                        Villager.Profession.FARMER,
                        2))
                .containsEntry(new FoliaEntityCollector.VillagerSnapshot(
                        WORLD_NAME,
                        Villager.Type.DESERT,
                        Villager.Profession.LIBRARIAN,
                        2), 1L);
    }

    @Test
    void givenVillagerRemovedAfterLabelDriftExpectCountToBeCleared() {
        Villager villager = mockVillager(
                UUID.randomUUID(),
                Villager.Type.PLAINS,
                Villager.Profession.FARMER,
                1);

        collector.onEntityAdded(new EntityAddToWorldEvent(villager, world));
        when(villager.getProfession()).thenReturn(Villager.Profession.BUTCHER);

        collector.onEntityRemoved(new EntityRemoveFromWorldEvent(villager, world));

        assertThat(collector.getVillagerCounts(WORLD_NAME)).isEmpty();
    }

    private Entity mockEntity(UUID uuid, EntityType type) {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(uuid);
        when(entity.getType()).thenReturn(type);
        return entity;
    }

    private Villager mockVillager(UUID uuid, Villager.Type type, Villager.Profession profession, int level) {
        Villager villager = mock(Villager.class);
        when(villager.getUniqueId()).thenReturn(uuid);
        when(villager.getType()).thenReturn(EntityType.VILLAGER);
        when(villager.getVillagerType()).thenReturn(type);
        when(villager.getProfession()).thenReturn(profession);
        when(villager.getVillagerLevel()).thenReturn(level);
        when(villager.getWorld()).thenReturn(world);
        return villager;
    }
}
