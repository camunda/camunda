/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.read.service.BatchOperationReader;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemStatusUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateCountsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateTotalCountDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationWriter.class);

  private final ExecutionQueue executionQueue;

  private final BatchOperationReader reader;

  public BatchOperationWriter(
      final BatchOperationReader reader, final ExecutionQueue executionQueue) {
    this.reader = reader;
    this.executionQueue = executionQueue;
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

  public void updateBatchAndInsertItems(
      final String batchOperationId, final List<BatchOperationItemDbModel> items) {
    if (items != null && !items.isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationId,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementOperationsTotalCount",
              new BatchOperationUpdateTotalCountDto(batchOperationId, items.size())));
      insertItems(new BatchOperationItemsDto(batchOperationId, items));
    }
  }

  public void updateItem(
      final String batchOperationId,
      final long itemKey,
      final BatchOperationItemState state,
      final OffsetDateTime endDate,
      final String errorMessage) {

    // TODO merging this into one statement would be more efficient
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationId,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItem",
            new BatchOperationItemDto(batchOperationId, itemKey, state, endDate, errorMessage)));

    if (state == BatchOperationItemState.FAILED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationId,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementFailedOperationsCount",
              new BatchOperationUpdateCountsDto(batchOperationId, itemKey)));
    } else if (state == BatchOperationItemState.COMPLETED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationId,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementCompletedOperationsCount",
              new BatchOperationUpdateCountsDto(batchOperationId, itemKey)));
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

  private void insertItems(final BatchOperationItemsDto items) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.INSERT,
            items.batchOperationId(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.insertItems",
            items));
  }
}
