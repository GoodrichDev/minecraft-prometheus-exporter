package de.sldk.mc.metrics;

import de.sldk.mc.folia.FoliaSupport;
import io.prometheus.client.Gauge;
import java.util.Arrays;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

public class PlayerOnline extends PlayerMetric {

    private static final Gauge PLAYERS_WITH_NAMES = Gauge.build()
            .name(prefix("player_online"))
            .help("Online state by player name")
            .labelNames("name", "uid")
            .create();

    private final boolean folia;
    private final Supplier<Iterable<? extends OfflinePlayer>> playersSupplier;

    public PlayerOnline(Plugin plugin) {
        this(plugin, FoliaSupport.isFolia(), null);
    }

    PlayerOnline(Plugin plugin, boolean folia, Supplier<Iterable<? extends OfflinePlayer>> playersSupplier) {
        super(plugin, PLAYERS_WITH_NAMES);
        this.folia = folia;
        this.playersSupplier = playersSupplier != null ? playersSupplier : defaultPlayersSupplier(folia);
    }

    @Override
    public void collect(OfflinePlayer player) {
        PLAYERS_WITH_NAMES.labels(getNameOrUid(player), getUid(player)).set(player.isOnline() ? 1 : 0);
    }

    @Override
    protected void beforeCollect() {
        if (folia) {
            PLAYERS_WITH_NAMES.clear();
        }
    }

    @Override
    protected Iterable<? extends OfflinePlayer> getPlayers() {
        return playersSupplier.get();
    }

    @Override
    public boolean isFoliaCapable() {
        return true;
    }

    private static Supplier<Iterable<? extends OfflinePlayer>> defaultPlayersSupplier(boolean folia) {
        if (folia) {
            return Bukkit::getOnlinePlayers;
        }

        return () -> Arrays.asList(Bukkit.getOfflinePlayers());
    }
}
