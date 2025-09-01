/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.DateUtil.toOffsetDateTime;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.db.rdbms.write.service.CorrelatedMessageWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.Set;

public class CorrelatedMessageExportHandler implements RdbmsExportHandler<Object> {

  private static final Set<Intent> CORRELATED_INTENTS =
      Set.of(
          ProcessMessageSubscriptionIntent.CORRELATED,
          MessageStartEventSubscriptionIntent.CORRELATED);

  private final CorrelatedMessageWriter correlatedMessageWriter;

  public CorrelatedMessageExportHandler(final CorrelatedMessageWriter correlatedMessageWriter) {
    this.correlatedMessageWriter = correlatedMessageWriter;
  }

  @Override
  public boolean canExport(final Record<Object> record) {
    return CORRELATED_INTENTS.contains(record.getIntent());
  }

  @Override
  public void export(final Record<Object> record) {
    if (record.getIntent() == ProcessMessageSubscriptionIntent.CORRELATED) {
      final var processMessageRecord = (ProcessMessageSubscriptionRecordValue) record.getValue();
      correlatedMessageWriter.create(mapFromProcessMessageSubscription(record, processMessageRecord));
    } else if (record.getIntent() == MessageStartEventSubscriptionIntent.CORRELATED) {
      final var messageStartRecord = (MessageStartEventSubscriptionRecordValue) record.getValue();
      correlatedMessageWriter.create(mapFromMessageStartEventSubscription(record, messageStartRecord));
    }
  }

  private CorrelatedMessageDbModel mapFromProcessMessageSubscription(
      final Record<Object> record, final ProcessMessageSubscriptionRecordValue value) {
    return new CorrelatedMessageDbModel(
        record.getKey(), // Use record key as primary key
        value.getMessageKey(),
        value.getMessageName(),
        value.getCorrelationKey(),
        value.getProcessInstanceKey(),
        value.getElementInstanceKey(), // flowNodeInstanceKey
        null, // startEventId - not applicable for process message subscriptions
        value.getBpmnProcessId(),
        MsgPackConverter.convertToJson(value.getVariables()),
        value.getTenantId(),
        toOffsetDateTime(record.getTimestamp()),
        record.getPartitionId(),
        null); // historyCleanupDate will be set by cleanup service
  }

  private CorrelatedMessageDbModel mapFromMessageStartEventSubscription(
      final Record<Object> record, final MessageStartEventSubscriptionRecordValue value) {
    return new CorrelatedMessageDbModel(
        record.getKey(), // Use record key as primary key
        value.getMessageKey(),
        value.getMessageName(),
        value.getCorrelationKey(),
        value.getProcessInstanceKey(),
        null, // flowNodeInstanceKey - not applicable for message start events
        value.getStartEventId(), // startEventId
        value.getBpmnProcessId(),
        MsgPackConverter.convertToJson(value.getVariables()),
        value.getTenantId(),
        toOffsetDateTime(record.getTimestamp()),
        record.getPartitionId(),
        null); // historyCleanupDate will be set by cleanup service
  }
}