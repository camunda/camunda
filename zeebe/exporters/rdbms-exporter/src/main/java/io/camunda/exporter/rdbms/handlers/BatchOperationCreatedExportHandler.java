package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationCreatedExportHandler implements RdbmsExportHandler<BatchOperationCreationRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationCreatedExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationCreatedExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationCreationRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION && record.getIntent().equals(BatchOperationIntent.CREATED);
  }

  @Override
  public void export(final Record<BatchOperationCreationRecordValue> record) {
    batchOperationWriter.create(map(record));
  }

  private BatchOperationDbModel map(final Record<BatchOperationCreationRecordValue> record) {
    final var value = record.getValue();
    return new BatchOperationDbModel.Builder()
        .batchOperationKey(record.getKey())
        .operationType(value.getBatchOperationType().name())
        .startDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .endDate(null)
        .operationsTotalCount(value.getKeys().size())
        .operationsFailedCount(0)
        .operationsCompletedCount(0)
        .build();
  }
}
