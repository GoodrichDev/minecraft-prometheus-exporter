package de.sldk.mc.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.prometheus.client.CollectorRegistry;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerOnlineTest {

    private static final String METRIC_NAME = "mc_player_online";
    private static final String[] METRIC_LABELS = {"name", "uid"};

    private Plugin plugin;

    @BeforeEach
    void beforeEachTest() {
        CollectorRegistry.defaultRegistry.clear();
        plugin = mock(Plugin.class);
    }

    @AfterEach
    void afterEachTest() {
        CollectorRegistry.defaultRegistry.clear();
    }

    @Test
    void foliaCollectionUsesOnlyOnlinePlayersAndClearsStaleSeries() {
        OfflinePlayer player = mock(OfflinePlayer.class);
        UUID uuid = UUID.fromString("1d2d94b7-1e58-42fa-b57f-2d5a7cf2d18e");

        Player onlinePlayer = mock(Player.class);
        when(onlinePlayer.getName()).thenReturn("loic");
        when(player.getPlayer()).thenReturn(onlinePlayer);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);

        PlayerOnline metric = new PlayerOnline(plugin, true, () -> List.of(player));
        metric.enable();
        metric.doCollect();

        assertThat(CollectorRegistry.defaultRegistry.getSampleValue(
                METRIC_NAME,
                METRIC_LABELS,
                new String[]{"loic", uuid.toString()})).isEqualTo(1D);

        PlayerOnline emptyMetric = new PlayerOnline(plugin, true, Collections::emptyList);
        emptyMetric.doCollect();

        assertThat(CollectorRegistry.defaultRegistry.getSampleValue(METRIC_NAME)).isNull();
    }

    @Test
    void offlinePlayerUsesProfileNameBeforeLegacyNameLookup() {
        OfflinePlayer player = mock(OfflinePlayer.class);
        PlayerProfile profile = stubProfile("cached-name");
        UUID uuid = UUID.fromString("f784df5a-c56f-41ab-b63d-d4db21f1b639");

        when(player.getPlayer()).thenReturn(null);
        when(player.getPlayerProfile()).thenReturn(profile);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(false);

        PlayerOnline metric = new PlayerOnline(plugin, false, () -> List.of(player));
        metric.enable();
        metric.doCollect();

        assertThat(CollectorRegistry.defaultRegistry.getSampleValue(
                METRIC_NAME,
                METRIC_LABELS,
                new String[]{"cached-name", uuid.toString()})).isZero();

        verify(player, never()).getName();
    }

    private static PlayerProfile stubProfile(String name) {
        return (PlayerProfile) Proxy.newProxyInstance(
                PlayerOnlineTest.class.getClassLoader(),
                new Class<?>[]{PlayerProfile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "clone" -> proxy;
                    case "update" -> CompletableFuture.completedFuture(proxy);
                    case "serialize" -> Collections.emptyMap();
                    case "getProperties" -> Collections.emptySet();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "StubPlayerProfile[" + name + "]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
