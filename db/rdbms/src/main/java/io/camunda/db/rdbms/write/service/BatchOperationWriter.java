package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public class BatchOperationWriter {

  private final ExecutionQueue executionQueue;

  public BatchOperationWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final BatchOperationDbModel batchOperation) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.INSERT,
            batchOperation.batchOperationKey(),
            "io.camunda.db.rdbms.sql.BatchOperationMapper.insert",
            batchOperation));
  }

  public void update(final long batchOperationKey,
      final int operationsFailedCount,
      final int operationsCompletedCount,
      final Set<Long> items) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateCompleted",
            new BatchOperationUpdateDto(batchOperationKey,
                null,
                operationsFailedCount,
                operationsCompletedCount)));

    if(items != null && !items.isEmpty()) {
      insertItems(new BatchOperationItemsDto(batchOperationKey, items));
    }
  }

  public void finish(final long batchOperationKey,
      final OffsetDateTime endDate,
      final int operationsFailedCount,
      final int operationsCompletedCount,
      final Set<Long> items) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateCompleted",
            new BatchOperationUpdateDto(batchOperationKey,
                endDate,
                operationsFailedCount,
                operationsCompletedCount)));

    if(items != null && !items.isEmpty()) {
      insertItems(new BatchOperationItemsDto(batchOperationKey, items));
    }
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
