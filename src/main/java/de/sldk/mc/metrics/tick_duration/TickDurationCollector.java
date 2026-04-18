package de.sldk.mc.metrics.tick_duration;

import de.sldk.mc.folia.FoliaSupport;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TickDurationCollector {

    private final TickDurationStrategy strategy;

    private final boolean foliaCapable;

    private TickDurationCollector(TickDurationStrategy strategy, boolean foliaCapable) {
        this.strategy = strategy;
        this.foliaCapable = foliaCapable;
    }

    public static TickDurationCollector forServerImplementation(Plugin plugin) {
        Logger logger = plugin.getLogger();

        if (FoliaSupport.isFolia()) {
            FoliaTickDurationStrategy foliaStrategy = new FoliaTickDurationStrategy(plugin.getServer(), logger);
            if (foliaStrategy.isAvailable()) {
                logger.log(Level.FINE, "Using Folia threaded-region tick statistics.");
                return new TickDurationCollector(foliaStrategy, true);
            }

            logger.log(Level.WARNING, "Folia threaded-region tick statistics are not available. Tick duration metrics will not be enabled.");
            return new TickDurationCollector(new NoOpTickDurationStrategy(), false);
        }

        PaperTickDurationStrategy paperStrategy = new PaperTickDurationStrategy();

        if (paperStrategy.isAvailable()) {
            logger.log(Level.FINE, "Using Paper tick times method.");
            return new TickDurationCollector(paperStrategy, false);
        }

        logger.log(Level.FINE, "Using default tick times guessing method.");
        DefaultTickDurationStrategy defaultTickDurationStrategy = new DefaultTickDurationStrategy(logger);
        return new TickDurationCollector(defaultTickDurationStrategy, false);
    }

    public boolean isFoliaCapable() {
        return foliaCapable;
    }

    public boolean isAvailable() {
        return strategy.isAvailable();
    }

    public double getAverageTickDurationNanos() {
        return strategy.getAverageTickDurationNanos();
    }

    public double getMedianTickDurationNanos() {
        return strategy.getMedianTickDurationNanos();
    }

    public double getMinTickDurationNanos() {
        return strategy.getMinTickDurationNanos();
    }

    public double getMaxTickDurationNanos() {
        return strategy.getMaxTickDurationNanos();
    }

    private static class NoOpTickDurationStrategy implements TickDurationStrategy {

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public double getAverageTickDurationNanos() {
            return 0.0D;
        }

        @Override
        public double getMedianTickDurationNanos() {
            return 0.0D;
        }

        @Override
        public double getMinTickDurationNanos() {
            return 0.0D;
        }

        @Override
        public double getMaxTickDurationNanos() {
            return 0.0D;
        }
    }
}
