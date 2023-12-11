package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.exporter.operate.schema.templates.EventTemplate.METADATA;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventSourceType;
import io.camunda.operate.entities.EventType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.EventTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class EventFromProcessInstanceHandler
    implements ExportHandler<EventEntity, ProcessInstanceRecordValue> {

  private static final String ID_PATTERN = "%s_%s";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(EventFromProcessInstanceHandler.class);

  private EventTemplate eventTemplate;

  public EventFromProcessInstanceHandler(EventTemplate eventTemplate) {
    this.eventTemplate = eventTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<EventEntity> getEntityType() {
    return EventEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {

    // TODO: filtering by intents?

    return !isProcessEvent(record.getValue());
  }

  private boolean isProcessEvent(ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(ProcessInstanceRecordValue recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

  @Override
  public String generateId(Record<ProcessInstanceRecordValue> record) {
    return String.format(ID_PATTERN, record.getValue().getProcessInstanceKey(), record.getKey());
  }

  @Override
  public EventEntity createNewEntity(String id) {
    return new EventEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<ProcessInstanceRecordValue> record, EventEntity entity) {

    final ProcessInstanceRecordValue recordValue = record.getValue();

    if (!isProcessEvent(recordValue)) { // we do not need to store process level events
      final EventEntity eventEntity = new EventEntity()
          .setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), record.getKey()));

      loadEventGeneralData(record, eventEntity);

      eventEntity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
          .setProcessInstanceKey(recordValue.getProcessInstanceKey())
          .setBpmnProcessId(recordValue.getBpmnProcessId())
          .setTenantId(tenantOrDefault(recordValue.getTenantId()));

      if (recordValue.getElementId() != null) {
        eventEntity.setFlowNodeId(recordValue.getElementId());
      }

      if (record.getKey() != recordValue.getProcessInstanceKey()) {
        eventEntity.setFlowNodeInstanceKey(record.getKey());
      }
    }
  }

  private void loadEventGeneralData(Record record, EventEntity eventEntity) {
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getPartitionId());
    eventEntity.setEventSourceType(EventSourceType
        .fromZeebeValueType(record.getValueType() == null ? null : record.getValueType().name()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getIntent().name()));
  }


  @Override
  public void flush(EventEntity entity, BatchRequest batchRequest) throws PersistenceException {
    persistEvent(entity, batchRequest);

  }

  private void persistEvent(EventEntity entity, BatchRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug("Event: id {}, eventSourceType {}, eventType {}, processInstanceKey {}",
        entity.getId(), entity.getEventSourceType(), entity.getEventType(),
        entity.getProcessInstanceKey());
    final Map<String, Object> jsonMap = new HashMap<>();
    jsonMap.put(EventTemplate.KEY, entity.getKey());
    jsonMap.put(EventTemplate.EVENT_SOURCE_TYPE, entity.getEventSourceType());
    jsonMap.put(EventTemplate.EVENT_TYPE, entity.getEventType());
    jsonMap.put(EventTemplate.DATE_TIME, entity.getDateTime());
    if (entity.getMetadata() != null) {
      final Map<String, Object> metadataMap = new HashMap<>();
      if (entity.getMetadata().getIncidentErrorMessage() != null) {
        metadataMap.put(EventTemplate.INCIDENT_ERROR_MSG,
            entity.getMetadata().getIncidentErrorMessage());
        metadataMap.put(EventTemplate.INCIDENT_ERROR_TYPE,
            entity.getMetadata().getIncidentErrorType());
      }
      if (entity.getMetadata().getJobKey() != null) {
        metadataMap.put(EventTemplate.JOB_KEY, entity.getMetadata().getJobKey());
      }
      if (entity.getMetadata().getJobType() != null) {
        metadataMap.put(EventTemplate.JOB_TYPE, entity.getMetadata().getJobType());
        metadataMap.put(EventTemplate.JOB_RETRIES, entity.getMetadata().getJobRetries());
        metadataMap.put(EventTemplate.JOB_WORKER, entity.getMetadata().getJobWorker());
        metadataMap.put(EventTemplate.JOB_KEY, entity.getMetadata().getJobKey());
        metadataMap.put(EventTemplate.JOB_CUSTOM_HEADERS,
            entity.getMetadata().getJobCustomHeaders());
      }
      if (entity.getMetadata().getMessageName() != null) {
        metadataMap.put(EventTemplate.MESSAGE_NAME, entity.getMetadata().getMessageName());
        metadataMap.put(EventTemplate.CORRELATION_KEY, entity.getMetadata().getCorrelationKey());
      }
      if (metadataMap.size() > 0) {
        jsonMap.put(METADATA, metadataMap);
      }
    }
    // write event
    batchRequest.upsert(eventTemplate.getFullQualifiedName(), entity.getId(), entity, jsonMap);
  }

  @Override
  public String getIndexName() {
    return eventTemplate.getFullQualifiedName();
  }
}
