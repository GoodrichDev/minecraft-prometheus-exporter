package de.sldk.mc.metrics;

import de.sldk.mc.metrics.tick_duration.TickDurationCollector;
import io.prometheus.client.Gauge;
import org.bukkit.plugin.Plugin;

public class TickDurationMaxCollector extends Metric {
    private static final String NAME = "tick_duration_max";
    private final TickDurationCollector collector = TickDurationCollector.forServerImplementation(this.getPlugin());

    private static final Gauge TD = Gauge.build()
            .name(prefix(NAME))
            .help("Max duration of server tick (nanoseconds)")
            .create();

    public TickDurationMaxCollector(Plugin plugin) {
        super(plugin, TD);
    }

    @Override
    public void doCollect() {
        if (!collector.isAvailable()) {
            return;
        }
        TD.set(collector.getMaxTickDurationNanos());
    }

    @Override
    public boolean isFoliaCapable() {
        return collector.isFoliaCapable();
    }
}
