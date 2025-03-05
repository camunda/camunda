package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemStateUpdateDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationItemsDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationUpdateDto;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import java.time.OffsetDateTime;
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
      final Set<Long> items) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateCompleted",
            new BatchOperationUpdateDto(batchOperationKey,
                BatchOperationState.ACTIVE,
                null)));

    if(items != null && !items.isEmpty()) {
      insertItems(new BatchOperationItemsDto(batchOperationKey, items));
    }
  }

  public void updateItem(final long batchOperationKey,
      final long itemKey,
      final BatchOperationState state) {

    // TODO merging this into one statement would be more efficient
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItem",
            new BatchOperationItemDto(
                batchOperationKey,
                itemKey,
                state)
        ));

    // TODO merging this into one statement would be more efficient
    if(state == BatchOperationState.FAILED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationKey,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementFailedOperationsCount",
              batchOperationKey));
    } else if(state == BatchOperationState.COMPLETED) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.BATCH_OPERATION,
              WriteStatementType.UPDATE,
              batchOperationKey,
              "io.camunda.db.rdbms.sql.BatchOperationMapper.incrementCompletedOperationsCount",
              batchOperationKey));
    }
  }

  public void finish(final long batchOperationKey,
      final OffsetDateTime endDate) {
    updateCompleted(batchOperationKey,
        new BatchOperationUpdateDto(
            batchOperationKey,
            BatchOperationState.COMPLETED,
            endDate
        )
    );
  }

  public void cancel(final long batchOperationKey, final OffsetDateTime endDate) {
    updateCompleted(batchOperationKey,
        new BatchOperationUpdateDto(
            batchOperationKey,
            BatchOperationState.CANCELED,
            endDate
        )
    );

    updateItemsWithState(
        batchOperationKey,
        BatchOperationState.ACTIVE,
        BatchOperationState.CANCELED
    );
  }

  public void paused(final long batchOperationKey) {
    updateCompleted(batchOperationKey,
        new BatchOperationUpdateDto(
            batchOperationKey,
            BatchOperationState.PAUSED,
            null
        )
    );

    updateItemsWithState(
        batchOperationKey,
        BatchOperationState.ACTIVE,
        BatchOperationState.PAUSED
    );
  }

  public void resumed(final long batchOperationKey) {
    updateCompleted(batchOperationKey,
        new BatchOperationUpdateDto(
            batchOperationKey,
            BatchOperationState.ACTIVE,
            null
        )
    );

    updateItemsWithState(
        batchOperationKey,
        BatchOperationState.PAUSED,
        BatchOperationState.ACTIVE
    );
  }

  private void updateCompleted(final long batchOperationKey,
      final BatchOperationUpdateDto dto) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateCompleted",
            dto
        )
    );
  }

  private void updateItemsWithState(final long batchOperationKey,
      final BatchOperationState oldState,
      final BatchOperationState newState) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.BATCH_OPERATION,
            WriteStatementType.UPDATE,
            batchOperationKey,
            "io.camunda.db.rdbms.sql.BatchOperationMapper.updateItemsWithState",
            new BatchOperationItemStateUpdateDto(
                batchOperationKey,
                oldState,
                newState))
    );
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
