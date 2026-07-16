/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.FactType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class MicrometerProjectionMetricsTest {

  private static final int PARTITION = 2;

  @Test
  void shouldCountEachSignalTaggedByPartition() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final MicrometerProjectionMetrics metrics =
        new MicrometerProjectionMetrics(registry, PARTITION);

    // when
    metrics.duplicateSkipped();
    metrics.foldRowMissing();
    metrics.factDropped();

    // then every correctness signal reaches Micrometer under its partition tag
    assertThat(counter(registry, "analytics.projection.duplicate.skipped")).isEqualTo(1.0);
    assertThat(counter(registry, "analytics.projection.fold.row.missing")).isEqualTo(1.0);
    assertThat(counter(registry, "analytics.projection.fact.dropped")).isEqualTo(1.0);
  }

  @Test
  void shouldCountFactsEmittedPerFactTypePreResolved() {
    // given a fresh registry (every FactType's counter is pre-created at construction)
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final MicrometerProjectionMetrics metrics =
        new MicrometerProjectionMetrics(registry, PARTITION);

    // when facts of two types are emitted
    metrics.factEmitted(FactType.PROCESS_INSTANCE);
    metrics.factEmitted(FactType.PROCESS_INSTANCE);
    metrics.factEmitted(FactType.INCIDENT);

    // then each factType's count is independent, and an unseen type stays at zero
    assertThat(
            registry
                .get("analytics.facts.emitted")
                .tag("partition", String.valueOf(PARTITION))
                .tag("factType", FactType.PROCESS_INSTANCE.name())
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry
                .get("analytics.facts.emitted")
                .tag("partition", String.valueOf(PARTITION))
                .tag("factType", FactType.INCIDENT.name())
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("analytics.facts.emitted")
                .tag("partition", String.valueOf(PARTITION))
                .tag("factType", FactType.ELEMENT.name())
                .counter()
                .count())
        .isZero();
  }

  private static double counter(final SimpleMeterRegistry registry, final String name) {
    return registry.get(name).tag("partition", String.valueOf(PARTITION)).counter().count();
  }
}
