package de.sldk.mc.metrics.tick_duration;

import de.sldk.mc.folia.FoliaTickStatistics;
import java.util.logging.Logger;
import org.bukkit.Server;

public class FoliaTickDurationStrategy implements TickDurationStrategy {

    private final FoliaTickStatistics tickStatistics;

    public FoliaTickDurationStrategy(Server server, Logger logger) {
        this.tickStatistics = FoliaTickStatistics.forServer(server, logger);
    }

    @Override
    public boolean isAvailable() {
        return tickStatistics.isAvailable();
    }

    @Override
    public double getAverageTickDurationNanos() {
        return tickStatistics.getAverageTickDurationNanos();
    }

    @Override
    public double getMedianTickDurationNanos() {
        return tickStatistics.getMedianTickDurationNanos();
    }

    @Override
    public double getMinTickDurationNanos() {
        return tickStatistics.getMinTickDurationNanos();
    }

    @Override
    public double getMaxTickDurationNanos() {
        return tickStatistics.getMaxTickDurationNanos();
    }
}
