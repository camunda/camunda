/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemStatusUpdateDto;
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
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationWriter.class);

  private final ExecutionQueue executionQueue;
  private final BatchOperationDbReader reader;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final BatchOperationMapper mapper;

  private final int itemInsertBlockSize;
  private final boolean exportPendingBatchOperationItems;

  public BatchOperationWriter(
      final BatchOperationDbReader reader,
      final ExecutionQueue executionQueue,
      final BatchOperationMapper mapper,
      final RdbmsWriterConfig config,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.reader = reader;
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.mapper = mapper;
    itemInsertBlockSize = config.batchOperationItemInsertBlockSize();
    exportPendingBatchOperationItems = config.exportBatchOperationItemsOnCreation();
  }

  public void createIfNotAlreadyExists(final BatchOperationDbModel batchOperation) {
    // here we read if the batch operation already exists in the database to avoid PK violations.
    // since we flush directly after the insert statement, this is transactionally safe
    if (reader.exists(batchOperation.batchOperationKey())) {
      LOGGER.trace("Batch operation already exists: {}", batchOperation);
      return;
    }

    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.INSERT,
            batchOperation.batchOperationKey(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.insert",
            batchOperation));
    LOGGER.trace("Force flush to directly create batch operation: {}", batchOperation);
    executionQueue.flush();
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
    } else if (item.state() == BatchOperationItemState.COMPLETED
        || item.state() == BatchOperationItemState.SKIPPED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              item.batchOperationKey(),
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementCompletedOperationsCount",
              item.batchOperationKey()));
    }
  }

  public void finish(final String batchOperationKey, final OffsetDateTime endDate) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationState.COMPLETED, endDate));
  }

  public void finishWithErrors(
      final String batchOperationKey,
      final OffsetDateTime endDate,
      final BatchOperationErrorsDto errors) {
    insertErrors(batchOperationKey, errors);

    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(
            batchOperationKey, BatchOperationState.PARTIALLY_COMPLETED, endDate));
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

  public int cleanupItemHistory(final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupItemHistory(
        new CleanupBatchOperationHistoryDto(cleanupDate, rowsToRemove));
  }
}
