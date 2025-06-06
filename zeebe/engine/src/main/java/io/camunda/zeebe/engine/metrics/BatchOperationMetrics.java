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
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.util.collection.Tuple;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.ResourceSample;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BatchOperationMetrics {

  private static final String ORGANIZATION_ID =
      System.getenv().getOrDefault("CAMUNDA_CLOUD_ORGANIZATION_ID", "null");

  final MeterRegistry registry;
  final int partitionId;

  private final Map<Tuple<BatchOperationAction, BatchOperationType>, Counter> executedActions =
      new HashMap<>();
  private final Map<Tuple<BatchOperationLatency, Long>, ResourceSample> latency = new HashMap<>();
  private Counter queryCounter;

  public BatchOperationMetrics(final MeterRegistry registry, final int partitionId) {
    this.registry = Objects.requireNonNull(registry, "must specify a registry");
    this.partitionId = partitionId;
  }

  public void recordCreated(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.CREATED, batchOperationType);
  }

  public void recordStarted(final BatchOperationType batchOperationType) {
    batchOperationEvent(BatchOperationAction.STARTED, batchOperationType);
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

  public void startTotalLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    createLatency(BatchOperationLatency.TOTAL_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopTotalLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.TOTAL_LATENCY, batchOperationKey);
  }

  public ResourceSample startTotalQueryLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    return createLatency(
        BatchOperationLatency.TOTAL_QUERY_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopQueryLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.TOTAL_QUERY_LATENCY, batchOperationKey);
  }

  public void startTotalExecutionLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    createLatency(
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
    createLatency(
        BatchOperationLatency.START_EXECUTE_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopStartExecuteLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.START_EXECUTE_LATENCY, batchOperationKey);
  }

  public void startExecuteCycleLatencyMeasure(
      final Long batchOperationKey, final BatchOperationType batchOperationType) {
    createLatency(
        BatchOperationLatency.EXECUTE_CYCLE_LATENCY, batchOperationKey, batchOperationType);
  }

  public void stopExecuteCycleLatencyMeasure(final Long batchOperationKey) {
    closeAndRemoveLatency(BatchOperationLatency.EXECUTE_CYCLE_LATENCY, batchOperationKey);
  }

  /**
   * Records a query against the secondary database, which is used for batch operations. This metric
   * is used to track the number of queries made against the secondary database.
   *
   * <p>Since in the scheduler we have no batch operation type, we cannot use the executedActions
   * map
   */
  public void recordQueryAgainstSecondaryDatabase() {
    if (queryCounter == null) {
      final var meterDoc = BatchOperationMetricsDoc.EXECUTED_QUERIES;
      queryCounter =
          Counter.builder(meterDoc.getName())
              .description(meterDoc.getDescription())
              .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
              .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
              .register(registry);
    }

    queryCounter.increment();
  }

  public void recordItemsPerPartition(
      final int itemsAmount,
      final long batchOperationKey,
      final BatchOperationType batchOperationType) {
    final var meterDoc = BatchOperationMetricsDoc.ITEMS_PER_PARTITION;
    DistributionSummary.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(
            BatchOperationKeyNames.BATCH_OPERATION_KEY.asString(),
            String.valueOf(batchOperationKey))
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(BatchOperationKeyNames.BATCH_OPERATION_TYPE.asString(), batchOperationType.toString())
        .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID)
        .serviceLevelObjectives(
            1,
            2,
            5,
            10,
            20,
            50,
            100,
            200,
            500,
            1_000,
            2_000,
            5_000,
            10_000,
            20_000,
            50_000,
            100_000,
            500_000,
            1_000_000,
            2_000_000,
            5_000_000,
            10_000_000)
        .register(registry)
        .record(itemsAmount);
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
   * @param batchOperationKey the key of the batch operation
   * @param batchOperationType the type of batch operation
   * @return the created or existing ResourceSample for the latency
   */
  private ResourceSample createLatency(
      final BatchOperationLatency latencyType,
      final Long batchOperationKey,
      final BatchOperationType batchOperationType) {
    return latency.computeIfAbsent(
        createLatencyKey(latencyType, batchOperationKey),
        (key) -> registerBatchOperationLatency(batchOperationKey, latencyType, batchOperationType));
  }

  /**
   * Closes the latency measurement and removes it from the map. If the latency measurement does not
   * exist, it does nothing.
   *
   * @param latency the latency type to close
   * @param batchOperationKey the key of the batch operation
   */
  private void closeAndRemoveLatency(
      final BatchOperationLatency latency, final Long batchOperationKey) {
    final var key = createLatencyKey(latency, batchOperationKey);
    if (this.latency.containsKey(key)) {
      this.latency.get(key).close();
      this.latency.remove(key);
    }
  }

  private static Tuple<BatchOperationLatency, Long> createLatencyKey(
      final BatchOperationLatency latency, final Long batchOperationKey) {
    return Tuple.of(latency, batchOperationKey);
  }

  private ResourceSample registerBatchOperationLatency(
      final Long batchOperationKey,
      final BatchOperationLatency batchOperationLatency,
      final BatchOperationType batchOperationType) {
    final var meterDoc = BatchOperationMetricsDoc.BATCH_OPERATION_LATENCY;
    return Timer.resource(registry, meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(
            BatchOperationKeyNames.BATCH_OPERATION_KEY.asString(),
            String.valueOf(batchOperationKey))
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(BatchOperationKeyNames.LATENCY.asString(), batchOperationLatency.toString())
        .tag(BatchOperationKeyNames.BATCH_OPERATION_TYPE.asString(), batchOperationType.toString())
        .tag(BatchOperationKeyNames.ORGANIZATION_ID.asString(), ORGANIZATION_ID);
  }
}
