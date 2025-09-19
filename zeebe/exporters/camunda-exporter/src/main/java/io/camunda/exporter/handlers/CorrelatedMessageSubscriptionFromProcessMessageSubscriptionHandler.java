/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.Set;

public class CorrelatedMessageSubscriptionFromProcessMessageSubscriptionHandler
    extends AbstractCorrelatedMessageSubscriptionHandler<ProcessMessageSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(ProcessMessageSubscriptionIntent.CORRELATED);

  private final String indexName;

  public CorrelatedMessageSubscriptionFromProcessMessageSubscriptionHandler(
      final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && ValueType.PROCESS_MESSAGE_SUBSCRIPTION == record.getValueType();
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  protected long getMessageKey(final ProcessMessageSubscriptionRecordValue value) {
    return value.getMessageKey();
  }

  @Override
  protected void updateEntityFromRecordValue(
      final ProcessMessageSubscriptionRecordValue value,
      final CorrelatedMessageSubscriptionEntity entity) {

    entity
        .setBpmnProcessId(value.getBpmnProcessId())
        .setCorrelationKey(value.getCorrelationKey())
        .setFlowNodeId(value.getElementId())
        .setFlowNodeInstanceKey(value.getElementInstanceKey())
        .setMessageKey(value.getMessageKey())
        .setMessageName(value.getMessageName())
        .setProcessDefinitionKey(null) // not available for process message subscriptions
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setSubscriptionType("PROCESS_EVENT")
        .setTenantId(tenantOrDefault(value.getTenantId()));
  }
}
