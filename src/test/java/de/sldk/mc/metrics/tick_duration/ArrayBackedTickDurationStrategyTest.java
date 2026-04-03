package de.sldk.mc.metrics.tick_duration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArrayBackedTickDurationStrategyTest {

    @Test
    void returnsZeroValuesForEmptySamples() {
        ArrayBackedTickDurationStrategy strategy = new TestStrategy();

        assertThat(strategy.isAvailable()).isFalse();
        assertThat(strategy.getAverageTickDurationNanos()).isZero();
        assertThat(strategy.getMedianTickDurationNanos()).isZero();
        assertThat(strategy.getMinTickDurationNanos()).isZero();
        assertThat(strategy.getMaxTickDurationNanos()).isZero();
    }

    @Test
    void calculatesStatisticsFromTickSamples() {
        ArrayBackedTickDurationStrategy strategy = new TestStrategy(10L, 40L, 20L, 30L);

        assertThat(strategy.isAvailable()).isTrue();
        assertThat(strategy.getAverageTickDurationNanos()).isEqualTo(25.0D);
        assertThat(strategy.getMedianTickDurationNanos()).isEqualTo(30.0D);
        assertThat(strategy.getMinTickDurationNanos()).isEqualTo(10.0D);
        assertThat(strategy.getMaxTickDurationNanos()).isEqualTo(40.0D);
    }

    private static class TestStrategy extends ArrayBackedTickDurationStrategy {

        private final long[] tickDurations;

        private TestStrategy(long... tickDurations) {
            this.tickDurations = tickDurations;
        }

        @Override
        protected long[] getTickDurations() {
            return tickDurations.clone();
        }
    }
}
