package de.sldk.mc.metrics.tick_duration;

import java.util.Arrays;

abstract class ArrayBackedTickDurationStrategy implements TickDurationStrategy {

    protected abstract long[] getTickDurations();

    @Override
    public boolean isAvailable() {
        return getTickDurations().length > 0;
    }

    @Override
    public double getAverageTickDurationNanos() {
        long[] tickDurations = getTickDurations();
        if (tickDurations.length == 0) {
            return 0.0D;
        }

        double total = 0.0D;
        for (long tickDuration : tickDurations) {
            total += tickDuration;
        }
        return total / tickDurations.length;
    }

    @Override
    public double getMedianTickDurationNanos() {
        long[] tickDurations = getTickDurations();
        if (tickDurations.length == 0) {
            return 0.0D;
        }

        Arrays.sort(tickDurations);
        return tickDurations[tickDurations.length / 2];
    }

    @Override
    public double getMinTickDurationNanos() {
        long[] tickDurations = getTickDurations();
        if (tickDurations.length == 0) {
            return 0.0D;
        }

        long min = Long.MAX_VALUE;
        for (long tickDuration : tickDurations) {
            min = Math.min(min, tickDuration);
        }
        return min;
    }

    @Override
    public double getMaxTickDurationNanos() {
        long[] tickDurations = getTickDurations();
        if (tickDurations.length == 0) {
            return 0.0D;
        }

        long max = Long.MIN_VALUE;
        for (long tickDuration : tickDurations) {
            max = Math.max(max, tickDuration);
        }
        return max;
    }
}
