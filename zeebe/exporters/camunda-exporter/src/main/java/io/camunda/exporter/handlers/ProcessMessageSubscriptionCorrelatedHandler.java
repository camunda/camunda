/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.exporter.utils.ExporterUtil.toOffsetDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.CorrelatedMessageEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProcessMessageSubscriptionCorrelatedHandler
    implements ExportHandler<CorrelatedMessageEntity, RecordValueWithVariables> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(ProcessMessageSubscriptionIntent.CORRELATED);

  private final String indexName;
  private final ObjectMapper objectMapper;

  public ProcessMessageSubscriptionCorrelatedHandler(final String indexName) {
    this.indexName = indexName;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
  }

  @Override
  public Class<CorrelatedMessageEntity> getEntityType() {
    return CorrelatedMessageEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<RecordValueWithVariables> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && record.getValueType() == ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
  }

  @Override
  public List<String> generateIds(final Record<RecordValueWithVariables> record) {
    final var value = (ProcessMessageSubscriptionRecordValue) record.getValue();
    // Generate composite ID: messageKey + "-" + subscriptionKey (record.getKey())
    final String compositeId = value.getMessageKey() + "-" + record.getKey();
    return List.of(compositeId);
  }

  @Override
  public CorrelatedMessageEntity createNewEntity(final String id) {
    return new CorrelatedMessageEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<RecordValueWithVariables> record, final CorrelatedMessageEntity entity) {
    final var value = (ProcessMessageSubscriptionRecordValue) record.getValue();
    final String compositeId = value.getMessageKey() + "-" + record.getKey();
    
    entity
        .setId(compositeId)
        .setMessageKey(value.getMessageKey())
        .setSubscriptionKey(record.getKey()) // Use record's key as subscription key
        .setMessageName(value.getMessageName())
        .setCorrelationKey(value.getCorrelationKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setFlowNodeInstanceKey(value.getElementInstanceKey()) // flowNodeInstanceKey
        .setStartEventId(null) // not applicable for process message subscriptions
        .setElementId(value.getElementId()) // flowNodeId (was elementId)
        .setIsInterrupting(value.isInterrupting()) // isInterrupting
        .setProcessDefinitionKey(null) // not available in this record type
        .setBpmnProcessId(value.getBpmnProcessId())
        .setVersion(null) // would need process cache to retrieve
        .setVersionTag(null) // would need process cache to retrieve
        .setVariables(convertVariablesToJson(value.getVariables()))
        .setTenantId(tenantOrDefault(value.getTenantId()))
        .setDateTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
  }

  private String convertVariablesToJson(final Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (final JsonProcessingException e) {
      // Log error and return null or empty JSON object
      return "{}";
    }
  }

  @Override
  public void flush(final CorrelatedMessageEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}