/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class LoadSpikeDetectorTest {

  private static final Instant T0 = Instant.parse("2026-11-27T10:00:00Z");
  private static final String PARTITION = "Partition-default-1";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final Counter appended =
      Counter.builder(LoadSpikeDetector.RECORD_APPENDED)
          .tags("physicalTenant", "default", "partition", "1", "valueType", "JOB")
          .register(registry);
  private boolean leader = true;
  private final LoadSpikeDetector detector =
      new LoadSpikeDetector(
          registry,
          child -> leader && child.equals(PARTITION),
          new ConditionThresholds(3, 1.5, Duration.ZERO, Duration.ZERO));

  @Test
  void shouldReportANoticeWhenRateJumpsAboveItsRecentAverage() {
    // given - a steady 200 records/s
    detector.sample(T0);
    for (int i = 1; i <= 6; i++) {
      appended.increment(2_000);
      detector.sample(T0.plusSeconds(10L * i));
    }

    // when - 2,000 records/s
    appended.increment(20_000);
    detector.sample(T0.plusSeconds(70));

    // then
    final var partition = detector.getHealthReport().children().get(PARTITION);
    assertThat(partition.status()).isEqualTo(HealthStatus.NOTICE);
    assertThat(partition.issue().message())
        .startsWith("appending 2000 records/s, 10.0x the recent average of 200 records/s");
  }

  @Test
  void shouldIgnoreRatesBelowTheMinimum() {
    // given - idle
    detector.sample(T0);
    detector.sample(T0.plusSeconds(10));
    detector.sample(T0.plusSeconds(20));

    // when - from idle to 50 records/s
    appended.increment(500);
    detector.sample(T0.plusSeconds(30));

    // then
    assertThat(detector.getHealthReport().children().get(PARTITION).status())
        .isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  void shouldIgnorePartitionsThisBrokerDoesNotLead() {
    // given
    leader = false;
    detector.sample(T0);
    appended.increment(2_000);
    detector.sample(T0.plusSeconds(10));

    // when
    appended.increment(200_000);
    detector.sample(T0.plusSeconds(20));

    // then
    assertThat(detector.getHealthReport().children()).isEmpty();
  }
}
