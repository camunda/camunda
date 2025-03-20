package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionExportHandler
    implements RdbmsExportHandler<BatchOperationExecutionRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationExecutionExportHandler.class);

  private static final Set<BatchOperationExecutionIntent> EXPORTABLE_INTENTS =
      Set.of(
          BatchOperationExecutionIntent.EXECUTING,
          BatchOperationExecutionIntent.EXECUTED,
          BatchOperationExecutionIntent.COMPLETED,
          BatchOperationExecutionIntent.CANCELED,
          BatchOperationExecutionIntent.PAUSED,
          BatchOperationExecutionIntent.RESUMED);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationExecutionExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationExecutionRecordValue> record) {
    if (record.getValueType() == ValueType.BATCH_OPERATION_EXECUTION
        && record.getIntent() instanceof final BatchOperationExecutionIntent intent) {
      return EXPORTABLE_INTENTS.contains(intent);
    }

    return false;
  }

  @Override
  public void export(final Record<BatchOperationExecutionRecordValue> record) {
    final var value = record.getValue();
    final var batchOperationKey = value.getBatchOperationKey();
    if (record.getIntent().equals(BatchOperationExecutionIntent.COMPLETED)) {
      batchOperationWriter.finish(
          batchOperationKey, DateUtil.toOffsetDateTime(record.getTimestamp()));
    } else if (record.getIntent().equals(BatchOperationExecutionIntent.CANCELED)) {
      batchOperationWriter.cancel(
          batchOperationKey, DateUtil.toOffsetDateTime(record.getTimestamp()));
    } else if (record.getIntent().equals(BatchOperationExecutionIntent.PAUSED)) {
      batchOperationWriter.paused(batchOperationKey);
    } else if (record.getIntent().equals(BatchOperationExecutionIntent.RESUMED)) {
      batchOperationWriter.resumed(batchOperationKey);
    }
  }
}
