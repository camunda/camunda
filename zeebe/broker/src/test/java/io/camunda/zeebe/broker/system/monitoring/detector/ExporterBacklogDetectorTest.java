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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class ExporterBacklogDetectorTest {

  private static final Instant T0 = Instant.parse("2026-11-27T10:00:00Z");

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final AtomicLong committed = new AtomicLong();
  private final AtomicLong exportedA = new AtomicLong();
  private final AtomicLong exportedB = new AtomicLong();
  private final ExporterBacklogDetector detector =
      new ExporterBacklogDetector(
          registry,
          child -> true,
          new ConditionThresholds(1_000, 100, Duration.ofSeconds(10), Duration.ZERO));

  ExporterBacklogDetectorTest() {
    Gauge.builder(ExporterBacklogDetector.COMMITTED_POSITION, committed, AtomicLong::get)
        .tags("physicalTenant", "default", "partition", "1")
        .register(registry);
    exportedGauge("elasticsearch", exportedA);
    exportedGauge("rdbms", exportedB);
  }

  @Test
  void shouldMarkTheExporterThatFallsBehind() {
    // given
    committed.set(10_000);
    exportedA.set(9_900);
    exportedB.set(5_000);
    detector.sample(T0);

    // when
    detector.sample(T0.plusSeconds(10));

    // then
    final var children = detector.getHealthReport().children();
    assertThat(children.get("Partition-default-1:elasticsearch").status())
        .isEqualTo(HealthStatus.HEALTHY);
    final var behind = children.get("Partition-default-1:rdbms");
    assertThat(behind.status()).isEqualTo(HealthStatus.DEGRADED);
    assertThat(behind.issue().message())
        .isEqualTo(
            "rdbms is 5000 records behind the committed position, above the threshold of 1000");
  }

  @Test
  void shouldNotStartBeforeTheBacklogLastsForTheMinimumDuration() {
    // given
    committed.set(10_000);
    exportedB.set(5_000);
    exportedA.set(10_000);
    detector.sample(T0);

    // when - the exporter caught up in between
    exportedB.set(10_000);
    detector.sample(T0.plusSeconds(10));

    // then
    assertThat(detector.getHealthReport().status()).isEqualTo(HealthStatus.HEALTHY);
  }

  private void exportedGauge(final String exporterId, final AtomicLong position) {
    Gauge.builder(ExporterBacklogDetector.EXPORTED_POSITION, position, AtomicLong::get)
        .tags("physicalTenant", "default", "partition", "1", "exporter", exporterId)
        .register(registry);
  }
}
