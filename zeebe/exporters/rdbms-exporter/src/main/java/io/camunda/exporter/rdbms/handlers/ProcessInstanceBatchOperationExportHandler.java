package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceBatchOperationExportHandler implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  public static final int DEFAULT_NOT_SET = -1;
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
        && record.getOperationReference() != DEFAULT_NOT_SET;
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(),
          value.getProcessInstanceKey(),
          BatchOperationItemState.COMPLETED
      );
    }
  }
}
