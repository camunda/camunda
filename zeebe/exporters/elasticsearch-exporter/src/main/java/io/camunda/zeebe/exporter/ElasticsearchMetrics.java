/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;

public class ElasticsearchMetrics {
  private static final String NAMESPACE = "zeebe.elasticsearch.exporter";
  private static final String PARTITION_LABEL = "partition";

  private final String partitionIdLabel;
  private final MeterRegistry meterRegistry;
  private final AtomicInteger bulkMemorySize = new AtomicInteger(0);
  private final Timer flushDuration;
  private final DistributionSummary bulkSize;
  private final Counter failedFlush;

  public ElasticsearchMetrics(final int partitionId, final MeterRegistry registry) {
    partitionIdLabel = String.valueOf(partitionId);
    meterRegistry = registry;

    Gauge.builder(meterName("bulk.memory.size"), bulkMemorySize, AtomicInteger::get)
        .tags(PARTITION_LABEL, partitionIdLabel)
        .description("Exporter bulk memory size")
        .register(meterRegistry);

    flushDuration =
        Timer.builder(meterName("flush.duration.seconds"))
            .description("Flush duration of bulk exporters in seconds")
            .tags(PARTITION_LABEL, partitionIdLabel)
            .publishPercentileHistogram()
            .register(meterRegistry);

    bulkSize =
        DistributionSummary.builder(meterName("bulk.size"))
            .description("Exporter bulk size")
            .tags(PARTITION_LABEL, partitionIdLabel)
            .serviceLevelObjectives(10, 100, 1_000, 10_000, 100_000)
            .register(meterRegistry);

    failedFlush =
        Counter.builder(meterName("failed.flush"))
            .description("Number of failed flush operations")
            .tags(PARTITION_LABEL, partitionIdLabel)
            .register(meterRegistry);
  }

  public void measureFlushDuration(final Runnable flushFunction) {
    flushDuration.record(flushFunction);
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
