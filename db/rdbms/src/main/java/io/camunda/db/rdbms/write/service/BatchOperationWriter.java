package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

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

}
