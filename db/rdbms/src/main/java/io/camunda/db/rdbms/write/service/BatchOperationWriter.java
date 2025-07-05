/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.read.service.BatchOperationReader;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemStatusUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateCountDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationWriter.class);

  private final ExecutionQueue executionQueue;
  private final BatchOperationReader reader;

  private final int itemInsertBlockSize;

  public BatchOperationWriter(
      final BatchOperationReader reader,
      final ExecutionQueue executionQueue,
      final RdbmsWriterConfig config) {
    this.reader = reader;
    this.executionQueue = executionQueue;
    itemInsertBlockSize = config.batchOperationItemInsertBlockSize();
  }

  public void createIfNotAlreadyExists(final BatchOperationDbModel batchOperation) {
    if (reader.exists(batchOperation.batchOperationId())) {
      LOGGER.trace("Batch operation already exists: {}", batchOperation);
      return;
    }

    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.INSERT,
            batchOperation.batchOperationId(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.insert",
            batchOperation));
    LOGGER.trace("Force flush to directly create batch operation: {}", batchOperation);
    executionQueue.flush();
  }

  public void incrementTotalItemCount(
      final String batchOperationId, final int totalCountIncrement) {
    incrementTotalCount(batchOperationId, totalCountIncrement);
  }

  public void updateItem(final BatchOperationItemDbModel item) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            item.batchOperationId(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItem",
            item));

    if (item.state() == BatchOperationItemState.FAILED) {
      incrementFailedCount(item.batchOperationId(), 1);
    } else if (item.state() == BatchOperationItemState.COMPLETED) {
      incrementCompletedCount(item.batchOperationId(), 1);
    }
  }

  /**
   * Inserts a list of items into the batch operation. This also updates all counts of the batch
   * operation depending on the state of the given items in the list.
   *
   * @param batchOperationId the batch operation id to which the items belong
   * @param itemList the items
   */
  public void insertItems(
      final String batchOperationId, final List<BatchOperationItemDbModel> itemList) {
    for (int i = 0; i < itemList.size(); i += itemInsertBlockSize) {
      final var block = itemList.subList(i, Math.min(i + itemInsertBlockSize, itemList.size()));
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.INSERT,
              batchOperationId,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.insertItems",
              new BatchOperationItemsDto(batchOperationId, block)));
    }

    final var stateCounts =
        itemList.stream().collect(Collectors.groupingBy(BatchOperationItemDbModel::state));

    final int activeCount =
        stateCounts.getOrDefault(BatchOperationItemState.ACTIVE, List.of()).size();
    final int completedCount =
        stateCounts.getOrDefault(BatchOperationItemState.COMPLETED, List.of()).size();
    final int failedCount =
        stateCounts.getOrDefault(BatchOperationItemState.FAILED, List.of()).size();

    if (activeCount > 0) {
      incrementTotalCount(batchOperationId, activeCount);
    }
    if (completedCount > 0) {
      incrementCompletedCount(batchOperationId, completedCount);
    }

    if (failedCount > 0) {
      incrementFailedCount(batchOperationId, failedCount);
    }
  }

  public void finish(final String batchOperationId, final OffsetDateTime endDate) {
    updateCompleted(
        batchOperationId,
        new BatchOperationUpdateDto(batchOperationId, BatchOperationState.COMPLETED, endDate));
  }

  public void cancel(final String batchOperationId, final OffsetDateTime endDate) {
    updateCompleted(
        batchOperationId,
        new BatchOperationUpdateDto(batchOperationId, BatchOperationState.CANCELED, endDate));

    // if we have exported pending items, we now need to set their state to canceled
    updateItemsWithState(
        batchOperationId, BatchOperationItemState.ACTIVE, BatchOperationItemState.CANCELED);
  }

  public void suspend(final String batchOperationId) {
    updateCompleted(
        batchOperationId,
        new BatchOperationUpdateDto(batchOperationId, BatchOperationState.SUSPENDED, null));
  }

  public void resume(final String batchOperationId) {
    updateCompleted(
        batchOperationId,
        new BatchOperationUpdateDto(batchOperationId, BatchOperationState.ACTIVE, null));
  }

  private void updateCompleted(final String batchOperationId, final BatchOperationUpdateDto dto) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationId,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateCompleted",
            dto));
  }

  private void updateItemsWithState(
      final String batchOperationId,
      final BatchOperationItemState oldState,
      final BatchOperationItemState newState) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationId,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItemsWithState",
            new BatchOperationItemStatusUpdateDto(batchOperationId, oldState, newState)));
  }

  private void incrementTotalCount(final String batchOperationId, final int totalCountIncrement) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationId,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementOperationsTotalCount",
            new BatchOperationUpdateCountDto(batchOperationId, totalCountIncrement)));
  }

  private void incrementFailedCount(final String batchOperationId, final int failedCount) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationId,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementFailedOperationsCount",
            new BatchOperationUpdateCountDto(batchOperationId, failedCount)));
  }

  private void incrementCompletedCount(final String batchOperationId, final int completedCount) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationId,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementCompletedOperationsCount",
            new BatchOperationUpdateCountDto(batchOperationId, completedCount)));
  }
}
