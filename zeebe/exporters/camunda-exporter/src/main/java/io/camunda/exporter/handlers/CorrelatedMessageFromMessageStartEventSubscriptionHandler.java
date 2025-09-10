/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.webapps.schema.entities.CorrelatedMessageEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.util.Set;

public class CorrelatedMessageFromMessageStartEventSubscriptionHandler
    extends AbstractCorrelatedMessageHandler<MessageStartEventSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(MessageStartEventSubscriptionIntent.CORRELATED);

  private final String indexName;

  public CorrelatedMessageFromMessageStartEventSubscriptionHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.MESSAGE_START_EVENT_SUBSCRIPTION;
  }

  @Override
  public boolean handlesRecord(final Record<MessageStartEventSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && ValueType.MESSAGE_START_EVENT_SUBSCRIPTION == record.getValueType();
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  protected long getMessageKey(final MessageStartEventSubscriptionRecordValue value) {
    return value.getMessageKey();
  }

  @Override
  protected void updateEntityFromRecordValue(
      final MessageStartEventSubscriptionRecordValue value, final CorrelatedMessageEntity entity) {

    entity
        .setBpmnProcessId(value.getBpmnProcessId())
        .setCorrelationKey(value.getCorrelationKey())
        .setFlowNodeId(value.getStartEventId())
        .setFlowNodeInstanceKey(null) // not available for start event subscriptions
        .setMessageKey(value.getMessageKey())
        .setMessageName(value.getMessageName())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setSubscriptionType("START_EVENT")
        .setTenantId(tenantOrDefault(value.getTenantId()));
  }
}
