package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.IncidentTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

public class IncidentHandler implements ExportHandler<IncidentEntity, IncidentRecordValue> {

  private static final Logger logger = LoggerFactory.getLogger(IncidentHandler.class);

  private IncidentTemplate incidentTemplate;

  
  // TODO: Did not port over the webhook call that notifies users of a new incident
  
  public IncidentHandler(IncidentTemplate incidentTemplate) {
    this.incidentTemplate = incidentTemplate;
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
    return ConversionUtils.toStringOrNull(record.getKey());
  }

  @Override
  public IncidentEntity createNewEntity(String id) {
    return new IncidentEntity()
        .setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, IncidentEntity incident) {

    Intent intent = record.getIntent();
    Long incidentKey = record.getKey();
    IncidentRecordValue recordValue = record.getValue();
    
    if (intent == IncidentIntent.RESOLVED) {

      // TODO: restore completing operations
//      //resolve corresponding operation
//      operationsManager.completeOperation(null, recordValue.getProcessInstanceKey(), incidentKey,
//          OperationType.RESOLVE_INCIDENT, batchRequest);
//      //resolved incident is not updated directly, only in post importer
    } else if (intent == IncidentIntent.CREATED) {
      incident
        .setKey(incidentKey)
        .setPartitionId(record.getPartitionId());
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getProcessInstanceKey() > 0) {
        incident.setProcessInstanceKey(recordValue.getProcessInstanceKey());
      }
      if (recordValue.getProcessDefinitionKey() > 0) {
        incident.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
      }
      incident.setBpmnProcessId(recordValue.getBpmnProcessId());
      String errorMessage = StringUtils.trimWhitespace(recordValue.getErrorMessage());
      incident.setErrorMessage(errorMessage)
          .setErrorType(ErrorType.fromZeebeErrorType(recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()))
          .setFlowNodeId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() > 0) {
        incident.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
      }
      incident.setState(IncidentState.PENDING)
          .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
          .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    }
  }

  @Override
  public void flush(IncidentEntity incident, BatchRequest batchRequest) throws PersistenceException {

    logger.debug("Index incident: id {}", incident.getId());
    //we only insert incidents but never update -> update will be performed in post importer
    batchRequest.upsert(incidentTemplate.getFullQualifiedName(), String.valueOf(incident.getKey()), incident, Map.of());
  }
}
