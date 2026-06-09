/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.metrics;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;

/**
 * Holds the App Integrations Exporter specific Micrometer metrics.
 *
 * <p>The {@link MeterRegistry} handed to an exporter is already partition scoped, so no partition
 * tag is added here (consistent with the Elasticsearch / OpenSearch exporters). Standard
 * per-exporter metrics (exporting latency, last exported position, ...) are emitted by the broker
 * for every exporter and are therefore not duplicated here.
 */
public class AppIntegrationsExporterMetrics {

  private static final String NAMESPACE = "zeebe.app.integrations.exporter";
  private static final String PHASE_TAG = "phase";

  private final Timer flushDuration;
  private final DistributionSummary batchSize;
  private final StatefulGauge batchesInFlight;
  private final Counter recordsExported;
  private final Counter exportFailed;
  private final Counter exportUnauthorized;
  private final Counter tokenFetchFailed;
  private final Counter exportTimeout;
  private final Counter tokenTimeout;

  public AppIntegrationsExporterMetrics(final MeterRegistry registry) {
    flushDuration =
        Timer.builder(meterName("flush.duration.seconds"))
            .description("Duration of exporting a batch of events to the backend")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .register(registry);

    batchSize =
        DistributionSummary.builder(meterName("batch.size"))
            .description("Number of events exported per batch")
            .serviceLevelObjectives(1, 10, 50, 100, 500)
            .register(registry);

    batchesInFlight =
        StatefulGauge.builder(meterName("batches.in.flight"))
            .description("Number of batches currently being exported")
            .register(registry);

    recordsExported =
        Counter.builder(meterName("records.exported"))
            .description("Number of events successfully exported to the backend")
            .register(registry);

    exportFailed =
        Counter.builder(meterName("export.failed"))
            .description("Number of failed batch export attempts")
            .register(registry);

    exportUnauthorized =
        Counter.builder(meterName("export.unauthorized"))
            .description("Number of 401 Unauthorized responses received while exporting")
            .register(registry);

    tokenFetchFailed =
        Counter.builder(meterName("token.fetch.failed"))
            .description("Number of failures while fetching a new OAuth token")
            .register(registry);

    exportTimeout =
        Counter.builder(meterName("timeout"))
            .description("Number of timeouts reached while acquiring a token or exporting")
            .tag(PHASE_TAG, Phase.EXPORT.tagValue)
            .register(registry);

    tokenTimeout =
        Counter.builder(meterName("timeout"))
            .description("Number of timeouts reached while acquiring a token or exporting")
            .tag(PHASE_TAG, Phase.TOKEN.tagValue)
            .register(registry);
  }

  /**
   * @return an instance backed by a throwaway {@link SimpleMeterRegistry}, for components
   *     constructed without an exporter {@link MeterRegistry} (e.g. in unit tests).
   */
  public static AppIntegrationsExporterMetrics disabled() {
    return new AppIntegrationsExporterMetrics(new SimpleMeterRegistry());
  }

  /** Measures the duration of a batch export. */
  public void measureFlushDuration(final Runnable flushFunction) {
    flushDuration.record(flushFunction);
  }

  public void recordBatchSize(final int size) {
    batchSize.record(size);
  }

  public void recordExported(final int count) {
    recordsExported.increment(count);
  }

  public void recordExportFailed() {
    exportFailed.increment();
  }

  public void recordUnauthorized() {
    exportUnauthorized.increment();
  }

  public void recordTokenFetchFailed() {
    tokenFetchFailed.increment();
  }

  public void recordTimeout(final Phase phase) {
    final Counter counter = phase == Phase.TOKEN ? tokenTimeout : exportTimeout;
    counter.increment();
  }

  public void incrementBatchesInFlight() {
    batchesInFlight.increment();
  }

  public void decrementBatchesInFlight() {
    batchesInFlight.decrement();
  }

  private static String meterName(final String name) {
    return NAMESPACE + "." + name;
  }

  /** The phase during which a timeout was reached. */
  public enum Phase {
    TOKEN("token"),
    EXPORT("export");

    private final String tagValue;

    Phase(final String tagValue) {
      this.tagValue = tagValue;
    }
  }
}
