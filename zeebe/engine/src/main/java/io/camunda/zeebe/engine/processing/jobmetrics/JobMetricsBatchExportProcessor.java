/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.jobmetrics;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobMetricsState;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricState;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsValue;
import io.camunda.zeebe.engine.state.jobmetrics.JobWorkerCounters;
import io.camunda.zeebe.engine.state.jobmetrics.StatusMetric;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchValueRecord;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobWorkerCountersRecord;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for the JOB_METRICS_BATCH EXPORT command. Reads job metrics from the state and produces
 * an EXPORTED event containing all aggregated metrics.
 */
@ExcludeAuthorizationCheck
public class JobMetricsBatchExportProcessor implements TypedRecordProcessor<JobMetricsBatchRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(JobMetricsBatchExportProcessor.class);

  private final JobMetricsState jobMetricsState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final InstantSource clock;

  public JobMetricsBatchExportProcessor(
      final JobMetricsState jobMetricsState,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final InstantSource clock) {
    this.jobMetricsState = jobMetricsState;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<JobMetricsBatchRecord> command) {
    final JobMetricsBatchRecord exportedRecord = createExportedRecord(command.getValue());

    LOG.debug(
        "Exporting job metrics batch with {} entries, time range: {} - {}",
        exportedRecord.getMetricsByTypeAndTenant().size(),
        exportedRecord.getBatchStartTime(),
        exportedRecord.getBatchEndTime());

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), JobMetricsBatchIntent.EXPORTED, exportedRecord);
  }

  private JobMetricsBatchRecord createExportedRecord(final JobMetricsBatchRecord commandRecord) {
    final JobMetricsBatchRecord record = new JobMetricsBatchRecord();

    record.setBatchStartTime(clock.millis());

    // Iterate over all job metrics in the state and populate the record
    jobMetricsState.forEachJobMetrics(
        (key, metricsValue) -> {
          // Parse the key format: "jobType_tenantId"
          final String[] parts = key.split("_", 2);
          if (parts.length != 2) {
            LOG.warn("Invalid job metrics key format: {}", key);
            return;
          }

          final String jobType = parts[0];
          final String tenantId = parts[1];

          // Get or add encoded string indices
          final int jobTypeIndex = record.getOrAddEncodedStringIndex(jobType);
          final int tenantIdIndex = record.getOrAddEncodedStringIndex(tenantId);

          // Create the metrics value record
          final JobMetricsBatchValueRecord valueRecord =
              record.getOrCreateMetric(jobTypeIndex, tenantIdIndex);

          // Add job type level counters
          addJobTypeCounters(valueRecord, metricsValue);

          // Add worker level counters
          addWorkerCounters(valueRecord, metricsValue, record);
        });

    record.setBatchEndTime(clock.millis());
    return record;
  }

  private void addJobTypeCounters(
      final JobMetricsBatchValueRecord valueRecord, final JobMetricsValue metricsValue) {
    for (final JobMetricState state : JobMetricState.values()) {
      final StatusMetric stateMetric = metricsValue.getJobTypeCounter(state);
      if (stateMetric != null) {
        final var statusMetric =
            new io.camunda.zeebe.protocol.impl.record.value.jobmetrics.StatusMetric();
        statusMetric.setCount(stateMetric.getCount());
        statusMetric.setLastUpdatedAt(stateMetric.getLastUpdatedAt());
        valueRecord.addJobTypeCounter(statusMetric);
      }
    }
  }

  private void addWorkerCounters(
      final JobMetricsBatchValueRecord valueRecord,
      final JobMetricsValue metricsValue,
      final JobMetricsBatchRecord record) {
    final Map<String, JobWorkerCounters> workerCountersMap = metricsValue.getAllWorkerCounters();

    for (final Map.Entry<String, JobWorkerCounters> entry : workerCountersMap.entrySet()) {
      final String workerName = entry.getKey();
      final JobWorkerCounters workerCounters = entry.getValue();

      // Get or add encoded string index for worker name
      final int workerNameIndex = record.getOrAddEncodedStringIndex(workerName);

      final JobWorkerCountersRecord workerRecord = new JobWorkerCountersRecord();
      workerRecord.setWorkerNameIndex(workerNameIndex);

      // Add counters for each state
      for (final JobMetricState state : JobMetricState.values()) {
        final StatusMetric stateMetric = workerCounters.getCounter(state);
        if (stateMetric != null) {
          final var statusMetric =
              new io.camunda.zeebe.protocol.impl.record.value.jobmetrics.StatusMetric();
          statusMetric.setCount(stateMetric.getCount());
          statusMetric.setLastUpdatedAt(stateMetric.getLastUpdatedAt());
          workerRecord.addCounter(statusMetric);
        }
      }

      valueRecord.addWorkerCounters(workerRecord);
    }
  }
}
