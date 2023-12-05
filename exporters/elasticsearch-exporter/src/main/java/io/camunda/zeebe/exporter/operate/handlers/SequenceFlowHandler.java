package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class SequenceFlowHandler implements ExportHandler<SequenceFlowEntity, ProcessInstanceRecordValue> {
  
  private static final String ID_PATTERN = "%s_%s";
  private static final Logger logger = LoggerFactory.getLogger(SequenceFlowHandler.class);

  private SequenceFlowTemplate sequenceFlowTemplate = new SequenceFlowTemplate();
  
  @Override
  public ValueType handlesValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    
    return ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN.equals(record.getIntent());
  }

  @Override
  public String generateId(Record<ProcessInstanceRecordValue> record) {
    ProcessInstanceRecordValue recordValue = record.getValue();
    return String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementId());
  }

  @Override
  public SequenceFlowEntity createNewEntity(String id) {
    return new SequenceFlowEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<ProcessInstanceRecordValue> record, SequenceFlowEntity entity) {
    ProcessInstanceRecordValue recordValue = record.getValue();
    
    entity
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setActivityId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));
  }

  @Override
  public void flush(SequenceFlowEntity entity, BatchRequest batchRequest)
      throws PersistenceException {

    logger.debug("Index sequence flow: id {}", entity.getId());
    batchRequest.add(sequenceFlowTemplate.getFullQualifiedName(), entity);
    
  }

}
