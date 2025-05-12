/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class CamundaExporterMetricsTest {
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void shouldObserveRecordExportLatencyBasedOnStreamClock() {
    // given
    final var clock = new AtomicLong(0);
    final var metrics =
        new CamundaExporterMetrics(registry, () -> Instant.ofEpochMilli(clock.get()));

    // when
    clock.set(5L);
    metrics.observeRecordExportLatencies(List.of(1L, 2L));
    clock.set(10L);
    metrics.observeRecordExportLatencies(List.of(3L, 4L));

    // then
    final var timer = registry.get("zeebe.camunda.exporter.record.export.duration").timer();
    final var histogramSnapshot = timer.takeSnapshot();
    assertThat(histogramSnapshot.count()).isEqualTo(4);
    assertThat(histogramSnapshot.max()).isEqualTo(Duration.ofMillis(7L).toNanos());
    assertThat(histogramSnapshot.mean()).isEqualTo(Duration.ofMillis(5L).toNanos());

    final var buckets = histogramSnapshot.histogramCounts();
    assertThat(buckets[0].count())
        .as("the first two values are in the lowest bucket <=5ms")
        .isEqualTo(2);
    assertThat(buckets[1].count())
        .as("then all values are in the next bucket <= 10ms")
        .isEqualTo(4);
  }
}
