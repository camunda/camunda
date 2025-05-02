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
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class ExportDurationObserverTest {
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final CamundaExporterMetrics metrics = new CamundaExporterMetrics(meterRegistry);
  private final AtomicLong clock = new AtomicLong(0L);

  @Test
  void shouldClearTimestampsBetweenObserveDurations() {
    // given
    final var observer =
        new ExportDurationObserver(metrics, () -> Instant.ofEpochMilli(clock.get()));

    // when
    observer.cacheRecordTimestamp(1L, 1L);
    observer.cacheRecordTimestamp(2L, 2L);
    clock.set(7L);
    observer.observeDurations();
    observer.observeDurations();
    observer.observeDurations();

    // then - we only observe 2 record durations, not more, even though we flush multiple times
    final var timer = meterRegistry.get("zeebe.camunda.exporter.record.export.duration").timer();
    assertThat(timer.count()).isEqualTo(2);
    assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(6L);
  }

  @Test
  void shouldNotObserveSameRecordTwice() {
    // given
    final var observer =
        new ExportDurationObserver(metrics, () -> Instant.ofEpochMilli(clock.get()));

    // when
    observer.cacheRecordTimestamp(1L, 1L);
    // it should take whatever the latest value is, though that should never happen in production
    // anyway...
    observer.cacheRecordTimestamp(1L, 2L);
    clock.set(7L);
    observer.observeDurations();

    // then - we only observe 2 record durations, not more, even though we flush multiple times
    final var timer = meterRegistry.get("zeebe.camunda.exporter.record.export.duration").timer();
    assertThat(timer.count()).isOne();
    assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(5L);
  }
}
