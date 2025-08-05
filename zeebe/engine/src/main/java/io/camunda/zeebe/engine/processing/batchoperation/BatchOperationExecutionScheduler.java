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
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
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
import io.camunda.zeebe.util.collection.Tuple;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
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

  private static final UnifiedRecordValue EMPTY_EXECUTION_RECORD =
      new BatchOperationExecutionRecord().setBatchOperationKey(-1L);
  private static final UnifiedRecordValue EMPTY_INITIALIZATION_RECORD =
      new BatchOperationInitializationRecord()
          .setBatchOperationKey(-1L)
          .setSearchQueryPageSize(0) // random int
          // random cursor string. 1024 chars should be enough for any cursor without sorting
          .setSearchResultCursor(RandomStringUtils.insecure().next(1024));

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

  private final AtomicReference<Tuple<Long, String>> initializing = new AtomicReference<>();

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
          .ifPresent(bo -> initializeBatchOperation(bo, taskResultBuilder));
      return taskResultBuilder.build();
    } finally {
      executing.set(false);
      scheduleExecution();
    }
  }

  private void initializeBatchOperation(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    if (batchOperation.isSuspended()) {
      LOG.trace("Batch operation {} is suspended.", batchOperation.getKey());
      return;
    }

    if (!validateNoReInitialization(batchOperation)) {
      return;
    }

    final var itemProvider = itemProviderFactory.fromBatchOperation(batchOperation);

    // use overall state variable for all parameters that are used in the loop
    final InitLoopState loopState = new InitLoopState(batchOperation);
    while (loopState.hasNextPage()) {
      try {
        loopState.page =
            itemProvider.fetchItemPage(
                loopState.lastSearchResultCursor, loopState.searchResultPageSize);
      } catch (final Exception e) {
        LOG.error(
            "Failed to query keys for batch operation with key {}. It will be removed from queue",
            batchOperation.getKey(),
            e);
        appendFailedCommand(
            taskResultBuilder,
            batchOperation,
            ExceptionUtils.getStackTrace(e),
            BatchOperationErrorType.QUERY_FAILED);
        return;
      }

      // Then try to append the items to the batch operation
      final boolean appendedChunks =
          appendChunks(batchOperation, taskResultBuilder, loopState.page.items());
      if (appendedChunks) {
        // everything went normally, so we can continue with the next page in the next loop

        loopState.chunksAppendedThisRun = true;
        loopState.lastSearchResultCursor = loopState.page.endCursor();
        loopState.keysAdded += loopState.page.items().size();
        if (loopState.page.isLastPage()) {
          // If we have reached the last page, we can finalize the initialization and start the BO
          // we always append the EXECUTE command at the end, even if no items were found, so we can
          // leave the completion logic in that processor.
          finishInitialization(loopState.batchOperation, taskResultBuilder);
          startExecutionPhase(taskResultBuilder, loopState);

          // this is the end of the initialization run, so we can break out of the loop
          return;
        }
      } else {
        handleFailedChunkAppend(taskResultBuilder, loopState);
        return;
      }
    }
  }

  private boolean validateNoReInitialization(final PersistedBatchOperation batchOperation) {
    final var initializingBO = initializing.get();
    if (initializingBO != null
        && initializingBO.getLeft() == batchOperation.getKey()
        && !Objects.equals(
            initializingBO.getRight(), batchOperation.getInitializationSearchCursor())) {
      // If the batch operation is already being initialized, we do not re-initialize it.
      LOG.trace(
          "Batch operation {} is already being executed, skipping re-initialization.",
          batchOperation.getKey());
      return false;
    }
    initializing.set(
        new Tuple<>(batchOperation.getKey(), batchOperation.getInitializationSearchCursor()));

    return true;
  }

  private void startExecutionPhase(
      final TaskResultBuilder resultBuilder, final InitLoopState state) {
    appendExecution(state.batchOperation.getKey(), resultBuilder);

    // start some metrics for the execution phase
    metrics.recordItemsPerPartition(
        state.batchOperation.getNumTotalItems() + state.keysAdded,
        state.batchOperation.getBatchOperationType());
    metrics.startStartExecuteLatencyMeasure(
        state.batchOperation.getKey(), state.batchOperation.getBatchOperationType());
    metrics.startTotalExecutionLatencyMeasure(
        state.batchOperation.getKey(), state.batchOperation.getBatchOperationType());
  }

  private boolean finishInitialization(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder resultBuilder) {
    final long batchOperationKey = batchOperation.getKey();
    final var command =
        new BatchOperationInitializationRecord().setBatchOperationKey(batchOperationKey);
    LOG.trace("Appending batch operation {} initializing finished command", batchOperationKey);

    final boolean appended =
        resultBuilder.appendCommandRecord(
            batchOperationKey,
            BatchOperationIntent.FINISH_INITIALIZATION,
            command,
            FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
    if (appended) {
      initializing.set(new Tuple<>(batchOperation.getKey(), "finished"));
      metrics.recordInitialized(batchOperation.getBatchOperationType());
    }
    return appended;
  }

  private void handleFailedChunkAppend(
      final TaskResultBuilder taskResultBuilder, final InitLoopState state) {
    if (!state.chunksAppendedThisRun) {
      // the first chunk of this init-run could not be appended. We try to reduce the page size
      // and retry again. If the pageSize can be halved, we will fail this partition.
      if (state.searchResultPageSize > 1) {
        state.searchResultPageSize = state.searchResultPageSize / 2;
        continueInitialization(taskResultBuilder, state);
      } else {
        // If we failed to append the first chunk even when the pageSize is 1,
        // we need to fail the batch operation. Otherwise, we would be stuck in an infinite loop
        appendFailedCommand(
            taskResultBuilder,
            state.batchOperation,
            String.format(ERROR_MSG_FAILED_FIRST_CHUNK_APPEND, state.page.items().size()),
            BatchOperationErrorType.RESULT_BUFFER_SIZE_EXCEEDED);
      }
    } else {
      // The RecordBatch is full, so we need another init run to continue
      continueInitialization(taskResultBuilder, state);
    }
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

    // we first ask the taskResultBuilder if we even can append the all the records. We also check
    // for adding an EXECUTE and a CONTINUE_INITIALIZATION record
    final List<UnifiedRecordValue> sizeCheckRecords = new ArrayList<>(chunkRecords);
    sizeCheckRecords.add(EMPTY_EXECUTION_RECORD);
    sizeCheckRecords.add(EMPTY_INITIALIZATION_RECORD);
    final var canAppend = taskResultBuilder.canAppendRecords(sizeCheckRecords, metadata);

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

  private void continueInitialization(
      final TaskResultBuilder taskResultBuilder, final InitLoopState state) {
    final var batchOperation = state.batchOperation;
    final var command =
        new BatchOperationInitializationRecord()
            .setBatchOperationKey(batchOperation.getKey())
            .setSearchResultCursor( // no null values allowed in the protocol
                state.lastSearchResultCursor == null
                    ? StringValue.EMPTY_STRING
                    : state.lastSearchResultCursor)
            .setSearchQueryPageSize(state.searchResultPageSize);
    LOG.trace("Appending batch operation {} initializing command", batchOperation.getKey());
    initializing.set(new Tuple<>(batchOperation.getKey(), command.getSearchResultCursor()));
    taskResultBuilder.appendCommandRecord(
        batchOperation.getKey(),
        BatchOperationIntent.INITIALIZE,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperation.getKey())));
  }

  private void appendFailedCommand(
      final TaskResultBuilder taskResultBuilder,
      final PersistedBatchOperation batchOperation,
      final String message,
      final BatchOperationErrorType errorType) {
    final var batchOperationKey = batchOperation.getKey();
    final var command = new BatchOperationPartitionLifecycleRecord();
    command.setBatchOperationKey(batchOperationKey);

    final var error = new BatchOperationError();
    error.setType(errorType);
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

  private boolean appendExecution(
      final Long batchOperationKey, final TaskResultBuilder taskResultBuilder) {
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperationKey);

    LOG.trace("Appending batch operation execution {}", batchOperationKey);
    return taskResultBuilder.appendCommandRecord(
        batchOperationKey,
        BatchOperationExecutionIntent.EXECUTE,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  /**
   * This is a mutable state class that is used to track the state of the initialization loop. Using
   * this state class avoids the need to pass around multiple parameters.
   */
  private class InitLoopState {
    public PersistedBatchOperation batchOperation;
    public String lastSearchResultCursor;
    public int searchResultPageSize;
    public ItemPage page;
    public int keysAdded;
    public boolean chunksAppendedThisRun = false;

    public InitLoopState(final PersistedBatchOperation batchOperation) {
      this.batchOperation = batchOperation;
      lastSearchResultCursor = batchOperation.getInitializationSearchCursor();
      searchResultPageSize = batchOperation.getInitializationSearchQueryPageSize(queryPageSize);
      page = null;
      keysAdded = 0;
      chunksAppendedThisRun = false;

      // StringValue cannot be null, so we need to check for empty string and interpret it as NULL
      if (lastSearchResultCursor != null && lastSearchResultCursor.isEmpty()) {
        // If the cursor is empty, we need to initialize it with the first page
        lastSearchResultCursor = null;
      }
    }

    public boolean hasNextPage() {
      return page == null || !page.isLastPage();
    }
  }
}
