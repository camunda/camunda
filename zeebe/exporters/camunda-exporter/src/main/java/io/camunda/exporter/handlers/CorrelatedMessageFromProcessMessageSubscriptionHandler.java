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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CorrelatedMessageFromProcessMessageSubscriptionHandler(String indexName)
    implements ExportHandler<CorrelatedMessageEntity, ProcessMessageSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(ProcessMessageSubscriptionIntent.CORRELATED);

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
  }

  @Override
  public Class<CorrelatedMessageEntity> getEntityType() {
    return CorrelatedMessageEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && ValueType.PROCESS_MESSAGE_SUBSCRIPTION == record.getValueType();
  }

  @Override
  public List<String> generateIds(final Record<ProcessMessageSubscriptionRecordValue> record) {
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
      final Record<ProcessMessageSubscriptionRecordValue> record,
      final CorrelatedMessageEntity entity) {
    final var value = record.getValue();

    entity
        .setBpmnProcessId(value.getBpmnProcessId())
        .setCorrelationKey(value.getCorrelationKey())
        .setCorrelationTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .setFlowNodeId(value.getElementId())
        .setFlowNodeInstanceKey(value.getElementInstanceKey())
        .setMessageKey(value.getMessageKey())
        .setMessageName(value.getMessageName())
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setProcessDefinitionKey(null) // not available for process message subscriptions
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
