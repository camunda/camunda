/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.db.rdbms.write.service.CorrelatedMessageWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.util.Set;

public class CorrelatedMessageFromMessageStartEventSubscriptionHandler
    extends AbstractCorrelatedMessageHandler<MessageStartEventSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS = Set.of(MessageStartEventSubscriptionIntent.CORRELATED);

  public CorrelatedMessageFromMessageStartEventSubscriptionHandler(
      final CorrelatedMessageWriter correlatedMessageWriter) {
    super(correlatedMessageWriter);
  }

  @Override
  protected Set<Intent> getSupportedIntents() {
    return SUPPORTED_INTENTS;
  }

  @Override
  protected CorrelatedMessageDbModel map(final Record<MessageStartEventSubscriptionRecordValue> record) {
    final MessageStartEventSubscriptionRecordValue value = record.getValue();
    
    return buildBaseModel(record)
        .subscriptionKey(record.getKey())
        .messageKey(value.getMessageKey())
        .messageName(value.getMessageName())
        .correlationKey(value.getCorrelationKey())
        .processInstanceKey(value.getProcessInstanceKey())
        .flowNodeInstanceKey(null) // Not available for start events
        .flowNodeId(value.getStartEventId())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .tenantId(value.getTenantId())
        .build();
  }
}