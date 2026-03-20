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

  private final CamundaArchiverMetrics archiverMetrics;

  /** time spent on counting process instances awaiting archiving */
  private final Timer processInstancesAwaitingArchivalTimer;

  private final Timer flushLatency;

  /** Count of incident updates that needed retrying. */
  private final Counter incidentUpdatesRetriesNeeded;

  /** Count of incident updates that were processed. */
  private final Counter incidentUpdatesProcessed;

  /** Count of document updated when incident updates were processed. */
  private final Counter incidentUpdatesDocumentsUpdated;

  private Timer.Sample flushLatencyMeasurement;
  private final DistributionSummary bulkSize;
  private final Counter bulkOperations;
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
    archiverMetrics = new CamundaArchiverMetrics(meterRegistry, this::meterName);

    processInstancesAwaitingArchivalTimer =
        Timer.builder(meterName("process.instances.awaiting.archival.request.duration"))
            .description(
                "Duration of how long it takes to get the count of process instances that need to be archived.")
            .tags("type", "search")
            .publishPercentileHistogram()
            .register(meterRegistry);

    flushLatency =
        Timer.builder(meterName("flush.latency"))
            .description(
                "Time of how long a export buffer is open and collects new records before flushing, meaning latency until the next flush is done.")
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
    bulkOperations =
        Counter.builder(meterName("bulk.operations"))
            .description(
                "Count of many secondary storage operations have been done via exporter bulk requests")
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

  public void measureProcessInstancesAwaitingArchivalDuration(final Timer.Sample sample) {
    sample.stop(processInstancesAwaitingArchivalTimer);
  }

  public CloseableSilently measureFlushDuration() {
    return MicrometerUtil.timer(flushDuration, Timer.start(meterRegistry));
  }

  public void recordBulkSize(final int bulkSize) {
    this.bulkSize.record(bulkSize);
  }

  public void recordBulkOperations(final int operations) {
    bulkOperations.increment(operations);
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

  public CamundaArchiverMetrics getArchiverMetrics() {
    return archiverMetrics;
  }

  @Override
  public void close() {
    // cleanup archiver metrics
    archiverMetrics.close();

    // clean up all registered meters
    meterRegistry.remove(processInstancesAwaitingArchivalTimer);
    meterRegistry.remove(flushLatency);

    meterRegistry.remove(incidentUpdatesRetriesNeeded);
    meterRegistry.remove(incidentUpdatesProcessed);
    meterRegistry.remove(incidentUpdatesDocumentsUpdated);

    meterRegistry.remove(bulkSize);
    meterRegistry.remove(bulkOperations);
    meterRegistry.remove(flushDuration);
    meterRegistry.remove(failedFlush);
    meterRegistry.remove(recordExportDuration);

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
