package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports a batch operation creation record to the database. This is only done if the batch was
 * also created on this partition!
 */
public class BatchOperationCreatedExportHandler
    implements RdbmsExportHandler<BatchOperationCreationRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationCreatedExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;
  private final long partitionId;

  public BatchOperationCreatedExportHandler(
      final BatchOperationWriter batchOperationWriter, final long partitionId) {
    this.batchOperationWriter = batchOperationWriter;
    this.partitionId = partitionId;
  }

  @Override
  public boolean canExport(final Record<BatchOperationCreationRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION
        && record.getIntent().equals(BatchOperationIntent.CREATED);
  }

  @Override
  public void export(final Record<BatchOperationCreationRecordValue> record) {
    batchOperationWriter.createIfNotAlreadyExists(map(record));
  }

  private BatchOperationDbModel map(final Record<BatchOperationCreationRecordValue> record) {
    final var value = record.getValue();
    return new BatchOperationDbModel.Builder()
        .batchOperationKey(record.getKey())
        .state(BatchOperationState.ACTIVE)
        .operationType(value.getBatchOperationType().name())
        .startDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .endDate(null)
        // FIXME no more keys list in the creation record, that needs to rely on something else
        .operationsTotalCount(0)
        .operationsFailedCount(0)
        .operationsCompletedCount(0)
        .build();
  }
}
