/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationActivateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationFinalizeEndDateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemCompletionDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemStatusUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsCompletionDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateTotalCountDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.CleanupBatchOperationHistoryDto;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.QueueItemMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public class BatchOperationWriter implements RdbmsWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationWriter.class);

  /** Above the default order of all other batch operation updates, see {@link #finalizeEndDate}. */
  private static final int FINALIZE_END_DATE_ORDER = 1;

  private static final String FINALIZE_END_DATE_STATEMENT =
      "io.camunda.db.rdbms.sql.BatchOperationMapper.finalizeEndDate";

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final BatchOperationMapper mapper;

  private final int itemInsertBlockSize;
  private final boolean exportPendingBatchOperationItems;

  public BatchOperationWriter(
      final ExecutionQueue executionQueue,
      final BatchOperationMapper mapper,
      final RdbmsWriterConfig config,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.mapper = mapper;
    itemInsertBlockSize = config.batchOperationItemInsertBlockSize();
    exportPendingBatchOperationItems = config.exportBatchOperationItemsOnCreation();
  }

  public void create(final BatchOperationDbModel batchOperation) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.INSERT,
            batchOperation.batchOperationKey(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.createIfNotExists",
            batchOperation));
  }

  public void activate(final String batchOperationKey, final OffsetDateTime startDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.activate",
            new BatchOperationActivateDto(batchOperationKey, startDate)));
  }

  public void updateBatchAndInsertItems(
      final String batchOperationKey, final List<BatchOperationItemDbModel> items) {
    if (items != null && !items.isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationKey,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementOperationsTotalCount",
              new BatchOperationUpdateTotalCountDto(batchOperationKey, items.size())));
      if (exportPendingBatchOperationItems) {
        insertItems(new BatchOperationItemsDto(batchOperationKey, items));
      }
    }
  }

  public void updateItem(final BatchOperationItemDbModel item) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            item.batchOperationKey(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.upsertItem",
            item.truncateErrorMessage(
                vendorDatabaseProperties.errorMessageSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));

    if (item.state() == BatchOperationItemState.FAILED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              item.batchOperationKey(),
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementFailedOperationsCount",
              item.batchOperationKey()));
      finalizeEndDate(item.batchOperationKey(), item.processedDate());
    } else if (item.state() == BatchOperationItemState.COMPLETED
        || item.state() == BatchOperationItemState.SKIPPED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              item.batchOperationKey(),
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementCompletedOperationsCount",
              item.batchOperationKey()));
      finalizeEndDate(item.batchOperationKey(), item.processedDate());
    }
  }

  /**
   * Marks a batch of process instance batch operation items as completed in bulk, setting their
   * state to COMPLETED and the processed date to the given end time. Items are split into blocks of
   * {@code itemInsertBlockSize} to avoid generating excessively large SQL statements.
   *
   * @param items list of (batchOperationKey, itemKey) pairs identifying the items to complete
   * @param processedDate the completion timestamp to set on each item
   */
  public void completeBatchOperationItems(
      final List<BatchOperationItemCompletionDto> items, final OffsetDateTime processedDate) {
    if (items.isEmpty()) {
      return;
    }
    for (int i = 0; i < items.size(); i += itemInsertBlockSize) {
      final var block = items.subList(i, Math.min(i + itemInsertBlockSize, items.size()));
      mapper.completeBatchOperationItems(
          new BatchOperationItemsCompletionDto(block, processedDate));
    }

    items.stream()
        .collect(
            Collectors.groupingBy(
                BatchOperationItemCompletionDto::batchOperationKey, Collectors.counting()))
        .forEach(
            (batchOperationKey, completedCount) -> {
              mapper.bulkIncrementCompletedOperationsCount(
                  new BatchOperationUpdateTotalCountDto(
                      batchOperationKey, completedCount.intValue()));
              mapper.finalizeEndDate(
                  new BatchOperationFinalizeEndDateDto(batchOperationKey, processedDate));
            });
  }

  /**
   * Marks the batch operation as {@code COMPLETED}. Its end date is only set once all of its items
   * have settled, see {@link #finalizeEndDate}.
   */
  public void finish(final String batchOperationKey, final OffsetDateTime endDate) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationState.COMPLETED, null));
    finalizeEndDate(batchOperationKey, endDate);
  }

  /**
   * Marks the batch operation with the given terminal state. Its end date is only set once all of
   * its items have settled, see {@link #finalizeEndDate}.
   */
  public void finishWithErrors(
      final String batchOperationKey,
      final OffsetDateTime endDate,
      final BatchOperationErrorsDto errors,
      final BatchOperationState batchOperationState) {
    insertErrors(batchOperationKey, errors);

    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, batchOperationState, null));
    finalizeEndDate(batchOperationKey, endDate);
  }

  public void cancel(final String batchOperationKey, final OffsetDateTime endDate) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationState.CANCELED, endDate));

    // if we have exported pending items, we now need to set their state to canceled
    if (exportPendingBatchOperationItems) {
      updateItemsWithState(
          batchOperationKey, BatchOperationItemState.ACTIVE, BatchOperationItemState.CANCELED);
    }
  }

  public void suspend(final String batchOperationKey) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationState.SUSPENDED, null));
  }

  public void resume(final String batchOperationKey) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationState.ACTIVE, null));
  }

  /**
   * Sets the end date once the batch operation has finished and all of its items are completed or
   * failed, and does nothing before that. The check runs in the database, so it does not depend on
   * whether the lifecycle record or the last item is exported first. It has to be queued after
   * every write that can make the condition true.
   *
   * <p>The end date is the latest of the timestamps passed in for the flush that settles the batch
   * operation: the {@code COMPLETED} record or the last item to settle, whichever is later.
   *
   * <p>The queue sorts the updates of a flush by statement ID, so it is queued with a higher {@code
   * order} to run after the state and count updates it reads.
   *
   * <p>Only one call per batch operation is kept in each flush, with the latest timestamp: they all
   * run after the count updates and see the same row, so the others could not change the result.
   */
  private void finalizeEndDate(final String batchOperationKey, final OffsetDateTime endDate) {
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new FinalizeEndDateMerger(batchOperationKey, endDate));
    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationKey,
              FINALIZE_END_DATE_STATEMENT,
              new BatchOperationFinalizeEndDateDto(batchOperationKey, endDate),
              FINALIZE_END_DATE_ORDER));
    }
  }

  private void updateCompleted(final String batchOperationKey, final BatchOperationUpdateDto dto) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateCompleted",
            dto));
  }

  private void updateItemsWithState(
      final String batchOperationKey,
      final BatchOperationItemState oldState,
      final BatchOperationItemState newState) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItemsWithState",
            new BatchOperationItemStatusUpdateDto(batchOperationKey, oldState, newState)));
  }

  @VisibleForTesting
  void insertItems(final BatchOperationItemsDto items) {
    final var itemList = items.items();

    for (int i = 0; i < itemList.size(); i += itemInsertBlockSize) {
      final var block = itemList.subList(i, Math.min(i + itemInsertBlockSize, itemList.size()));
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.INSERT,
              items.batchOperationKey(),
              "io.camunda.db.rdbms.sql.BatchOperationMapper.insertItems",
              new BatchOperationItemsDto(items.batchOperationKey(), block)));
    }
  }

  void insertErrors(final String batchOperationKey, final BatchOperationErrorsDto errors) {
    final BatchOperationErrorsDto truncatedErrors =
        new BatchOperationErrorsDto(
            batchOperationKey,
            errors.errors().stream()
                .map(
                    error ->
                        error.truncateErrorMessage(
                            vendorDatabaseProperties.errorMessageSize(),
                            vendorDatabaseProperties.charColumnMaxBytes()))
                .collect(Collectors.toList()));

    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.INSERT,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.insertErrors",
            truncatedErrors));
  }

  public void scheduleForHistoryCleanup(
      final String batchOperationKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateHistoryCleanupDate",
            new BatchOperationMapper.UpdateHistoryCleanupDateDto(
                batchOperationKey, historyCleanupDate)));
  }

  public int cleanupHistory(final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupHistory(new CleanupBatchOperationHistoryDto(cleanupDate, rowsToRemove));
  }

  /** Keeps the later end date of two {@link #finalizeEndDate} calls in the same flush. */
  private record FinalizeEndDateMerger(String batchOperationKey, OffsetDateTime endDate)
      implements QueueItemMerger {

    @Override
    public boolean canBeMerged(final QueueItem queueItem) {
      return queueItem.contextType() == ContextType.BATCH_OPERATION
          && queueItem.statementId().equals(FINALIZE_END_DATE_STATEMENT)
          && queueItem.id().equals(batchOperationKey);
    }

    @Override
    public QueueItem merge(final QueueItem originalItem) {
      final var queuedEndDate =
          ((BatchOperationFinalizeEndDateDto) originalItem.parameter()).endDate();
      final var laterEndDate =
          queuedEndDate == null || (endDate != null && endDate.isAfter(queuedEndDate))
              ? endDate
              : queuedEndDate;
      return originalItem.copy(
          b -> b.parameter(new BatchOperationFinalizeEndDateDto(batchOperationKey, laterEndDate)));
    }
  }
}
