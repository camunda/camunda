/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.appint.metrics.AppIntegrationsExporterMetrics.Phase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class AppIntegrationsExporterMetricsTest {

  private static final String NAMESPACE = "zeebe.app.integrations.exporter";

  private SimpleMeterRegistry registry;
  private AppIntegrationsExporterMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new AppIntegrationsExporterMetrics(registry);
  }

  @Test
  void shouldRecordTokenFetchFailed() {
    // when
    metrics.recordTokenFetchFailed();
    metrics.recordTokenFetchFailed();

    // then
    assertThat(counter(NAMESPACE + ".token.fetch.failed")).isEqualTo(2.0);
  }

  @Test
  void shouldRecordExportFailed() {
    // when
    metrics.recordExportFailed();

    // then
    assertThat(counter(NAMESPACE + ".export.failed")).isEqualTo(1.0);
  }

  @Test
  void shouldRecordUnauthorized() {
    // when
    metrics.recordUnauthorized();

    // then
    assertThat(counter(NAMESPACE + ".export.unauthorized")).isEqualTo(1.0);
  }

  @Test
  void shouldRecordTimeoutPerPhase() {
    // when
    metrics.recordTimeout(Phase.TOKEN);
    metrics.recordTimeout(Phase.EXPORT);
    metrics.recordTimeout(Phase.EXPORT);

    // then
    assertThat(timeoutCounter("token")).isEqualTo(1.0);
    assertThat(timeoutCounter("export")).isEqualTo(2.0);
  }

  @Test
  void shouldRecordBatchSizeAndExportedRecords() {
    // when
    metrics.recordBatchSize(5);
    metrics.recordExported(5);

    // then
    assertThat(registry.get(NAMESPACE + ".batch.size").summary().totalAmount()).isEqualTo(5.0);
    assertThat(counter(NAMESPACE + ".records.exported")).isEqualTo(5.0);
  }

  @Test
  void shouldMeasureFlushDuration() {
    // when
    metrics.measureFlushDuration(() -> {});

    // then
    assertThat(registry.get(NAMESPACE + ".flush.duration.seconds").timer().count()).isEqualTo(1L);
  }

  @Test
  void shouldTrackBatchesInFlight() {
    // when
    metrics.incrementBatchesInFlight();
    metrics.incrementBatchesInFlight();
    metrics.decrementBatchesInFlight();

    // then
    assertThat(registry.get(NAMESPACE + ".batches.in.flight").gauge().value()).isEqualTo(1.0);
  }

  private double counter(final String name) {
    return registry.get(name).counter().count();
  }

  private double timeoutCounter(final String phase) {
    return registry.get(NAMESPACE + ".timeout").tag("phase", phase).counter().count();
  }
}
