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
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CamundaExporterMetrics implements AutoCloseable {
  private static final String NAMESPACE = "zeebe.camunda.exporter";

  private final MeterRegistry meterRegistry;
  private final InstantSource streamClock;

  private final Timer flushLatency;

  /** Count of completed process instances that are in progress of archiving. */
  private final Counter processInstancesArchiving;

  /** Count of completed process instances that have been archived. */
  private final Counter processInstancesArchived;

  /** Count of completed batch operations that are in progress of archiving. */
  private final Counter batchOperationsArchiving;

  /** Count of completed batch operations that have been archived. */
  private final Counter batchOperationsArchived;

  /** Count of usage-metrics that are in progress of archiving. */
  private final Counter usageMetricsArchiving;

  /** Count of usage-metrics that have been archived. */
  private final Counter usageMetricsArchived;

  /** Count of usage-metrics-task-users that are in progress of archiving. */
  private final Counter usageMetricsTUArchiving;

  /** Count of usage-metrics-task-users that have been archived. */
  private final Counter usageMetricsTUArchived;

  /** Count of standalone-decisions that are in progress of archiving. */
  private final Counter standaloneDecisionsArchiving;

  /** Count of standalone-decisions that have been archived. */
  private final Counter standaloneDecisionsArchived;

  /** Count of job-batch-metrics that are in progress of archiving. */
  private final Counter jobBatchMetricsArchiving;

  /** Count of job-batch-metrics that have been archived. */
  private final Counter jobBatchMetricsArchived;

  /** Count of incident updates that needed retrying. */
  private final Counter incidentUpdatesRetriesNeeded;

  /** Count of incident updates that were processed. */
  private final Counter incidentUpdatesProcessed;

  /** Count of document updated when incident updates were processed. */
  private final Counter incidentUpdatesDocumentsUpdated;

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
    usageMetricsArchived =
        Counter.builder(meterName("archiver.usage.metrics"))
            .tag("state", "archived")
            .description("Count of completed usage-metrics, that have been archived.")
            .register(meterRegistry);
    usageMetricsArchiving =
        Counter.builder(meterName("archiver.usage.metrics"))
            .tag("state", "archiving")
            .description(
                "Count of completed usage-metrics that have been found, and are now in progress of archiving.")
            .register(meterRegistry);
    usageMetricsTUArchived =
        Counter.builder(meterName("archiver.usage.metrics.tu"))
            .tag("state", "archived")
            .description("Count of completed usage-metrics-task-users, that have been archived.")
            .register(meterRegistry);
    usageMetricsTUArchiving =
        Counter.builder(meterName("archiver.usage.metrics.tu"))
            .tag("state", "archiving")
            .description(
                "Count of completed usage-metrics-task-users that have been found, and are now in progress of archiving.")
            .register(meterRegistry);
    standaloneDecisionsArchived =
        Counter.builder(meterName("archiver.standalone.decisions"))
            .tag("state", "archived")
            .description("Count of completed standalone-decisions, that have been archived.")
            .register(meterRegistry);
    standaloneDecisionsArchiving =
        Counter.builder(meterName("archiver.standalone.decisions"))
            .tag("state", "archiving")
            .description(
                "Count of completed standalone-decisions that have been found, and are now in progress of archiving.")
            .register(meterRegistry);
    jobBatchMetricsArchived =
        Counter.builder(meterName("archiver.job.batch.metrics"))
            .tag("state", "archived")
            .description("Count of completed job-batch-metrics, that have been archived.")
            .register(meterRegistry);
    jobBatchMetricsArchiving =
        Counter.builder(meterName("archiver.job.batch.metrics"))
            .tag("state", "archiving")
            .description(
                "Count of completed job-batch-metrics that have been found, and are now in progress of archiving.")
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
    incidentUpdatesRetriesNeeded =
        Counter.builder(meterName("incident.updates"))
            .tag("action", "retry")
            .description(
                "Count of incidents that have will need retrying due to matching process instances not being found.")
            .register(meterRegistry);
    incidentUpdatesProcessed =
        Counter.builder(meterName("incident.updates"))
            .tag("action", "processed")
            .description("Count of incidents that have been processed.")
            .register(meterRegistry);
    incidentUpdatesDocumentsUpdated =
        Counter.builder(meterName("incident.updates.documents"))
            .tag("action", "updated")
            .description("Count of documents that were updated when incidents were processed.")
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

    TimeGauge.builder(
            meterName("since.last.flush.seconds"), this::secondSinceLastFlush, TimeUnit.SECONDS)
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

  public void recordUsageMetricsArchived(final int count) {
    usageMetricsArchived.increment(count);
  }

  public void recordUsageMetricsArchiving(final int count) {
    usageMetricsArchiving.increment(count);
  }

  public void recordUsageMetricsTUArchived(final int count) {
    usageMetricsTUArchived.increment(count);
  }

  public void recordUsageMetricsTUArchiving(final int count) {
    usageMetricsTUArchiving.increment(count);
  }

  public void recordStandaloneDecisionsArchived(final int count) {
    standaloneDecisionsArchived.increment(count);
  }

  public void recordStandaloneDecisionsArchiving(final int count) {
    standaloneDecisionsArchiving.increment(count);
  }

  public void recordJobBatchMetricsArchived(final int count) {
    jobBatchMetricsArchived.increment(count);
  }

  public void recordJobBatchMetricsArchiving(final int count) {
    jobBatchMetricsArchiving.increment(count);
  }

  public void recordIncidentUpdatesRetriesNeeded(final int count) {
    incidentUpdatesRetriesNeeded.increment(count);
  }

  public void recordIncidentUpdatesProcessed(final int count) {
    incidentUpdatesProcessed.increment(count);
  }

  public void recordIncidentUpdatesDocumentsUpdated(final int count) {
    incidentUpdatesDocumentsUpdated.increment(count);
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

  public void setProcessInstancesAwaitingArchival(final int count) {
    processInstancesAwaitingArchival.set(count);
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

  @Override
  public void close() {
    // clean up all registered meters
    meterRegistry.remove(flushLatency);
    meterRegistry.remove(processInstancesArchived);
    meterRegistry.remove(processInstancesArchiving);
    meterRegistry.remove(batchOperationsArchived);
    meterRegistry.remove(batchOperationsArchiving);
    meterRegistry.remove(archiverSearchTimer);
    meterRegistry.remove(archiverDeleteTimer);
    meterRegistry.remove(archiverReindexTimer);
    meterRegistry.remove(archivingDuration);
    meterRegistry.remove(bulkSize);
    meterRegistry.remove(flushDuration);
    meterRegistry.remove(failedFlush);
    meterRegistry.remove(recordExportDuration);
    meterRegistry.remove(incidentUpdatesRetriesNeeded);
    meterRegistry.remove(incidentUpdatesProcessed);
    meterRegistry.remove(incidentUpdatesDocumentsUpdated);
    meterRegistry.remove(jobBatchMetricsArchiving);
    meterRegistry.remove(jobBatchMetricsArchived);

    // Remove custom gauges by their names if needed
    removeGaugeIfExists(meterName("since.last.flush.seconds"));
    removeGaugeIfExists(meterName("process.instances.awaiting.archival"));
  }

  private void removeGaugeIfExists(final String meterName) {
    final var gauge = meterRegistry.find(meterName).gauge();
    if (gauge != null) {
      meterRegistry.remove(gauge);
    }
  }
}
