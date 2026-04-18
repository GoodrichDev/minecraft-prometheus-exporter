package de.sldk.mc.metrics;

import de.sldk.mc.collectors.TpsCollector;
import de.sldk.mc.folia.FoliaSupport;
import de.sldk.mc.folia.FoliaTickStatistics;
import io.prometheus.client.Gauge;
import org.bukkit.plugin.Plugin;

public class Tps extends Metric {

    private static final Gauge TPS = Gauge.build()
            .name(prefix("tps"))
            .help("Server TPS (ticks per second)")
            .create();

    private final boolean folia = FoliaSupport.isFolia();
    private final FoliaTickStatistics foliaTickStatistics;

    private final TpsCollector tpsCollector = new TpsCollector();
    private int taskId = -1;

    public Tps(Plugin plugin) {
        super(plugin, TPS);
        this.foliaTickStatistics = folia ? FoliaTickStatistics.forServer(plugin.getServer(), plugin.getLogger()) : null;
    }

    @Override
    public void enable() {
        super.enable();
        if (!folia) {
            this.taskId = startTask(getPlugin());
        }
    }

    @Override
    public void disable() {
        super.disable();
        if (!folia && taskId != -1) {
            getPlugin().getServer().getScheduler().cancelTask(taskId);
        }
    }

    private int startTask(Plugin plugin) {
        return plugin.getServer()
                .getScheduler()
                .scheduleSyncRepeatingTask(plugin, tpsCollector, 0, TpsCollector.POLL_INTERVAL);
    }

    @Override
    public void doCollect() {
        if (folia) {
            TPS.set(foliaTickStatistics.getAverageTps());
            return;
        }
        TPS.set(tpsCollector.getAverageTPS());
    }

    @Override
    public boolean isFoliaCapable() {
        return !folia || foliaTickStatistics.isAvailable();
    }
}
