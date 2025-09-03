/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.DateUtil.toOffsetDateTime;

import io.camunda.db.rdbms.write.domain.MessageCorrelationDbModel;
import io.camunda.db.rdbms.write.service.MessageCorrelationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.time.Instant;
import java.util.Set;

public class MessageStartEventSubscriptionCorrelationExportHandler
    implements RdbmsExportHandler<MessageStartEventSubscriptionRecordValue> {

  private static final Set<Intent> STATES = Set.of(MessageStartEventSubscriptionIntent.CORRELATED);
  private final MessageCorrelationWriter messageCorrelationWriter;

  public MessageStartEventSubscriptionCorrelationExportHandler(
      final MessageCorrelationWriter messageCorrelationWriter) {
    this.messageCorrelationWriter = messageCorrelationWriter;
  }

  @Override
  public boolean canExport(final Record<MessageStartEventSubscriptionRecordValue> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public void export(final Record<MessageStartEventSubscriptionRecordValue> record) {
    if (record.getIntent() == MessageStartEventSubscriptionIntent.CORRELATED) {
      messageCorrelationWriter.create(map(record));
    }
  }

  private MessageCorrelationDbModel map(final Record<MessageStartEventSubscriptionRecordValue> record) {
    final MessageStartEventSubscriptionRecordValue value = record.getValue();
    
    return new MessageCorrelationDbModel.MessageCorrelationDbModelBuilder()
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
        .correlationTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .partitionId(record.getPartitionId())
        .build();
  }
}