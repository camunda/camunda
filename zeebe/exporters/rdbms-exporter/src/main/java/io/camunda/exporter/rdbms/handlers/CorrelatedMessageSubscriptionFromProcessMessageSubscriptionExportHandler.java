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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.Set;

public class CorrelatedMessageSubscriptionFromProcessMessageSubscriptionExportHandler
    extends AbstractCorrelatedMessageSubscriptionExportHandler<
        ProcessMessageSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(ProcessMessageSubscriptionIntent.CORRELATED);

  public CorrelatedMessageSubscriptionFromProcessMessageSubscriptionExportHandler(
      final CorrelatedMessageSubscriptionWriter correlatedMessageSubscriptionWriter) {
    super(correlatedMessageSubscriptionWriter);
  }

  @Override
  protected void mapValue(
      final ProcessMessageSubscriptionRecordValue value, final Builder builder) {
    builder
        .correlationKey(value.getCorrelationKey())
        .flowNodeId(value.getElementId())
        .flowNodeInstanceKey(value.getElementInstanceKey())
        .messageKey(value.getMessageKey())
        .messageName(value.getMessageName())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(null) // not available for process message subscriptions
        .processInstanceKey(value.getProcessInstanceKey())
        .subscriptionType(MessageSubscriptionType.PROCESS_EVENT)
        .tenantId(tenantOrDefault(value.getTenantId()));
  }

  @Override
  public boolean canExport(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent())
        && ValueType.PROCESS_MESSAGE_SUBSCRIPTION == record.getValueType();
  }
}
