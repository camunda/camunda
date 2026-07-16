/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.query.QueryMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class MicrometerQueryMetricsTest {

  @Test
  void shouldRecordDurationTaggedByDataset() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final QueryMetrics metrics = new MicrometerQueryMetrics(registry);

    // when
    metrics.recordQueryDuration("pi-mixed", TimeUnit.MILLISECONDS.toNanos(5));
    metrics.recordQueryDuration("pi-mixed", TimeUnit.MILLISECONDS.toNanos(15));

    // then one timer accumulates both samples, tagged by dataset
    final var timer = registry.get("analytics.query.duration").tag("dataset", "pi-mixed").timer();
    assertThat(timer.count()).isEqualTo(2);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(20.0);
  }

  @Test
  void shouldKeepDatasetsSeparate() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final QueryMetrics metrics = new MicrometerQueryMetrics(registry);

    // when
    metrics.recordQueryDuration("pi-mixed", TimeUnit.MILLISECONDS.toNanos(1));
    metrics.recordQueryDuration("other-cube", TimeUnit.MILLISECONDS.toNanos(2));

    // then each dataset's timer is independent
    assertThat(registry.get("analytics.query.duration").tag("dataset", "pi-mixed").timer().count())
        .isEqualTo(1);
    assertThat(
            registry.get("analytics.query.duration").tag("dataset", "other-cube").timer().count())
        .isEqualTo(1);
  }
}
