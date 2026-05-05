/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.toOffsetDateTime;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.CORRELATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.DATE_TIME;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.EVENT_SOURCE_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.INBOUND_CONNECTOR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.INCIDENT_ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.INCIDENT_ERROR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.JOB_CUSTOM_HEADERS;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.JOB_RETRIES;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.JOB_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.JOB_WORKER;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_NAME;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_SUBSCRIPTION_STATE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_SUBSCRIPTION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.METADATA;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_DEFINITION_NAME;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_DEFINITION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.TOOL_NAME;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.messagesubscription.EventSourceType;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.utils.ProcessCacheUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractEventHandler<R extends RecordValue>
    implements ExportHandler<MessageSubscriptionEntity, R> {
  protected static final String ID_PATTERN = "%s_%s";
  protected final String indexName;

  public AbstractEventHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public Class<MessageSubscriptionEntity> getEntityType() {
    return MessageSubscriptionEntity.class;
  }

  @Override
  public MessageSubscriptionEntity createNewEntity(final String id) {
    return new MessageSubscriptionEntity().setId(id);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  protected void loadEventGeneralData(
      final Record<R> record, final MessageSubscriptionEntity messageSubscriptionEntity) {
    messageSubscriptionEntity.setKey(record.getKey());
    messageSubscriptionEntity.setPartitionId(record.getPartitionId());
    messageSubscriptionEntity.setEventSourceType(
        EventSourceType.fromZeebeValueType(
            record.getValueType() == null ? null : record.getValueType().name()));
    messageSubscriptionEntity.setDateTime(
        toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    messageSubscriptionEntity.setEventType(
        MessageSubscriptionState.fromZeebeIntent(record.getIntent().name()));
  }

  protected void persistEvent(
      final MessageSubscriptionEntity entity,
      final String positionFieldName,
      final long positionFieldValue,
      final BatchRequest batchRequest) {

    final Map<String, Object> jsonMap = new HashMap<>();
    jsonMap.put(KEY, entity.getKey());
    jsonMap.put(EVENT_SOURCE_TYPE, entity.getEventSourceType());
    jsonMap.put(MESSAGE_SUBSCRIPTION_STATE, entity.getEventType());
    jsonMap.put(MESSAGE_SUBSCRIPTION_TYPE, entity.getMessageSubscriptionType());
    jsonMap.put(DATE_TIME, entity.getDateTime());
    jsonMap.put(PROCESS_KEY, entity.getProcessDefinitionKey());
    jsonMap.put(BPMN_PROCESS_ID, entity.getBpmnProcessId());
    jsonMap.put(FLOW_NODE_ID, entity.getFlowNodeId());
    jsonMap.put(PROCESS_DEFINITION_NAME, entity.getProcessDefinitionName());
    jsonMap.put(PROCESS_DEFINITION_VERSION, entity.getProcessDefinitionVersion());
    jsonMap.put(TOOL_NAME, entity.getToolName());
    jsonMap.put(INBOUND_CONNECTOR_TYPE, entity.getInboundConnectorType());
    jsonMap.put(positionFieldName, positionFieldValue);
    if (entity.getMetadata() != null) {
      final Map<String, Object> metadataMap = new HashMap<>();
      if (entity.getMetadata().getIncidentErrorMessage() != null) {
        metadataMap.put(INCIDENT_ERROR_MSG, entity.getMetadata().getIncidentErrorMessage());
        metadataMap.put(INCIDENT_ERROR_TYPE, entity.getMetadata().getIncidentErrorType());
      }
      if (entity.getMetadata().getJobKey() != null) {
        metadataMap.put(JOB_KEY, entity.getMetadata().getJobKey());
      }
      if (entity.getMetadata().getJobType() != null) {
        metadataMap.put(JOB_TYPE, entity.getMetadata().getJobType());
        metadataMap.put(JOB_RETRIES, entity.getMetadata().getJobRetries());
        metadataMap.put(JOB_WORKER, entity.getMetadata().getJobWorker());
        metadataMap.put(JOB_CUSTOM_HEADERS, entity.getMetadata().getJobCustomHeaders());
      }
      if (entity.getMetadata().getMessageName() != null) {
        metadataMap.put(MESSAGE_NAME, entity.getMetadata().getMessageName());
        metadataMap.put(CORRELATION_KEY, entity.getMetadata().getCorrelationKey());
      }
      if (!metadataMap.isEmpty()) {
        jsonMap.put(METADATA, metadataMap);
      }
    }

    // write event
    batchRequest.upsert(indexName, entity.getId(), entity, jsonMap);
  }

  protected void extractDefinitionData(
      final MessageSubscriptionEntity entity,
      final String elementId,
      final long processDefinitionKey,
      final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    if (processDefinitionKey > 0) {
      entity.setProcessDefinitionKey(processDefinitionKey);
      final var cached = processCache.get(processDefinitionKey);
      entity.setProcessDefinitionName(
          cached.map(CachedProcessEntity::name).filter(s -> !s.isBlank()).orElse(null));
      entity.setProcessDefinitionVersion(cached.map(CachedProcessEntity::version).orElse(null));
      final Map<String, String> ext =
          cached
              .map(CachedProcessEntity::elementExtensionProperties)
              .map(p -> p.get(elementId))
              .orElse(Map.of());
      entity
          .setExtensionProperties(ext)
          .setToolName(ProcessCacheUtil.getToolName(ext))
          .setInboundConnectorType(ProcessCacheUtil.getInboundConnectorType(ext));
    }
  }
}
