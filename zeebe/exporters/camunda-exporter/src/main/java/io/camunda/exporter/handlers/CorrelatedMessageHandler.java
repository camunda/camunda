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

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.CorrelatedMessageEntity;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.List;
import java.util.Set;

public class CorrelatedMessageHandler implements ExportHandler<CorrelatedMessageEntity, Object> {

  private static final Set<Intent> CORRELATED_INTENTS =
      Set.of(
          ProcessMessageSubscriptionIntent.CORRELATED,
          MessageStartEventSubscriptionIntent.CORRELATED);

  private final String indexName;

  public CorrelatedMessageHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return null; // We handle multiple value types
  }

  @Override
  public Class<CorrelatedMessageEntity> getEntityType() {
    return CorrelatedMessageEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<Object> record) {
    return CORRELATED_INTENTS.contains(record.getIntent())
        && (record.getValueType() == ValueType.PROCESS_MESSAGE_SUBSCRIPTION
            || record.getValueType() == ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
  }

  @Override
  public List<String> generateIds(final Record<Object> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public CorrelatedMessageEntity createNewEntity(final String id) {
    return new CorrelatedMessageEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<Object> record, final CorrelatedMessageEntity entity) {
    entity
        .setId(String.valueOf(record.getKey()))
        .setKey(record.getKey())
        .setDateTime(toOffsetDateTime(record.getTimestamp()));

    if (record.getIntent() == ProcessMessageSubscriptionIntent.CORRELATED) {
      final var processMessageRecord = (ProcessMessageSubscriptionRecordValue) record.getValue();
      updateFromProcessMessageSubscription(entity, processMessageRecord);
    } else if (record.getIntent() == MessageStartEventSubscriptionIntent.CORRELATED) {
      final var messageStartRecord = (MessageStartEventSubscriptionRecordValue) record.getValue();
      updateFromMessageStartEventSubscription(entity, messageStartRecord);
    }
  }

  private void updateFromProcessMessageSubscription(
      final CorrelatedMessageEntity entity, final ProcessMessageSubscriptionRecordValue value) {
    entity
        .setMessageKey(value.getMessageKey())
        .setMessageName(value.getMessageName())
        .setCorrelationKey(value.getCorrelationKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setFlowNodeInstanceKey(value.getElementInstanceKey()) // flowNodeInstanceKey
        .setStartEventId(null) // not applicable for process message subscriptions
        .setBpmnProcessId(value.getBpmnProcessId())
        .setVariables(MsgPackConverter.convertToJson(value.getVariables()))
        .setTenantId(tenantOrDefault(value.getTenantId()));
  }

  private void updateFromMessageStartEventSubscription(
      final CorrelatedMessageEntity entity, final MessageStartEventSubscriptionRecordValue value) {
    entity
        .setMessageKey(value.getMessageKey())
        .setMessageName(value.getMessageName())
        .setCorrelationKey(value.getCorrelationKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setFlowNodeInstanceKey(null) // not applicable for message start events
        .setStartEventId(value.getStartEventId())
        .setBpmnProcessId(value.getBpmnProcessId())
        .setVariables(MsgPackConverter.convertToJson(value.getVariables()))
        .setTenantId(tenantOrDefault(value.getTenantId()));
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