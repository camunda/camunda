/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.metrics;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CamundaExporterMetrics {
  private static final String NAMESPACE = "zeebe.camunda.exporter";

  private final MeterRegistry meterRegistry;
  private final InstantSource streamClock;

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
  private final DistributionSummary bulkSize;
  private final Timer flushDuration;
  private final Counter failedFlush;
  private final Timer recordExportDuration;

  private final AtomicReference<Instant> lastFlushTime = new AtomicReference<>(Instant.now());
  private final AtomicInteger processInstancesAwaitingArchival = new AtomicInteger(0);

  public CamundaExporterMetrics(final MeterRegistry meterRegistry) {
    this(meterRegistry, InstantSource.system());
  }

  public CamundaExporterMetrics(
      final MeterRegistry meterRegistry, final InstantSource streamClock) {
    this.meterRegistry = meterRegistry;
    this.streamClock = streamClock;

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
    bulkSize =
        DistributionSummary.builder(meterName("bulk.size"))
            .description("How many items were exported in one bulk request")
            .serviceLevelObjectives(10, 100, 1_000, 10_000, 100_000)
            .register(meterRegistry);
    flushDuration =
        Timer.builder(meterName("flush.duration.seconds"))
            .description("Flush duration of bulk exporters in seconds")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .register(meterRegistry);
    failedFlush =
        Counter.builder(meterName("failed.flush"))
            .description("Number of failed flush operations")
            .register(meterRegistry);
    recordExportDuration =
        Timer.builder(meterName("record.export.duration"))
            .description(
                "How much time it took to export a record from the moment it was written (not committed)")
            .serviceLevelObjectives(MicrometerUtil.defaultPrometheusBuckets())
            .register(meterRegistry);

    Gauge.builder(meterName("since.last.flush.seconds"), this::secondSinceLastFlush)
        .description("Time in seconds since the last successful flush")
        .register(meterRegistry);

    Gauge.builder(
            meterName("process.instances.awaiting.archival"),
            processInstancesAwaitingArchival,
            AtomicInteger::get)
        .description("Number of process instances awaiting archival (approximate)")
        .register(meterRegistry);
  }

  public CloseableSilently measureFlushDuration() {
    return MicrometerUtil.timer(flushDuration, Timer.start(meterRegistry));
  }

  public void measureArchiverSearch(final Timer.Sample sample) {
    sample.stop(archiverSearchTimer);
  }

  public void recordBulkSize(final int bulkSize) {
    this.bulkSize.record(bulkSize);
  }

  public void recordFailedFlush() {
    failedFlush.increment();
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

  public void recordFlushFailureType(final String failureType) {
    meterRegistry.counter(meterName("flush.failure.type"), "failure_type", failureType).increment();
  }

  private double secondSinceLastFlush() {
    return Duration.between(lastFlushTime.get(), Instant.now()).toMillis() / 1000.0;
  }

  public void recordFlushOccurrence(final Instant time) {
    lastFlushTime.set(time);
  }

  public void addToProcessInstancesAwaitingArchival(final int count) {
    processInstancesAwaitingArchival.addAndGet(count);
  }

  /**
   * For each record write timestamp, observes the export latency by subtracting the timestamp from
   * the current stream clock.
   *
   * @param recordTimestamps a collection of {@link Record#getTimestamp()}
   */
  public void observeRecordExportLatencies(final Collection<Long> recordTimestamps) {
    final var now = streamClock.millis();
    recordTimestamps.stream()
        .mapToLong(timestamp -> now - timestamp)
        .forEach(duration -> recordExportDuration.record(duration, TimeUnit.MILLISECONDS));
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
