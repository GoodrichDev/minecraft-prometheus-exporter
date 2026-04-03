package de.sldk.mc.collectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TpsCollectorTest {

	private long systemTime;
	private TpsCollector tpsCollector;

	@BeforeEach
	void setup() {
		systemTime = 0L;
		tpsCollector = new TpsCollector(() -> systemTime);
	}

	@Test
	void test_optimal_ticks() {

		advanceSystemTime(TpsCollector.TICKS_PER_SECOND);
		tpsCollector.run();

		advanceSystemTime(TpsCollector.TICKS_PER_SECOND);
		tpsCollector.run();

		assertThat(tpsCollector.getAverageTPS()).isEqualTo(20);
	}

	@Test
	void test_slow_ticks() {

		advanceSystemTime(2667L);
		tpsCollector.run();

		advanceSystemTime(2667L);
		tpsCollector.run();

		assertThat(tpsCollector.getAverageTPS()).isCloseTo(15, Offset.offset(0.01f));
	}

	@Test
	void test_invalid_ticks_should_be_ignored() {

		advanceSystemTime(TpsCollector.TICKS_PER_SECOND);
		tpsCollector.run();

		advanceSystemTime(0);
		tpsCollector.run();


		advanceSystemTime(-1000);
		tpsCollector.run();

		assertThat(tpsCollector.getAverageTPS()).isEqualTo(20);
	}

	@Test
	void test_default_value_before_first_poll() {

		assertThat(tpsCollector.getAverageTPS()).isEqualTo(20);
	}

	private void advanceSystemTime(long millis) {
		systemTime += millis;
	}

}
