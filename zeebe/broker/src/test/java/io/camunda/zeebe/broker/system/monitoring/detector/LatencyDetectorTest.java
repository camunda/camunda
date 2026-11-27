/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LatencyDetectorTest {

  private static final String METER = "test.flush.time";
  private static final Instant T0 = Instant.parse("2026-11-27T10:00:00Z");

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final LatencyDetector detector =
      new LatencyDetector(
          "FlushLatency",
          registry,
          METER,
          "flush latency",
          0.99,
          "Node",
          new ConditionThresholds(100, 50, Duration.ZERO, Duration.ZERO));

  @Test
  void shouldMarkPartitionDegradedWhenQuantileOfWindowExceedsThreshold() {
    // given
    final var timer = timer("1");
    detector.sample(T0);

    // when
    record(timer, 98, Duration.ofMillis(5));
    record(timer, 2, Duration.ofMillis(800));
    detector.sample(T0.plusSeconds(10));

    // then - 2 of 100 flushes took 800 ms, which falls in the 1000 ms bucket
    final var partition = detector.getHealthReport().children().get("Partition-default-1");
    assertThat(partition.status()).isEqualTo(HealthStatus.DEGRADED);
    assertThat(partition.issue().message())
        .isEqualTo("p99 flush latency reached 1000 ms, above the threshold of 100 ms");
    assertThat(partition.issue().since()).isEqualTo(T0.plusSeconds(10));
    assertThat(detector.getHealthReport().status()).isEqualTo(HealthStatus.DEGRADED);
  }

  @Test
  void shouldOnlyLookAtWhatWasRecordedSinceThePreviousSample() {
    // given
    final var timer = timer("1");
    detector.sample(T0);
    record(timer, 1, Duration.ofMillis(800));
    detector.sample(T0.plusSeconds(10));

    // when
    record(timer, 100, Duration.ofMillis(5));
    detector.sample(T0.plusSeconds(20));

    // then
    assertThat(detector.getHealthReport().children().get("Partition-default-1").status())
        .isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  void shouldKeepPartitionsApart() {
    // given
    final var slow = timer("1");
    final var fast = timer("2");
    detector.sample(T0);

    // when
    record(slow, 10, Duration.ofMillis(800));
    record(fast, 10, Duration.ofMillis(5));
    detector.sample(T0.plusSeconds(10));

    // then
    final var children = detector.getHealthReport().children();
    assertThat(children.get("Partition-default-1").status()).isEqualTo(HealthStatus.DEGRADED);
    assertThat(children.get("Partition-default-2").status()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  void shouldNotifyListenersOnlyWhenTheConditionStartsOrEnds() {
    // given
    final var reports = new ArrayList<HealthReport>();
    detector.addFailureListener(new RecordingListener(reports));
    final var timer = timer("1");
    detector.sample(T0);

    // when
    record(timer, 10, Duration.ofMillis(800));
    detector.sample(T0.plusSeconds(10));
    record(timer, 10, Duration.ofMillis(900));
    detector.sample(T0.plusSeconds(20));
    record(timer, 10, Duration.ofMillis(5));
    detector.sample(T0.plusSeconds(30));

    // then
    assertThat(reports)
        .extracting(HealthReport::status)
        .containsExactly(HealthStatus.HEALTHY, HealthStatus.DEGRADED, HealthStatus.HEALTHY);
  }

  @Test
  void shouldForgetPartitionsWhoseTimerIsGone() {
    // given
    final var timer = timer("1");
    detector.sample(T0);
    record(timer, 10, Duration.ofMillis(800));
    detector.sample(T0.plusSeconds(10));

    // when
    registry.remove(timer);
    detector.sample(T0.plusSeconds(20));

    // then
    assertThat(detector.getHealthReport().children()).isEmpty();
    assertThat(detector.getHealthReport().status()).isEqualTo(HealthStatus.HEALTHY);
  }

  private Timer timer(final String partition) {
    // like the Prometheus registry: bucket counts grow for the lifetime of the timer
    return Timer.builder(METER)
        .tags("physicalTenant", "default", "partition", partition)
        .serviceLevelObjectives(
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            Duration.ofMillis(1000))
        .distributionStatisticExpiry(Duration.ofDays(1))
        .distributionStatisticBufferLength(1)
        .register(registry);
  }

  private static void record(final Timer timer, final int times, final Duration duration) {
    for (int i = 0; i < times; i++) {
      timer.record(duration);
    }
  }

  private record RecordingListener(List<HealthReport> reports) implements FailureListener {
    @Override
    public void onFailure(final HealthReport report) {
      reports.add(report);
    }

    @Override
    public void onRecovered(final HealthReport report) {
      reports.add(report);
    }

    @Override
    public void onUnrecoverableFailure(final HealthReport report) {
      reports.add(report);
    }
  }
}
