package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationSubbatchRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports a batch operation creation record to the database. This is only done if the batch was
 * also created on this partition!
 */
public class BatchOperationSubBatchExportHandler
    implements RdbmsExportHandler<BatchOperationSubbatchRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationSubBatchExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationSubBatchExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationSubbatchRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_SUBBATCH
        && record.getIntent().equals(BatchOperationIntent.CREATED_SUBBATCH);
  }

  @Override
  public void export(final Record<BatchOperationSubbatchRecordValue> record) {
    final var value = record.getValue();
    batchOperationWriter.updateBatchAndInsertItems(value.getBatchOperationKey(), value.getKeys());
  }
}
