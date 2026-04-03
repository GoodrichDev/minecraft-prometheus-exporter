package de.sldk.mc.metrics;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.sldk.mc.folia.FoliaSupport;
import io.prometheus.client.Collector;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public abstract class PlayerMetric extends Metric {

    public PlayerMetric(Plugin plugin, Collector collector) {
        super(plugin, collector);
    }

    @Override
    public final void doCollect() {
        beforeCollect();

        for (OfflinePlayer player : getPlayers()) {
            collect(player);
        }
    }

    protected void beforeCollect() {
    }

    protected Iterable<? extends OfflinePlayer> getPlayers() {
        return Arrays.asList(Bukkit.getOfflinePlayers());
    }

    protected abstract void collect(OfflinePlayer player);

    protected String getUid(OfflinePlayer player) {
        return player.getUniqueId().toString();
    }

    protected String getNameOrUid(OfflinePlayer player) {
        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer != null && onlinePlayer.getName() != null) {
            return onlinePlayer.getName();
        }

        try {
            PlayerProfile profile = player.getPlayerProfile();
            if (profile != null && profile.getName() != null) {
                return profile.getName();
            }
        } catch (Throwable ignored) {
            // Fall back to legacy name lookup if the runtime does not expose Paper profiles.
        }

        if (!FoliaSupport.isFolia()) {
            String playerName = player.getName();
            if (playerName != null) {
                return playerName;
            }
        }

        return player.getUniqueId().toString();
    }

}
