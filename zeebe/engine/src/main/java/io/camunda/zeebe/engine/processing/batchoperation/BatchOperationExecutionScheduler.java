/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import com.google.common.collect.Lists;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.ItemPage;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The is class is a scheduler that periodically checks for newly created batch operations and
 * initializes them with the itemKeys to be executed.
 *
 * <p>For this, it deserializes the filter object and queries the EntityKeyProvider for all matching
 * itemKeys for this partition. Then this collection of itemKeys will be split into smaller chunks
 * and appended to the TaskResultBuilder as BatchOperationChunkRecord.
 */
public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {

  public static final String ERROR_MSG_FAILED_FIRST_CHUNK_APPEND =
      "Unable to append first chunk of batch operation items. Number of items: %d";
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private final Duration pollingInterval;
  private final int chunkSize;
  private final int queryPageSize;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final ItemProviderFactory itemProviderFactory;
  private final BatchOperationMetrics metrics;
  private final int partitionId;

  /** Marks if this scheduler is currently executing or not. */
  private final AtomicBoolean executing = new AtomicBoolean(false);

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final ItemProviderFactory itemProviderFactory,
      final EngineConfiguration engineConfiguration,
      final int partitionId,
      final BatchOperationMetrics metrics) {
    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    this.itemProviderFactory = itemProviderFactory;
    pollingInterval = engineConfiguration.getBatchOperationSchedulerInterval();
    chunkSize = engineConfiguration.getBatchOperationChunkSize();
    queryPageSize = engineConfiguration.getBatchOperationQueryPageSize();
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

  /** Schedules the next execution of the batch operation scheduler run. */
  private void scheduleExecution() {
    if (!executing.get()) {
      processingContext
          .getScheduleService()
          .runDelayedAsync(pollingInterval, this::execute, AsyncTaskGroup.BATCH_OPERATIONS);
    } else {
      LOG.warn("Execution is already in progress, skipping scheduling.");
    }
  }

  /**
   * Executes the next pending batch operation in the queue. If more than one batch operation is
   * pending, the following one will be executed in the next scheduled run.
   *
   * @param taskResultBuilder the task result builder to append the commands to
   * @return the task result containing the commands to be executed
   */
  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    try {
      LOG.trace("Looking for the next pending batch operation to execute (scheduled).");
      executing.set(true);
      batchOperationState
          .getNextPendingBatchOperation()
          .ifPresent(bo -> executeBatchOperation(bo, taskResultBuilder));
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

    // First fire a start event to indicate the beginning of the INIT phase
    appendStartedCommand(taskResultBuilder, batchOperation);

    final var itemProvider = itemProviderFactory.fromBatchOperation(batchOperation);

    String lastSearchResultCursor = batchOperation.getInitializationSearchCursor();
    int keysAdded = 0;
    ItemPage page = null;
    if (lastSearchResultCursor != null && lastSearchResultCursor.isEmpty()) {
      // If the cursor is empty, we need to initialize it with the first page
      lastSearchResultCursor = null;
    }
    while (page == null || !page.isLastPage()) {
      try {
        page = itemProvider.fetchItemPage(lastSearchResultCursor, queryPageSize);
      } catch (final Exception e) {
        LOG.error(
            "Failed to query keys for batch operation with key {}. It will be removed from queue",
            batchOperation.getKey(),
            e);
        appendQueryFailedCommand(
            taskResultBuilder, batchOperation, ExceptionUtils.getStackTrace(e));
        return;
      }

      // Then try to append the items to the batch operation
      final boolean appendedChunks = appendChunks(batchOperation, taskResultBuilder, page.items());
      if (!appendedChunks) {
        if (lastSearchResultCursor == null) {
          // If we failed to append even the first chunk, we need to fail the batch operation
          // directly. Otherwise, we would be stuck in an infinite loop
          LOG.error(
              "Failed to query keys for batch operation with key {}. It will be removed from queue",
              batchOperation.getKey());
          appendQueryFailedCommand(
              taskResultBuilder,
              batchOperation,
              String.format(ERROR_MSG_FAILED_FIRST_CHUNK_APPEND, page.items().size()));
          return;
        } else {
          // The RecordBatch is full, so we need another init run to continue
          appendInitializeCommand(
              taskResultBuilder, batchOperation.getKey(), lastSearchResultCursor);
          break;
        }
      } else {
        lastSearchResultCursor = page.endCursor();
        keysAdded += page.items().size();
        if (page.isLastPage()) {
          // If we have reached the last page, we can finalize the initialization and start the BO
          // we always append the EXECUTE command at the end, even if no items were found, so we can
          // leave the completion logic in that processor.
          appendExecution(batchOperation.getKey(), taskResultBuilder);
          metrics.recordItemsPerPartition(
              batchOperation.getNumTotalItems() + keysAdded,
              batchOperation.getBatchOperationType());
        }
      }
    }

    metrics.startStartExecuteLatencyMeasure(
        batchOperation.getKey(), batchOperation.getBatchOperationType());
    metrics.startTotalExecutionLatencyMeasure(
        batchOperation.getKey(), batchOperation.getBatchOperationType());
  }

  /**
   * THis method will create chunk records from the given items and append them to the
   * taskResultBuilder. This method is atomic. Either all chunks are appended or none.
   *
   * @param batchOperation the batch operation to which the chunks belong
   * @param taskResultBuilder the task result builder to append the chunks to
   * @param items the items to be chunked and appended
   * @return true if the chunks were successfully appended, false otherwise
   */
  private boolean appendChunks(
      final PersistedBatchOperation batchOperation,
      final TaskResultBuilder taskResultBuilder,
      final List<Item> items) {

    final var chunkRecords =
        Lists.partition(items, chunkSize).stream()
            .map(chunkItems -> createChunkRecord(batchOperation, chunkItems))
            .toList();

    final FollowUpCommandMetadata metadata =
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperation.getKey()));

    // we first ask the taskResultBuilder if we even can append the all the records
    final var canAppend = taskResultBuilder.canAppendRecords(chunkRecords, metadata);

    if (canAppend) {
      chunkRecords.forEach(
          command -> {
            LOG.trace(
                "Appending batch operation {} chunk with {} items.",
                batchOperation.getKey(),
                command.getItems().size());
            taskResultBuilder.appendCommandRecord(
                batchOperation.getKey(), BatchOperationChunkIntent.CREATE, command, metadata);
            metrics.recordChunkCreated(batchOperation.getBatchOperationType());
          });
      return true;
    } else {
      return false;
    }
  }

  private static BatchOperationChunkRecord createChunkRecord(
      final PersistedBatchOperation batchOperation, final List<Item> chunkItems) {
    final var command = new BatchOperationChunkRecord();
    command.setBatchOperationKey(batchOperation.getKey());
    command.setItems(
        chunkItems.stream().map(BatchOperationExecutionScheduler::map).collect(Collectors.toSet()));
    return command;
  }

  private static BatchOperationItem map(final Item i) {
    return new BatchOperationItem()
        .setItemKey(i.itemKey())
        .setProcessInstanceKey(i.processInstanceKey());
  }

  private void appendStartedCommand(
      final TaskResultBuilder taskResultBuilder, final PersistedBatchOperation batchOperation) {
    final var batchOperationKey = batchOperation.getKey();
    final var command = new BatchOperationCreationRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setBatchOperationType(batchOperation.getBatchOperationType());
    LOG.trace("Appending batch operation {} started event", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.START,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  private void appendInitializeCommand(
      final TaskResultBuilder taskResultBuilder,
      final long batchOperationKey,
      final String cursor) {
    final var command =
        new BatchOperationInitializationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSearchResultCursor(cursor);
    LOG.trace("Appending batch operation {} initializing command", batchOperationKey);

    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.CONTINUE_INITIALIZATION,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  private void appendQueryFailedCommand(
      final TaskResultBuilder taskResultBuilder,
      final PersistedBatchOperation batchOperation,
      final String message) {
    final var batchOperationKey = batchOperation.getKey();
    final var command = new BatchOperationPartitionLifecycleRecord();
    command.setBatchOperationKey(batchOperationKey);

    final var error = new BatchOperationError();
    error.setType(BatchOperationErrorType.QUERY_FAILED);
    error.setPartitionId(partitionId);
    error.setMessage(message);
    command.setError(error);

    LOG.trace("Appending batch operation {} failed event", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.FAIL,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  private void appendExecution(
      final Long batchOperationKey, final TaskResultBuilder taskResultBuilder) {
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperationKey);

    LOG.trace("Appending batch operation execution {}", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationExecutionIntent.EXECUTE,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }
}
