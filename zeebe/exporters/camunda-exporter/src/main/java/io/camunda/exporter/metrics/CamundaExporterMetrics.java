/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.ResourceSample;
import io.micrometer.core.instrument.Timer.Sample;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class CamundaExporterMetrics {
  private static final String NAMESPACE = "zeebe.camunda.exporter";

  private final MeterRegistry meterRegistry;
  private final AtomicInteger bulkMemorySize = new AtomicInteger(0);
  private final Timer flushLatency;
  private final Counter processInstancesArchived;

  /**
   * Count of completed process instances that have been found, and are now in progress of
   * archiving.
   */
  private final Counter processInstancesArchiving;

  /**
   * Count of completed batch operations that have been found, and are now in progress of archiving.
   */
  private final Counter batchOperationsArchiving;

  private final Counter batchOperationsArchived;
  private final Timer archiverSearchTimer;
  private final Timer archiverDeleteTimer;
  private final Timer archiverReindexTimer;
  private Timer.Sample flushLatencyMeasurement;
  private final Timer archivingDuration;

  public CamundaExporterMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    flushLatency =
        Timer.builder(meterName("flush.latency"))
            .description(
                "Time of how long a export buffer is open and collects new records before flushing, meaning latency until the next flush is done.")
            .publishPercentileHistogram()
            .register(meterRegistry);

    processInstancesArchived =
        Counter.builder(meterName("archiver.process.instances"))
            .tag("state", "archived")
            .description("Count of completed process instances, that have been archived.")
            .register(meterRegistry);
    processInstancesArchiving =
        Counter.builder(meterName("archiver.process.instances"))
            .tag("state", "archiving")
            .description(
                "Count of completed process instances that have been found, and are now in progress of archiving.")
            .register(meterRegistry);
    batchOperationsArchived =
        Counter.builder(meterName("archiver.batch.operations"))
            .tag("state", "archived")
            .description("Count of completed batch operations, that have been archived.")
            .register(meterRegistry);
    batchOperationsArchiving =
        Counter.builder(meterName("archiver.batch.operations"))
            .tag("state", "archiving")
            .description(
                "Count of completed batch operations that have been found, and are now in progress of archiving.")
            .register(meterRegistry);
    archiverSearchTimer =
        Timer.builder(meterName("archiver.request.duration"))
            .description(
                "Duration of how long it takes to run the search request to resolve completed entities, that need to be archived.")
            .tags("type", "search")
            .publishPercentileHistogram()
            .register(meterRegistry);
    archiverDeleteTimer =
        Timer.builder(meterName("archiver.request.duration"))
            .description(
                "Duration of how long it takes to run the delete request to remove completed entities, from old indices.")
            .tags("type", "delete")
            .publishPercentileHistogram()
            .register(meterRegistry);
    archiverReindexTimer =
        Timer.builder(meterName("archiver.request.duration"))
            .description(
                "Duration of how long it takes to run the reindex request to copy over to the dated indices, from old indices.")
            .tags("type", "reindex")
            .publishPercentileHistogram()
            .register(meterRegistry);
    archivingDuration =
        Timer.builder(meterName("archiver.duration"))
            .description(
                "Duration of how long it takes from resolving to archiving entities, all in all together.")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  public ResourceSample measureFlushDuration() {
    return Timer.resource(meterRegistry, meterName("flush.duration.seconds"))
        .description("Flush duration of bulk exporters in seconds")
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public void measureArchiverSearch(final Timer.Sample sample) {
    sample.stop(archiverSearchTimer);
  }

  public void recordBulkSize(final int bulkSize) {
    DistributionSummary.builder(meterName("bulk.size"))
        .description("Exporter bulk size")
        .serviceLevelObjectives(10, 100, 1_000, 10_000, 100_000)
        .register(meterRegistry)
        .record(bulkSize);
  }

  public void recordBulkMemorySize(final int bulkMemorySize) {
    Gauge.builder(meterName("bulk.memory.size"), this.bulkMemorySize, AtomicInteger::get)
        .description("Exporter bulk memory size")
        .register(meterRegistry);

    this.bulkMemorySize.set(bulkMemorySize);
  }

  public void recordFailedFlush() {
    Counter.builder(meterName("failed.flush"))
        .description("Number of failed flush operations")
        .register(meterRegistry)
        .increment();
  }

  public void startFlushLatencyMeasurement() {
    flushLatencyMeasurement = Timer.start(meterRegistry);
  }

  public void stopFlushLatencyMeasurement() {
    if (flushLatencyMeasurement != null) {
      flushLatencyMeasurement.stop(flushLatency);
    }
  }

  public void recordProcessInstancesArchived(final int count) {
    processInstancesArchived.increment(count);
  }

  public void recordProcessInstancesArchiving(final int count) {
    processInstancesArchiving.increment(count);
  }

  public void recordBatchOperationsArchived(final int count) {
    batchOperationsArchived.increment(count);
  }

  public void recordBatchOperationsArchiving(final int count) {
    batchOperationsArchiving.increment(count);
  }

  private String meterName(final String name) {
    return NAMESPACE + "." + name;
  }

  public void measureArchiverDelete(final Sample timer) {
    timer.stop(archiverDeleteTimer);
  }

  public void measureArchiverReindex(final Sample timer) {
    timer.stop(archiverReindexTimer);
  }

  public void measureArchivingDuration(final Sample timer) {
    timer.stop(archivingDuration);
  }
}
