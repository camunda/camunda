/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.BatchOperationMetricsDoc.BatchOperationAction;
import io.camunda.zeebe.engine.metrics.BatchOperationMetricsDoc.BatchOperationKeyNames;
import io.camunda.zeebe.engine.metrics.BatchOperationMetricsDoc.BatchOperationLatency;
import io.camunda.zeebe.engine.metrics.BatchOperationMetricsDoc.QueryStatus;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.util.collection.Tuple;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.ResourceSample;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class BatchOperationMetrics {

  private static final String ORGANIZATION_ID =
      System.getenv().getOrDefault("CAMUNDA_CLOUD_ORGANIZATION_ID", "null");

  final MeterRegistry registry;
  final int partitionId;

  private final Map<Tuple<BatchOperationAction, BatchOperationType>, Counter> executedActions =
      new ConcurrentHashMap<>();
  private final Map<Tuple<BatchOperationLatency, Long>, ResourceSample> latency =
      new ConcurrentHashMap<>();
  private final Map<Long, ResourceSample> duration = new ConcurrentHashMap<>();
  private Counter queryCounter;

  public BatchOperationMetrics(final MeterRegistry registry, final int partitionId) {
    this.registry = Objects.requireNonNull(registry, "must specify a registry");
    this.partitionId = partitionId;
  }

  public void recordCreated(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.CREATED, batchOperationType);
  }

  public void recordInitialized(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.INITIALIZED, batchOperationType);
  }

  public void recordFailed(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.FAILED, batchOperationType);
  }

  public void recordChunkCreated(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.CHUNK_CREATED, batchOperationType);
  }

  public void recordExecuted(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.EXECUTED, batchOperationType);
  }

  public void recordCancelled(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.CANCELLED, batchOperationType);
  }

  public void recordSuspended(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.SUSPENDED, batchOperationType);
  }

  public void recordResumed(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.RESUMED, batchOperationType);
  }

  public void recordCompleted(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.COMPLETED, batchOperationType);
  }

  public void startTotalDurationMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    createResourceSampleForDuration(batchOperationKey, batchOperationType);
  }

  public void stopTotalDurationMeasure(final Long batchOperationKey) {
    closeAndRemoveDuration(batchOperationKey);
  }

  public ResourceSample startTotalQueryLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    return createResourceSampleForLatency(
        BatchOperationLatency.TOTAL_QUERY_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopQueryLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.TOTAL_QUERY_LATENCY, batchOperationKey);
  }

  public void startTotalExecutionLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    createResourceSampleForLatency(
        BatchOperationLatency.TOTAL_EXECUTION_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopTotalExecutionLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.TOTAL_EXECUTION_LATENCY, batchOperationKey);
  }

  /**
   * Measures the time from the creation of the first execution command until the first execution in
   * the processor
   *
   * @param batchOperationKey
   * @param batchOperationType
   */
  public void startStartExecuteLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    createResourceSampleForLatency(
        BatchOperationLatency.START_EXECUTE_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopStartExecuteLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.START_EXECUTE_LATENCY, batchOperationKey);
  }

  public void startExecuteCycleLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    createResourceSampleForLatency(
        BatchOperationLatency.EXECUTE_CYCLE_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopExecuteCycleLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.EXECUTE_CYCLE_LATENCY, batchOperationKey);
  }

  /**
   * Records a successful query against the secondary database, which is used for batch operations.
   * This metric is used to track the number of queries made against the secondary database.
   *
   * <p>Since in the scheduler we have no batch operation type, we cannot use the executedActions
   * map
   */
  public void recordQueryAgainstSecondaryDatabase() {
    recordQuery(QueryStatus.COMPLETED);
  }

  /**
   * Records a failed query against the secondary database, which is used for batch operations. This
   * metric is used to track the number of failed queries made against the secondary database.
   *
   * <p>Since in the scheduler we have no batch operation type, we cannot use the executedActions
   * map
   */
  public void recordFailedQueryAgainstSecondaryDatabase() {
    recordQuery(QueryStatus.FAILED);
  }

  public void recordItemsPerPartition(
      final int itemsAmount, final BatchOperationType batchOperationType) {
    final var meterDoc = BatchOperationMetricsDoc.ITEMS_PER_PARTITION;
    DistributionSummary.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(BatchOperationKeyNames.BATCH_OPERATION_TYPE.asString(), batchOperationType.toString())
        .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
        .serviceLevelObjectives(meterDoc.getDistributionSLOs())
        .register(registry)
        .record(itemsAmount);
  }

  private synchronized void recordQuery(final QueryStatus queryStatus) {
    if (queryCounter == null) {
      final var meterDoc = BatchOperationMetricsDoc.EXECUTED_QUERIES;
      queryCounter =
          Counter.builder(meterDoc.getName())
              .description(meterDoc.getDescription())
              .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
              .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
              .tag(BatchOperationKeyNames.QUERY_STATUS.asString(), queryStatus.getLabel())
              .register(registry);
    }

    queryCounter.increment();
  }

  private void batchOperationEvent(
      final BatchOperationAction action, final BatchOperationType batchOperationType) {
    executedActions
        .computeIfAbsent(
            Tuple.of(action, batchOperationType),
            (key) -> registerBatchOperationEventCounter(action, batchOperationType))
        .increment();
  }

  private Counter registerBatchOperationEventCounter(
      final BatchOperationAction batchOperationAction,
      final BatchOperationType batchOperationType) {
    final var meterDoc = BatchOperationMetricsDoc.EXECUTED_LIFECYCLE_EVENTS;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(BatchOperationKeyNames.BATCH_OPERATION_TYPE.asString(), batchOperationType.toString())
        .tag(BatchOperationKeyNames.ACTION.asString(), batchOperationAction.toString())
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
        .register(registry);
  }

  /**
   * Creates a latency measurement for the given latency type and batch operation key. If the
   * latency measurement already exists, it returns the existing one.
   *
   * @param latencyType the type of latency to create
   * @param batchOperationType the type of batch operation
   * @return the created or existing ResourceSample for the latency
   */
  private ResourceSample createResourceSampleForLatency(
      final BatchOperationLatency latencyType,
      final Long batchOperationKey,
      final BatchOperationType batchOperationType) {
    return latency.computeIfAbsent(
        createLatencyKey(latencyType, batchOperationKey),
        (key) -> registerBatchOperationExecutionLatency(latencyType, batchOperationType));
  }

  /**
   * Creates a duration measurement for the given latency type and batch operation key. If the
   * duration measurement already exists, it returns the existing one.
   *
   * @param batchOperationKey the key of the batch operation
   * @param batchOperationType the type of batch operation
   * @return the created or existing ResourceSample for the latency
   */
  private ResourceSample createResourceSampleForDuration(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    return duration.computeIfAbsent(
        batchOperationKey, (key) -> registerBatchOperationDuration(batchOperationType));
  }

  /**
   * Closes the latency measurement and removes it from the map. If the latency measurement does not
   * exist, it does nothing.
   *
   * @param latencyType the latency type to close
   * @param batchOperationKey the key of the batch operation
   */
  private void closeAndRemoveLatency(
      final BatchOperationLatency latencyType, final Long batchOperationKey) {
    final var key = createLatencyKey(latencyType, batchOperationKey);
    if (latency.containsKey(key)) {
      latency.get(key).close();
      latency.remove(key);
    }
  }

  /**
   * Closes the duration measurement and removes it from the map. If the duration measurement does
   * not exist, it does nothing.
   *
   * @param batchOperationKey the key of the batch operation
   */
  private void closeAndRemoveDuration(final Long batchOperationKey) {
    if (duration.containsKey(batchOperationKey)) {
      duration.get(batchOperationKey).close();
      duration.remove(batchOperationKey);
    }
  }

  private static Tuple<BatchOperationLatency, Long> createLatencyKey(
      final BatchOperationLatency latency, final Long batchOperationKey) {
    return Tuple.of(latency, batchOperationKey);
  }

  private ResourceSample registerBatchOperationExecutionLatency(
      final BatchOperationLatency batchOperationLatency,
      final BatchOperationType batchOperationType) {
    final var meterDoc = BatchOperationMetricsDoc.EXECUTION_LATENCY;
    return Timer.resource(registry, meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .minimumExpectedValue(Duration.ofMillis(10))
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(BatchOperationKeyNames.LATENCY.asString(), batchOperationLatency.toString())
        .tag(BatchOperationKeyNames.BATCH_OPERATION_TYPE.asString(), batchOperationType.toString())
        .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID);
  }

  private ResourceSample registerBatchOperationDuration(
      final BatchOperationType batchOperationType) {
    final var meterDoc = BatchOperationMetricsDoc.BATCH_OPERATION_DURATION;
    return Timer.resource(registry, meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .minimumExpectedValue(Duration.ofMillis(10))
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(BatchOperationKeyNames.BATCH_OPERATION_TYPE.asString(), batchOperationType.toString())
        .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID);
  }
}
