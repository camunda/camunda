/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The job metrics state over a {@link JobMetricsStore}: it enforces the size limits of a batch and
 * keeps the batch's start and end time and whether the limits truncated it. The strings of the
 * metrics are encoded as integers only when the batch is read for the export, in the order they
 * first appear, so the stores don't have to know the encoding.
 */
public final class JobMetricsStateImpl implements MutableJobMetricsState {

  /** Metadata key: flag indicating if size limit was exceeded (1 = true, 0 = false) */
  public static final String META_SIZE_LIMITS_EXCEEDED = "__size_limits_exceeded__";

  public static final String META_BATCH_STARTING_TIME = "__batch_starting_time__";
  public static final String META_BATCH_ENDING_TIME = "__batch_ending_time__";

  private final JobMetricsStore store;
  private final InstantSource clock;
  private final int maxJobTypeLength;
  private final int maxTenantIdLength;
  private final int maxWorkerNameLength;
  private final int maxUniqueJobMetricsKeys;

  public JobMetricsStateImpl(
      final JobMetricsStore store,
      final InstantSource clock,
      final EngineConfiguration engineConfiguration) {
    this.store = store;
    this.clock = clock;
    maxJobTypeLength = engineConfiguration.getJobMetricsMaxTypeLength();
    maxTenantIdLength = engineConfiguration.getJobMetricsMaxTenantIdLength();
    maxWorkerNameLength = engineConfiguration.getJobMetricsMaxWorkerNameLength();
    maxUniqueJobMetricsKeys = engineConfiguration.getJobMetricsMaxUniqueKeys();
  }

  @Override
  public void forEach(final MetricsConsumer consumer) {
    final Map<String, Integer> encoding = new LinkedHashMap<>();
    store.forEach(
        (jobType, tenantId, workerName, metrics) ->
            consumer.accept(
                encode(encoding, jobType),
                encode(encoding, tenantId),
                encode(encoding, workerName),
                metrics));
  }

  @Override
  public List<String> getEncodedStrings() {
    final Map<String, Integer> encoding = new LinkedHashMap<>();
    store.forEach(
        (jobType, tenantId, workerName, metrics) -> {
          encode(encoding, jobType);
          encode(encoding, tenantId);
          encode(encoding, workerName);
        });
    return new ArrayList<>(encoding.keySet());
  }

  @Override
  public long getMetadata(final String key) {
    return store.getMetadata(key);
  }

  @Override
  public boolean isIncompleteBatch() {
    return getMetadata(META_SIZE_LIMITS_EXCEEDED) == 1L;
  }

  /**
   * Increments the job metrics for the given combination of job type, tenant ID, worker name,
   * unless the batch can't take it, which marks the batch as truncated.
   *
   * @param status the job status to increment
   */
  @Override
  public void incrementMetric(final JobRecord jobRecord, final JobMetricsExportState status) {
    final var jobType = jobRecord.getType();
    final var tenantId = jobRecord.getTenantId();
    final var workerName = jobRecord.getWorker();

    if (sizeLimitsExceeded(jobType, tenantId, workerName)) {
      // Would exceed threshold, mark as truncated and skip
      store.setMetadata(META_SIZE_LIMITS_EXCEEDED, 1L);
      return;
    }

    // Safe to proceed, increment the metric
    final long currentEpochMillis = clock.millis();
    store.increment(jobType, tenantId, workerName, status, currentEpochMillis);

    if (store.countKeys() == 1) {
      // First entry, set batch starting time
      store.setMetadata(META_BATCH_STARTING_TIME, currentEpochMillis);
    }
    store.setMetadata(META_BATCH_ENDING_TIME, currentEpochMillis);
  }

  @Override
  public void cleanUp() {
    store.deleteMetrics();

    store.setMetadata(META_SIZE_LIMITS_EXCEEDED, 0L);
    store.setMetadata(META_BATCH_ENDING_TIME, -1L);
    store.setMetadata(META_BATCH_STARTING_TIME, -1L);
  }

  private boolean sizeLimitsExceeded(
      final String jobType, final String tenantId, final String workerName) {
    return jobType.length() > maxJobTypeLength
        || tenantId.length() > maxTenantIdLength
        || workerName.length() > maxWorkerNameLength
        || uniqueKeysLimitExceeded(jobType, tenantId, workerName);
  }

  private boolean uniqueKeysLimitExceeded(
      final String jobType, final String tenantId, final String workerName) {
    return store.countKeys() + 1 > maxUniqueJobMetricsKeys
        && !store.containsKey(jobType, tenantId, workerName);
  }

  private static int encode(final Map<String, Integer> encoding, final String string) {
    final Integer existing = encoding.get(string);
    if (existing != null) {
      return existing;
    }
    final int index = encoding.size();
    encoding.put(string, index);
    return index;
  }
}
