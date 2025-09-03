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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CorrelatedMessageFromMessageStartEventSubscriptionHandler(String indexName)
    implements ExportHandler<CorrelatedMessageEntity, MessageStartEventSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(MessageStartEventSubscriptionIntent.CORRELATED);

  @Override
  public ValueType getHandledValueType() {
    return ValueType.MESSAGE_START_EVENT_SUBSCRIPTION;
  }

  @Override
  public Class<CorrelatedMessageEntity> getEntityType() {
    return CorrelatedMessageEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<MessageStartEventSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && ValueType.MESSAGE_START_EVENT_SUBSCRIPTION == record.getValueType();
  }

  @Override
  public List<String> generateIds(final Record<MessageStartEventSubscriptionRecordValue> record) {
    final var value = record.getValue();
    // composite id: messageKey_subscriptionKey
    return List.of("%s_%s".formatted(value.getMessageKey(), record.getKey()));
  }

  @Override
  public CorrelatedMessageEntity createNewEntity(final String id) {
    return new CorrelatedMessageEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<MessageStartEventSubscriptionRecordValue> record,
      final CorrelatedMessageEntity entity) {
    final var value = record.getValue();

    entity
        .setBpmnProcessId(value.getBpmnProcessId())
        .setCorrelationKey(value.getCorrelationKey())
        .setCorrelationTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .setFlowNodeId(value.getStartEventId())
        .setFlowNodeInstanceKey(null) // not available for start event subscriptions
        .setMessageKey(value.getMessageKey())
        .setMessageName(value.getMessageName())
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setSubscriptionKey(record.getKey())
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
