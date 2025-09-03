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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.time.Instant;
import java.util.Set;

public class MessageCorrelationExportHandler
    implements RdbmsExportHandler<ProcessMessageSubscriptionRecordValue> {

  private static final Set<Intent> STATES = Set.of(ProcessMessageSubscriptionIntent.CORRELATED);
  private final MessageCorrelationWriter messageCorrelationWriter;

  public MessageCorrelationExportHandler(
      final MessageCorrelationWriter messageCorrelationWriter) {
    this.messageCorrelationWriter = messageCorrelationWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public void export(final Record<ProcessMessageSubscriptionRecordValue> record) {
    if (record.getIntent() == ProcessMessageSubscriptionIntent.CORRELATED) {
      messageCorrelationWriter.create(map(record));
    }
  }

  private MessageCorrelationDbModel map(final Record<ProcessMessageSubscriptionRecordValue> record) {
    final ProcessMessageSubscriptionRecordValue value = record.getValue();
    
    return new MessageCorrelationDbModel.Builder()
        .subscriptionKey(record.getKey())
        .messageKey(value.getMessageKey())
        .messageName(value.getMessageName())
        .correlationKey(value.getCorrelationKey())
        .processInstanceKey(value.getProcessInstanceKey())
        .flowNodeInstanceKey(value.getElementInstanceKey())
        .flowNodeId(value.getElementId())
        .bpmnProcessId(value.getBpmnProcessId())
        .processDefinitionKey(null) // Not available in ProcessMessageSubscriptionRecord
        .tenantId(value.getTenantId())
        .dateTime(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .partitionId(record.getPartitionId())
        .build();
  }
}