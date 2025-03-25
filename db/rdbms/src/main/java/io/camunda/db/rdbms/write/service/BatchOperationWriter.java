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
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateTotalCountDto;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemStatus;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationStatus;
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

  public void updateBatchAndInsertItems(final long batchOperationKey, final List<Long> items) {
    if (items != null && !items.isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationKey,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementOperationsTotalCount",
              new BatchOperationUpdateTotalCountDto(batchOperationKey, items.size())));
      insertItems(new BatchOperationItemsDto(batchOperationKey, items));
    }
  }

  public void updateItem(
      final long batchOperationKey, final long itemKey, final BatchOperationItemStatus state) {

    // TODO merging this into one statement would be more efficient
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItem",
            new BatchOperationItemDto(batchOperationKey, itemKey, state)));

    if (state == BatchOperationItemStatus.FAILED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationKey,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementFailedOperationsCount",
              batchOperationKey));
    } else if (state == BatchOperationItemStatus.COMPLETED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationKey,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementCompletedOperationsCount",
              batchOperationKey));
    }
  }

  public void finish(final long batchOperationKey, final OffsetDateTime endDate) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationStatus.COMPLETED, endDate));
  }

  public void cancel(final long batchOperationKey, final OffsetDateTime endDate) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationStatus.CANCELED, endDate));
  }

  public void paused(final long batchOperationKey) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationStatus.PAUSED, null));
  }

  public void resumed(final long batchOperationKey) {
    updateCompleted(
        batchOperationKey,
        new BatchOperationUpdateDto(batchOperationKey, BatchOperationStatus.ACTIVE, null));
  }

  private void updateCompleted(final long batchOperationKey, final BatchOperationUpdateDto dto) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateCompleted",
            dto));
  }

  private void updateItemsWithStatus(
      final long batchOperationKey,
      final BatchOperationItemStatus oldState,
      final BatchOperationItemStatus newState) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItemsWithState",
            new BatchOperationItemStatusUpdateDto(batchOperationKey, oldState, newState)));
  }

  private void insertItems(final BatchOperationItemsDto items) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.INSERT,
            items.batchOperationKey(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.insertItems",
            items));
  }
}
