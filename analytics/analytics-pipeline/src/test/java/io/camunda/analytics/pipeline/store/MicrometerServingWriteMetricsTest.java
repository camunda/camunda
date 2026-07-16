/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class MicrometerServingWriteMetricsTest {

  @Test
  void shouldCountRowsWrittenPerDatasetTaggedByBackend() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final MicrometerServingWriteMetrics metrics =
        new MicrometerServingWriteMetrics(registry, "rdbms");

    // when
    metrics.rowWritten("pi-count");
    metrics.rowWritten("pi-count");
    metrics.rowWritten("other-cube");

    // then each dataset's counter moves independently under the backend tag
    assertThat(
            registry
                .get("analytics.serving.rows.written")
                .tag("backend", "rdbms")
                .tag("dataset", "pi-count")
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry
                .get("analytics.serving.rows.written")
                .tag("backend", "rdbms")
                .tag("dataset", "other-cube")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void shouldCountFencedRejectionsAndTimeWrites() {
    // given
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final MicrometerServingWriteMetrics metrics =
        new MicrometerServingWriteMetrics(registry, "elasticsearch");

    // when
    metrics.fencedRejected();
    metrics.writeDuration(TimeUnit.MILLISECONDS.toNanos(7));
    metrics.batchSize(250);

    // then every fixed meter is registered under the backend tag and moved as recorded
    assertThat(
            registry
                .get("analytics.serving.fenced.rejected")
                .tag("backend", "elasticsearch")
                .counter()
                .count())
        .isEqualTo(1.0);
    final var timer =
        registry.get("analytics.serving.write.duration").tag("backend", "elasticsearch").timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(7.0);
    final var summary =
        registry.get("analytics.serving.batch.size").tag("backend", "elasticsearch").summary();
    assertThat(summary.count()).isEqualTo(1);
    assertThat(summary.totalAmount()).isEqualTo(250.0);
  }

  @Test
  void shouldFoldANullDatasetIntoTheUnknownBucket() {
    // given a writer that cannot resolve a row's owning dataset (e.g. a pre-instrumentation SQL)
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final MicrometerServingWriteMetrics metrics =
        new MicrometerServingWriteMetrics(registry, "rdbms");

    // when
    metrics.rowWritten(null);

    // then the row still counts, under a bounded fallback tag instead of a null
    assertThat(
            registry
                .get("analytics.serving.rows.written")
                .tag("backend", "rdbms")
                .tag("dataset", "unknown")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
