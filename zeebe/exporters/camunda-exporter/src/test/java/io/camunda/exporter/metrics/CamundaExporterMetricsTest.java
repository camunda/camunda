/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
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

  @Test
  void shouldRecordDeletionMetrics() {
    // given
    final var metrics = new CamundaExporterMetrics(registry, Instant::now);

    // when
    metrics.recordProcessInstancesDeleting(3);
    metrics.recordProcessInstancesDeleted(2);
    metrics.measureDeletionDuration(Timer.start());
    metrics.measureDeletionSearch(Timer.start());
    metrics.measureDeletionDocIdsSearchDuration(Timer.start());
    metrics.measureDeletionDelete(5L, Timer.start());

    // then
    assertThat(
            registry
                .get("zeebe.camunda.exporter.deletion.process.instances")
                .tag("state", "deleting")
                .counter()
                .count())
        .isEqualTo(3);
    assertThat(
            registry
                .get("zeebe.camunda.exporter.deletion.process.instances")
                .tag("state", "deleted")
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(registry.get("zeebe.camunda.exporter.deletion.duration").timer().count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("zeebe.camunda.exporter.deletion.docs")
                .tag("type", "delete")
                .counter()
                .count())
        .isEqualTo(5);
    for (final var type : List.of("search", "search-docid-batch", "delete")) {
      assertThat(
              registry
                  .get("zeebe.camunda.exporter.deletion.request.duration")
                  .tag("type", type)
                  .timer()
                  .count())
          .as("request duration timer of type %s", type)
          .isEqualTo(1);
    }
  }

  @Test
  void shouldNotCountDocsWhenDeletionFailedBeforeCountingDocs() {
    // given
    final var metrics = new CamundaExporterMetrics(registry, Instant::now);

    // when
    metrics.measureDeletionDelete(null, Timer.start());

    // then
    assertThat(registry.get("zeebe.camunda.exporter.deletion.docs").counter().count()).isZero();
    assertThat(
            registry
                .get("zeebe.camunda.exporter.deletion.request.duration")
                .tag("type", "delete")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void shouldRecordDeletionIndexMetricsPerSourceIndex() {
    // given
    final var metrics = new CamundaExporterMetrics(registry, Instant::now);

    // when
    metrics.measureDeletionIndexDuration("index-a", Timer.start(), 3L);
    metrics.measureDeletionIndexDuration("index-a", Timer.start(), 4L);
    metrics.measureDeletionIndexDuration("index-b", Timer.start(), null);

    // then
    assertThat(
            registry
                .get("zeebe.camunda.exporter.deletion.index.duration")
                .tag("source", "index-a")
                .timer()
                .count())
        .isEqualTo(2);
    assertThat(
            registry
                .get("zeebe.camunda.exporter.deletion.index.docs")
                .tag("source", "index-a")
                .counter()
                .count())
        .isEqualTo(7);
    assertThat(
            registry
                .get("zeebe.camunda.exporter.deletion.index.docs")
                .tag("source", "index-b")
                .counter()
                .count())
        .isZero();
  }

  @Test
  void shouldRemoveDeletionMetersOnClose() {
    // given
    final var metrics = new CamundaExporterMetrics(registry, Instant::now);
    // this will dynamically create meters
    metrics.measureDeletionIndexDuration("index-a", Timer.start(), 1L);
    metrics.measureDeletionIndexDuration("index-b", Timer.start(), 1L);

    // when
    metrics.close();

    // then
    assertThat(registry.getMeters()).isEmpty();
  }

  @Test
  void shouldRemoveAllRegisteredMetersOnClose() {
    // given
    final var metrics = new CamundaExporterMetrics(registry, Instant::now);

    // this will dynamically create meters
    metrics.recordFlushFailureType("failure1");
    metrics.recordFlushFailureType("failure2");

    assertThat(registry.getMeters()).isNotEmpty();

    // when
    metrics.close();

    // then
    final var meterIds = registry.getMeters().stream().map(Meter::getId).toList();
    assertThat(meterIds).isEmpty();
  }
}
