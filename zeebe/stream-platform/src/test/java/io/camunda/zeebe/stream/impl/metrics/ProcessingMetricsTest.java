/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.EnumCounters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class ProcessingMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final EnumCounters<StreamProcessorAction> counters =
      new EnumCounters<>(StreamProcessorAction.class);

  @Test
  void shouldExportOnlyWhatItCounted() {
    // given
    counters.add(StreamProcessorAction.PROCESSED, 5);
    final var metrics = new ProcessingMetrics(registry, counters);

    // when
    metrics.commandsProcessed();
    metrics.commandsProcessed();
    metrics.recordsWritten(3);

    // then
    assertThat(exported("processed")).isEqualTo(2);
    assertThat(exported("written")).isEqualTo(3);
  }

  @Test
  void shouldKeepCountingInTheSharedCounters() {
    // given
    counters.add(StreamProcessorAction.PROCESSED, 5);
    final var metrics = new ProcessingMetrics(registry, counters);

    // when
    metrics.commandsProcessed();
    metrics.eventSkipped();

    // then
    assertThat(counters.get(StreamProcessorAction.PROCESSED)).isEqualTo(6);
    assertThat(counters.get(StreamProcessorAction.SKIPPED)).isEqualTo(1);
  }

  @Test
  void shouldNotExportAnActionBeforeItIsCounted() {
    // when
    new ProcessingMetrics(registry, counters);

    // then
    assertThat(registry.find("zeebe.stream.processor.records.total").meters()).isEmpty();
  }

  private double exported(final String action) {
    return registry
        .get("zeebe.stream.processor.records.total")
        .tag("action", action)
        .functionCounter()
        .count();
  }
}
