package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionExportHandler implements RdbmsExportHandler<BatchOperationExecutionRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationExecutionExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationExecutionExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationExecutionRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_EXECUTION
        && (record.getIntent().equals(BatchOperationIntent.EXECUTED)
        || record.getIntent().equals(BatchOperationIntent.COMPLETED));
  }

  @Override
  public void export(final Record<BatchOperationExecutionRecordValue> record) {
    final var value = record.getValue();
    if (record.getIntent().equals(BatchOperationIntent.EXECUTED)) {
      batchOperationWriter.update(
          record.getKey(),
          0,
          value.getOffset(),
          value.getKeys());
    } else if (record.getIntent().equals(BatchOperationIntent.COMPLETED)) {
      final OffsetDateTime endDate = DateUtil.toOffsetDateTime(record.getTimestamp());
      batchOperationWriter.finish(record.getKey(),
          endDate,
          0,
          value.getOffset(),
          value.getKeys()
      );
    }
  }
}
