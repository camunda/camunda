package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.exporter.operate.schema.templates.EventTemplate.METADATA;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventMetadataEntity;
import io.camunda.operate.entities.EventSourceType;
import io.camunda.operate.entities.EventType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.EventTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventFromMessageSubscriptionHandler
    implements ExportHandler<EventEntity, ProcessMessageSubscriptionRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EventFromMessageSubscriptionHandler.class);

  private static final String ID_PATTERN = "%s_%s";
  private static final Set<Intent> PROCESS_MESSAGE_SUBSCRIPTION_STATES = new HashSet<>();

  private EventTemplate eventTemplate;

  static {
    PROCESS_MESSAGE_SUBSCRIPTION_STATES.add(ProcessMessageSubscriptionIntent.CREATED);
  }

  public EventFromMessageSubscriptionHandler(EventTemplate eventTemplate) {
    this.eventTemplate = eventTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.MESSAGE_SUBSCRIPTION;
  }

  @Override
  public Class<EventEntity> getEntityType() {
    return EventEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessMessageSubscriptionRecordValue> record) {
    return PROCESS_MESSAGE_SUBSCRIPTION_STATES.contains(record.getIntent());
  }

  @Override
  public String generateId(Record<ProcessMessageSubscriptionRecordValue> record) {
    final ProcessMessageSubscriptionRecordValue recordValue = record.getValue();
    return String.format(
        ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementInstanceKey());
  }

  @Override
  public EventEntity createNewEntity(String id) {
    return new EventEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<ProcessMessageSubscriptionRecordValue> record, EventEntity eventEntity) {

    final ProcessMessageSubscriptionRecordValue recordValue = record.getValue();

    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getPartitionId());
    eventEntity.setEventSourceType(
        EventSourceType.fromZeebeValueType(
            record.getValueType() == null ? null : record.getValueType().name()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getIntent().name()));

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      eventEntity.setProcessInstanceKey(processInstanceKey);
    }

    eventEntity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      eventEntity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    final EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setMessageName(recordValue.getMessageName());
    eventMetadata.setCorrelationKey(recordValue.getCorrelationKey());

    eventEntity.setMetadata(eventMetadata);
  }

  @Override
  public void flush(EventEntity entity, BatchRequest batchRequest) throws PersistenceException {
    LOGGER.debug(
        "Event: id {}, eventSourceType {}, eventType {}, processInstanceKey {}",
        entity.getId(),
        entity.getEventSourceType(),
        entity.getEventType(),
        entity.getProcessInstanceKey());
    final Map<String, Object> jsonMap = new HashMap<>();
    jsonMap.put(EventTemplate.KEY, entity.getKey());
    jsonMap.put(EventTemplate.EVENT_SOURCE_TYPE, entity.getEventSourceType());
    jsonMap.put(EventTemplate.EVENT_TYPE, entity.getEventType());
    jsonMap.put(EventTemplate.DATE_TIME, entity.getDateTime());
    if (entity.getMetadata() != null) {
      final Map<String, Object> metadataMap = new HashMap<>();
      if (entity.getMetadata().getIncidentErrorMessage() != null) {
        metadataMap.put(
            EventTemplate.INCIDENT_ERROR_MSG, entity.getMetadata().getIncidentErrorMessage());
        metadataMap.put(
            EventTemplate.INCIDENT_ERROR_TYPE, entity.getMetadata().getIncidentErrorType());
      }
      if (entity.getMetadata().getJobKey() != null) {
        metadataMap.put(EventTemplate.JOB_KEY, entity.getMetadata().getJobKey());
      }
      if (entity.getMetadata().getJobType() != null) {
        metadataMap.put(EventTemplate.JOB_TYPE, entity.getMetadata().getJobType());
        metadataMap.put(EventTemplate.JOB_RETRIES, entity.getMetadata().getJobRetries());
        metadataMap.put(EventTemplate.JOB_WORKER, entity.getMetadata().getJobWorker());
        metadataMap.put(EventTemplate.JOB_KEY, entity.getMetadata().getJobKey());
        metadataMap.put(
            EventTemplate.JOB_CUSTOM_HEADERS, entity.getMetadata().getJobCustomHeaders());
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
