package de.sldk.mc.metrics.tick_duration;

public interface TickDurationStrategy {
    boolean isAvailable();

    double getAverageTickDurationNanos();

    double getMedianTickDurationNanos();

    double getMinTickDurationNanos();

    double getMaxTickDurationNanos();
}
