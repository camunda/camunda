package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

public class FlowNodeInstanceFromIncidentHandler implements ExportHandler<FlowNodeInstanceEntity, IncidentRecordValue> {

  // TODO: same problem as in ListViewFromIncidentHandler: this updates the same entity that another handler manages
  
  
  private static final Logger logger = LoggerFactory.getLogger(FlowNodeInstanceFromIncidentHandler.class);

  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  
  public FlowNodeInstanceFromIncidentHandler(FlowNodeInstanceTemplate flowNodeInstanceTemplate) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
  }

  @Override
  public ValueType handlesValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public boolean handlesRecord(Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public String generateId(Record<IncidentRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey());
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(String id) {
    return new FlowNodeInstanceEntity()
        .setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, FlowNodeInstanceEntity entity) {
    final Intent intent = record.getIntent();
    IncidentRecordValue recordValue = (IncidentRecordValue)record.getValue();

    //update activity instance
    entity
        .setKey(recordValue.getElementInstanceKey())
        .setPartitionId(record.getPartitionId())
        .setFlowNodeId(recordValue.getElementId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));
    
    if (intent == IncidentIntent.CREATED) {
      entity.setIncidentKey(record.getKey());
    } else if (intent == IncidentIntent.RESOLVED) {
      entity.setIncidentKey(null);
    }

  }

  @Override
  public void flush(FlowNodeInstanceEntity entity, BatchRequest batchRequest)
      throws PersistenceException {

    logger.debug("Flow node instance: id {}", entity.getId());
    Map<String,Object> updateFields = new HashMap<>();
    updateFields.put(FlowNodeInstanceTemplate.INCIDENT_KEY, entity.getIncidentKey());
    batchRequest.upsert(flowNodeInstanceTemplate.getFullQualifiedName(), entity.getId(), entity, updateFields);    
    
  }
}
