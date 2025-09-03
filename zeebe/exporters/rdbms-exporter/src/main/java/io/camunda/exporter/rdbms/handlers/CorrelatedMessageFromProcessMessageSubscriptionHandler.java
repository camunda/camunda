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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.Set;

public class CorrelatedMessageFromProcessMessageSubscriptionHandler
    extends AbstractCorrelatedMessageHandler<ProcessMessageSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS = Set.of(ProcessMessageSubscriptionIntent.CORRELATED);

  public CorrelatedMessageFromProcessMessageSubscriptionHandler(
      final CorrelatedMessageWriter correlatedMessageWriter) {
    super(correlatedMessageWriter);
  }

  @Override
  protected Set<Intent> getSupportedIntents() {
    return SUPPORTED_INTENTS;
  }

  @Override
  protected CorrelatedMessageDbModel map(final Record<ProcessMessageSubscriptionRecordValue> record) {
    final ProcessMessageSubscriptionRecordValue value = record.getValue();
    
    return buildBaseModel(record)
        .subscriptionKey(record.getKey())
        .messageKey(value.getMessageKey())
        .messageName(value.getMessageName())
        .correlationKey(value.getCorrelationKey())
        .processInstanceKey(value.getProcessInstanceKey())
        .flowNodeInstanceKey(value.getElementInstanceKey())
        .flowNodeId(value.getElementId())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(null) // Not available in ProcessMessageSubscriptionRecord
        .tenantId(value.getTenantId())
        .build();
  }
}