/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import com.google.common.base.Strings;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes batch operations by processing items in chunks and handling the initialization phase.
 *
 * <p>This class is responsible for:
 *
 * <ul>
 *   <li>Processing batch operation items in configurable page sizes
 *   <li>Handling chunk appending failures with retry logic and page size reduction
 *   <li>Managing the transition from initialization to execution phase
 *   <li>Recording metrics for batch operation performance
 * </ul/>
 *
 * <p>The initialization process fetches items using an {@link ItemProviderFactory}, processes them
 * in pages via {@link BatchOperationPageProcessor}, and builds commands through {@link
 * BatchOperationCommandAppender}. If chunk appending fails, it attempts to reduce the page size and
 * retry, or marks the operation as failed if the minimum page size is reached.
 *
 * @see BatchOperationPageProcessor
 * @see BatchOperationCommandAppender
 * @see ItemProviderFactory
 * @see PersistedBatchOperation
 */
public class BatchOperationInitializer {
  public static final String ERROR_MSG_FAILED_FIRST_CHUNK_APPEND =
      "Unable to append first chunk of batch operation items. Number of items: %d";
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationInitializer.class);

  private final ItemProviderFactory itemProviderFactory;
  private final BatchOperationMetrics metrics;
  private final BatchOperationCommandAppender commandAppender;
  private final BatchOperationPageProcessor pageProcessor;
  private final int queryPageSize;

  public BatchOperationInitializer(
      final ItemProviderFactory itemProviderFactory,
      final BatchOperationPageProcessor pageProcessor,
      final BatchOperationCommandAppender commandAppender,
      final int queryPageSize,
      final BatchOperationMetrics metrics) {
    this.itemProviderFactory = itemProviderFactory;
    this.commandAppender = commandAppender;
    this.pageProcessor = pageProcessor;
    this.queryPageSize = queryPageSize;
    this.metrics = metrics;
  }

  /**
   * Initializes a batch operation by processing its items in pages and appending them as chunks.
   * This method handles the initialization phase of a batch operation, including:
   *
   * <ul>
   *   <li>Checking if the operation is suspended
   *   <li>Fetching items using an item provider
   *   <li>Processing pages of items and appending them as chunks
   *   <li>Handling chunk appending failures with retries and page size reduction
   *   <li>Transitioning to the execution phase once initialization is complete
   *   <li>Recording metrics for the operation
   *   <li>Returning a result containing the batch operation key and search result cursor
   * </ul>
   *
   * This method is designed to be resilient against failures during the initialization phase,
   * allowing for retries and adjustments to the page size if necessary.
   *
   * @param batchOperation the batch operation to initialize
   * @param taskResultBuilder the builder to append task results
   * @return a result containing the batch operation key and search result cursor
   */
  public BatchOperationInitializationResult initializeBatchOperation(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    if (batchOperation.isSuspended()) {
      LOG.trace("Batch operation {} is suspended.", batchOperation.getKey());
      return new BatchOperationInitializationResult(
          batchOperation.getKey(), batchOperation.getInitializationSearchCursor());
    }

    final var itemProvider = itemProviderFactory.fromBatchOperation(batchOperation);
    var context = InitializationContext.fromBatchOperation(batchOperation, queryPageSize);

    while (true) {
      try {
        final var page = itemProvider.fetchItemPage(context.currentCursor(), context.pageSize());
        final var result =
            pageProcessor.processPage(batchOperation.getKey(), page, taskResultBuilder);

        if (result.chunksAppended()) {
          context = context.withNextPage(result.endCursor(), result.itemsProcessed(), true);

          if (result.isLastPage()) {
            finishInitialization(batchOperation, taskResultBuilder);
            startExecutionPhase(taskResultBuilder, context);
            return new BatchOperationInitializationResult(batchOperation.getKey(), "finished");
          }
        } else {
          return handleFailedChunkAppend(taskResultBuilder, context, result.itemsProcessed());
        }
      } catch (final BatchOperationInitializationException e) {
        throw e;
      } catch (final Exception e) {
        if (context.hasAppendedChunks()) {
          continueInitialization(taskResultBuilder, context);
        }
        throw new BatchOperationInitializationException(e, context.currentCursor());
      }
    }
  }

  private BatchOperationInitializationResult handleFailedChunkAppend(
      final TaskResultBuilder taskResultBuilder,
      final InitializationContext context,
      final int itemCount) {
    if (!context.hasAppendedChunks()) {
      if (context.pageSize() > 1) {
        final var reducedContext = context.withHalvedPageSize();
        continueInitialization(taskResultBuilder, reducedContext);
      } else {
        throw new BatchOperationInitializationException(
            String.format(ERROR_MSG_FAILED_FIRST_CHUNK_APPEND, itemCount),
            BatchOperationErrorType.RESULT_BUFFER_SIZE_EXCEEDED,
            context.currentCursor());
      }
    } else {
      continueInitialization(taskResultBuilder, context);
    }
    return new BatchOperationInitializationResult(
        context.operation().getKey(), Strings.nullToEmpty(context.currentCursor()));
  }

  private void startExecutionPhase(
      final TaskResultBuilder resultBuilder, final InitializationContext context) {
    commandAppender.appendExecutionCommand(resultBuilder, context.operation().getKey());

    metrics.recordItemsPerPartition(
        context.operation().getNumTotalItems() + context.itemsProcessed(),
        context.operation().getBatchOperationType());
    metrics.startStartExecuteLatencyMeasure(
        context.operation().getKey(), context.operation().getBatchOperationType());
    metrics.startTotalExecutionLatencyMeasure(
        context.operation().getKey(), context.operation().getBatchOperationType());
  }

  private void finishInitialization(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder resultBuilder) {
    commandAppender.appendFinishInitializationCommand(resultBuilder, batchOperation.getKey());
    metrics.recordInitialized(batchOperation.getBatchOperationType());
  }

  private void continueInitialization(
      final TaskResultBuilder taskResultBuilder, final InitializationContext context) {

    commandAppender.appendInitializationCommand(
        taskResultBuilder,
        context.operation().getKey(),
        context.currentCursor(),
        context.pageSize());
  }

  public void appendFailedCommand(
      final TaskResultBuilder taskResultBuilder,
      final long batchOperationKey,
      final BatchOperationInitializationException exception) {
    commandAppender.appendFailureCommand(
        taskResultBuilder, batchOperationKey, exception.getMessage(), exception.getErrorType());
  }

  public record BatchOperationInitializationResult(
      long batchOperationKey, String searchResultCursor) {}

  public static class BatchOperationInitializationException extends RuntimeException {
    private final String endCursor;
    private final BatchOperationErrorType errorType;

    public BatchOperationInitializationException(final Throwable e, final String endCursor) {
      super(
          String.format(
              "Failed to initialize batch operation with end cursor: %s. Reason: %s",
              endCursor, e.getMessage()),
          e);
      this.endCursor = endCursor;
      errorType = BatchOperationErrorType.QUERY_FAILED;
    }

    public BatchOperationInitializationException(
        final String message, final BatchOperationErrorType errorType, final String endCursor) {
      super(message);
      this.endCursor = endCursor;
      this.errorType = errorType;
    }

    public BatchOperationErrorType getErrorType() {
      return errorType;
    }

    public String getEndCursor() {
      return endCursor;
    }
  }
}
