package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.protocol.record.Record;
import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceBatchOperationExportHandler implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      ProcessInstanceBatchOperationExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;

  public ProcessInstanceBatchOperationExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return record.getValueType() == ValueType.PROCESS_INSTANCE
        && record.getValue().getBpmnElementType() == BpmnElementType.PROCESS
        && record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED)
        && record.getOperationReference() != operationReferenceNullValue(); // TODO this would also fire on NON Batch operations which have a reference
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(),
          value.getProcessInstanceKey(),
          BatchOperationState.COMPLETED
      );
    }
  }
}
