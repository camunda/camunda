/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.batchoperation.BatchOperationItemProvider.Item;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private final Duration pollingInterval;
  private final int chunkSize;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final BatchOperationItemProvider entityKeyProvider;
  private final int partitionId;

  /** Marks if this scheduler is currently executing or not. */
  private final AtomicBoolean executing = new AtomicBoolean(false);

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final BatchOperationItemProvider entityKeyProvider,
      final EngineConfiguration engineConfiguration,
      final int partitionId) {
    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    this.entityKeyProvider = entityKeyProvider;
    pollingInterval = engineConfiguration.getBatchOperationSchedulerInterval();
    chunkSize = engineConfiguration.getBatchOperationChunkSize();
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
    if (batchOperation.isPaused()) {
      LOG.trace("Batch operation {} is paused.", batchOperation.getKey());
      return;
    }

    // First fire a start event
    appendStartedCommand(taskResultBuilder, batchOperation);

    try {
      // Then append the chunks
      final var keys = queryAllKeys(batchOperation);
      for (int i = 0; i < keys.size(); i += chunkSize) {
        final Set<Item> chunkKeys =
            keys.stream().skip(i).limit(chunkSize).collect(Collectors.toSet());
        appendChunk(batchOperation.getKey(), taskResultBuilder, chunkKeys);
      }

      appendExecution(batchOperation.getKey(), taskResultBuilder);
    } catch (final Exception e) {
      LOG.error(
          "Failed to append chunks for batch operation with key {}. It will be removed from queue",
          batchOperation.getKey(),
          e);
      appendFailedCommand(taskResultBuilder, batchOperation);
    }
  }

  private void appendStartedCommand(
      final TaskResultBuilder taskResultBuilder, final PersistedBatchOperation batchOperation) {
    final var batchOperationKey = batchOperation.getKey();
    final var command = new BatchOperationCreationRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setBatchOperationType(batchOperation.getBatchOperationType());
    LOG.debug("Appending batch operation {} started event", batchOperationKey);
    taskResultBuilder.appendCommandRecord(batchOperationKey, BatchOperationIntent.START, command);
  }

  private void appendFailedCommand(
      final TaskResultBuilder taskResultBuilder, final PersistedBatchOperation batchOperation) {
    final var batchOperationKey = batchOperation.getKey();
    final var command = new BatchOperationCreationRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setBatchOperationType(batchOperation.getBatchOperationType());
    LOG.debug("Appending batch operation {} failed event", batchOperationKey);
    taskResultBuilder.appendCommandRecord(batchOperationKey, BatchOperationIntent.FAIL, command);
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

    LOG.debug(
        "Appending batch operation {} subbatch with {} items.", batchOperationKey, items.size());
    taskResultBuilder.appendCommandRecord(
        batchOperationKey, BatchOperationChunkIntent.CREATE, command);
  }

  private void appendExecution(
      final Long batchOperationKey, final TaskResultBuilder taskResultBuilder) {
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperationKey);

    LOG.debug("Appending batch operation execution {}", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey, BatchOperationExecutionIntent.EXECUTE, command, batchOperationKey);
  }

  private Set<Item> queryAllKeys(final PersistedBatchOperation batchOperation) {
    final Supplier<Boolean> abortCondition =
        () -> !batchOperationState.exists(batchOperation.getKey());

    return switch (batchOperation.getBatchOperationType()) {
      case CANCEL_PROCESS_INSTANCE, MIGRATE_PROCESS_INSTANCE, MODIFY_PROCESS_INSTANCE ->
          entityKeyProvider.fetchProcessInstanceItems(
              partitionId,
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication(),
              abortCondition);
      case RESOLVE_INCIDENT ->
          entityKeyProvider.fetchIncidentItems(
              partitionId,
              batchOperation.getEntityFilter(ProcessInstanceFilter.class),
              batchOperation.getAuthentication(),
              abortCondition);
      default ->
          throw new IllegalArgumentException(
              "Unexpected batch operation type: " + batchOperation.getBatchOperationType());
    };
  }
}
