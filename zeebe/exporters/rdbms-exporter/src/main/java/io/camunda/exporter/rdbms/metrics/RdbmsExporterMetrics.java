/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.metrics;

import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.ResourceSample;
import io.micrometer.core.instrument.Timer.Sample;
import java.time.Duration;

public class RdbmsExporterMetrics implements RdbmsWriterMetrics {

  private static final String NAMESPACE = "zeebe.rdbms.exporter";

  private final MeterRegistry meterRegistry;
  private final Timer flushLatency;
  private Sample flushLatencyMeasurement;

  public RdbmsExporterMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    flushLatency =
        Timer.builder(meterName("flush.latency"))
            .description(
                "Time of how long a export buffer is open and collects new records before flushing, meaning latency until the next flush is done.")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  @Override
  public ResourceSample measureFlushDuration() {
    return Timer.resource(meterRegistry, meterName("flush.duration.seconds"))
        .description("Flush duration of bulk exporters in seconds")
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  @Override
  public void recordBulkSize(final int bulkSize) {
    DistributionSummary.builder(meterName("bulk.size"))
        .description("Exporter bulk size")
        .serviceLevelObjectives(10, 100, 1_000, 10_000, 100_000)
        .register(meterRegistry)
        .record(bulkSize);
  }

  @Override
  public void recordFailedFlush() {
    Counter.builder(meterName("failed.flush"))
        .description("Number of failed flush operations")
        .register(meterRegistry)
        .increment();
  }

  @Override
  public void recordExecutedStatement(final String statementId) {
    Counter.builder(meterName("statements"))
        .tags("statementId", statementId)
        .description("Number of executed statements")
        .register(meterRegistry)
        .increment();
  }

  @Override
  public void startFlushLatencyMeasurement() {
    flushLatencyMeasurement = Timer.start(meterRegistry);
  }

  @Override
  public void stopFlushLatencyMeasurement() {
    if (flushLatencyMeasurement != null) {
      flushLatencyMeasurement.stop(flushLatency);
    }
  }

  private String meterName(final String name) {
    return NAMESPACE + "." + name;
  }
}
