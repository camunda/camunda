/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

public class OpensearchMetrics {
  private static final String NAMESPACE = "zeebe.opensearch.exporter";

  private final MeterRegistry meterRegistry;
  private final StatefulGauge bulkMemorySize;
  private final Timer flushDuration;
  private final DistributionSummary bulkSize;
  private final Counter failedFlush;

  public OpensearchMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    bulkMemorySize =
        StatefulGauge.builder(meterName("bulk.memory.size"))
            .description("Exporter bulk memory size")
            .register(meterRegistry);
    flushDuration =
        Timer.builder(meterName("flush.duration.seconds"))
            .description("Flush duration of bulk exporters in seconds")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .register(meterRegistry);
    bulkSize =
        DistributionSummary.builder(meterName("bulk.size"))
            .description("Exporter bulk size")
            .serviceLevelObjectives(10, 100, 1_000, 10_000, 100_000)
            .register(meterRegistry);
    failedFlush =
        Counter.builder(meterName("failed.flush"))
            .description("Number of failed flush operations")
            .register(meterRegistry);
  }

  public CloseableSilently measureFlushDuration() {
    return MicrometerUtil.timer(flushDuration, Timer.start(meterRegistry));
  }

  public void recordBulkSize(final int bulkSize) {
    this.bulkSize.record(bulkSize);
  }

  public void recordBulkMemorySize(final int bulkMemorySize) {
    this.bulkMemorySize.set(bulkMemorySize);
  }

  public void recordFailedFlush() {
    failedFlush.increment();
  }

  private String meterName(final String name) {
    return NAMESPACE + "." + name;
  }
}
