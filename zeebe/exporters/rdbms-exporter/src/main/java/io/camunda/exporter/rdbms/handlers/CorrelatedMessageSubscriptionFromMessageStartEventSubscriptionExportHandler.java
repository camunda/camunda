/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.ExportUtil.tenantOrDefault;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel.Builder;
import io.camunda.db.rdbms.write.service.CorrelatedMessageSubscriptionWriter;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.util.Set;

public class CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionExportHandler
    extends AbstractCorrelatedMessageSubscriptionExportHandler<
        MessageStartEventSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(MessageStartEventSubscriptionIntent.CORRELATED);

  public CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionExportHandler(
      final CorrelatedMessageSubscriptionWriter correlatedMessageSubscriptionWriter) {
    super(correlatedMessageSubscriptionWriter);
  }

  @Override
  protected void mapValue(
      final MessageStartEventSubscriptionRecordValue value, final Builder builder) {
    builder
        .correlationKey(value.getCorrelationKey())
        .flowNodeId(value.getStartEventId())
        .flowNodeInstanceKey(null) // not available for start event subscriptions
        .messageKey(value.getMessageKey())
        .messageName(value.getMessageName())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processInstanceKey(value.getProcessInstanceKey())
        // processes started via message are always root process instances
        .rootProcessInstanceKey(value.getProcessInstanceKey())
        .subscriptionType(MessageSubscriptionType.START_EVENT)
        .tenantId(tenantOrDefault(value.getTenantId()));
  }

  @Override
  public boolean canExport(final Record<MessageStartEventSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && ValueType.MESSAGE_START_EVENT_SUBSCRIPTION == record.getValueType();
  }
}
