/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.BatchOperationItemProvider.Item;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private final Duration pollingInterval;
  private final int chunkSize;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final BatchOperationItemProvider entityKeyProvider;
  private final BatchOperationMetrics metrics;
  private final int partitionId;

  /** Marks if this scheduler is currently executing or not. */
  private final AtomicBoolean executing = new AtomicBoolean(false);

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final BatchOperationItemProvider entityKeyProvider,
      final EngineConfiguration engineConfiguration,
      final int partitionId,
      final BatchOperationMetrics metrics) {
    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    this.entityKeyProvider = entityKeyProvider;
    pollingInterval = engineConfiguration.getBatchOperationSchedulerInterval();
    chunkSize = engineConfiguration.getBatchOperationChunkSize();
    this.metrics = metrics;
    this.partitionId = partitionId;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    scheduleExecution();
  }

  @Override
  public void onResumed() {
    scheduleExecution();
  }

  private void scheduleExecution() {
    if (!executing.get()) {
      processingContext
          .getScheduleService()
          .runDelayedAsync(pollingInterval, this::execute, AsyncTaskGroup.BATCH_OPERATIONS);
    } else {
      LOG.warn("Execution is already in progress, skipping scheduling.");
    }
  }

  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    try {
      LOG.trace("Looking for pending batch operations to execute (scheduled).");
      executing.set(true);
      batchOperationState.foreachPendingBatchOperation(
          bo -> executeBatchOperation(bo, taskResultBuilder));
      return taskResultBuilder.build();
    } finally {
      executing.set(false);
      scheduleExecution();
    }
  }

  private void executeBatchOperation(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    if (batchOperation.isSuspended()) {
      LOG.trace("Batch operation {} is suspended.", batchOperation.getKey());
      return;
    }

    // First fire a start event
    appendStartedCommand(taskResultBuilder, batchOperation);

    final Set<Item> keys;
    try {
      keys = queryAllKeys(batchOperation);
    } catch (final Exception e) {
      LOG.error(
          "Failed to query keys for batch operation with key {}. It will be removed from queue",
          batchOperation.getKey(),
          e);
      appendFailedCommand(
          taskResultBuilder, batchOperation, BatchOperationErrorType.QUERY_FAILED, e);
      return;
    }

    try {
      // Then append the chunks
      appendChunks(batchOperation, taskResultBuilder, keys);

      metrics.recordItemsPerPartition(keys.size(), batchOperation.getBatchOperationType());

      appendExecution(batchOperation.getKey(), taskResultBuilder);

      metrics.startStartExecuteLatencyMeasure(
          batchOperation.getKey(), batchOperation.getBatchOperationType());
      metrics.startTotalExecutionLatencyMeasure(
          batchOperation.getKey(), batchOperation.getBatchOperationType());
    } catch (final Exception e) {
      LOG.error(
          "Failed to append chunks for batch operation with key {}. It will be removed from queue",
          batchOperation.getKey(),
          e);
      appendFailedCommand(
          taskResultBuilder, batchOperation, BatchOperationErrorType.APPEND_CHUNKS_FAILED, e);
    }
  }

  private void appendChunks(
      final PersistedBatchOperation batchOperation,
      final TaskResultBuilder taskResultBuilder,
      final Set<Item> keys) {
    if (!keys.isEmpty()) {
      LOG.debug(
          "Found {} items for batch operation with key {} on partition {}.",
          keys.size(),
          batchOperation.getKey(),
          partitionId);
      for (int i = 0; i < keys.size(); i += chunkSize) {
        final Set<Item> chunkKeys =
            keys.stream().skip(i).limit(chunkSize).collect(Collectors.toSet());
        appendChunk(batchOperation.getKey(), taskResultBuilder, chunkKeys);
        metrics.recordChunkCreated(batchOperation.getBatchOperationType());
      }
    } else {
      LOG.debug(
          "No items found for batch operation with key {} on partition {}.",
          batchOperation.getKey(),
          partitionId);
    }
  }

  private void appendStartedCommand(
      final TaskResultBuilder taskResultBuilder, final PersistedBatchOperation batchOperation) {
    final var batchOperationKey = batchOperation.getKey();
    final var command = new BatchOperationCreationRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setBatchOperationType(batchOperation.getBatchOperationType());
    LOG.debug("Appending batch operation {} started event", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.START,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  private void appendFailedCommand(
      final TaskResultBuilder taskResultBuilder,
      final PersistedBatchOperation batchOperation,
      final BatchOperationErrorType errorType,
      final Exception exception) {
    final var batchOperationKey = batchOperation.getKey();
    final var command = new BatchOperationCreationRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setBatchOperationType(batchOperation.getBatchOperationType());

    final var error = new BatchOperationError();
    error.setType(errorType);
    error.setPartitionId(partitionId);
    error.setMessage(ExceptionUtils.getStackTrace(exception));
    command.setError(error);

    LOG.debug("Appending batch operation {} failed event", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.FAIL,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  private void appendChunk(
      final Long batchOperationKey,
      final TaskResultBuilder taskResultBuilder,
      final Set<Item> items) {
    final var command = new BatchOperationChunkRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setItems(
        items.stream()
            .map(
                i ->
                    new BatchOperationItem()
                        .setItemKey(i.itemKey())
                        .setProcessInstanceKey(i.processInstanceKey()))
            .collect(Collectors.toSet()));

    LOG.debug("Appending batch operation {} chunk with {} items.", batchOperationKey, items.size());
    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationChunkIntent.CREATE,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  private void appendExecution(
      final Long batchOperationKey, final TaskResultBuilder taskResultBuilder) {
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperationKey);

    LOG.debug("Appending batch operation execution {}", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationExecutionIntent.EXECUTE,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  private Set<Item> queryAllKeys(final PersistedBatchOperation batchOperation) {
    final Supplier<Boolean> abortCondition =
        () -> !batchOperationState.exists(batchOperation.getKey());

    try (final var ignored =
        metrics.startTotalQueryLatencyMeasure(
            batchOperation.getKey(), batchOperation.getBatchOperationType())) {
      return switch (batchOperation.getBatchOperationType()) {
        case CANCEL_PROCESS_INSTANCE ->
            entityKeyProvider.fetchProcessInstanceItems(
                partitionId,
                batchOperation.getEntityFilter(ProcessInstanceFilter.class).toBuilder()
                    .states(ProcessInstanceState.ACTIVE.name())
                    .parentProcessInstanceKeyOperations(Operation.exists(false))
                    .build(),
                batchOperation.getAuthentication(),
                abortCondition);
        case MIGRATE_PROCESS_INSTANCE, MODIFY_PROCESS_INSTANCE ->
            entityKeyProvider.fetchProcessInstanceItems(
                partitionId,
                batchOperation.getEntityFilter(ProcessInstanceFilter.class).toBuilder()
                    .states(ProcessInstanceState.ACTIVE.name())
                    .build(),
                batchOperation.getAuthentication(),
                abortCondition);
        case RESOLVE_INCIDENT ->
            entityKeyProvider.fetchIncidentItems(
                partitionId,
                batchOperation.getEntityFilter(ProcessInstanceFilter.class).toBuilder()
                    .states(ProcessInstanceState.ACTIVE.name())
                    .build(),
                batchOperation.getAuthentication(),
                abortCondition);
        default ->
            throw new IllegalArgumentException(
                "Unexpected batch operation type: " + batchOperation.getBatchOperationType());
      };
    } finally {
      metrics.stopQueryLatencyMeasure(batchOperation.getKey());
    }
  }
}
