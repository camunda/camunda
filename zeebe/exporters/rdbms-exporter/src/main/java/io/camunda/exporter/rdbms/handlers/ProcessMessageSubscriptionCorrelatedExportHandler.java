/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.DateUtil.toOffsetDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.db.rdbms.write.service.CorrelatedMessageWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.Map;
import java.util.Set;

public class ProcessMessageSubscriptionCorrelatedExportHandler
    implements RdbmsExportHandler<ProcessMessageSubscriptionRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(ProcessMessageSubscriptionIntent.CORRELATED);

  private final CorrelatedMessageWriter correlatedMessageWriter;
  private final ObjectMapper objectMapper;

  public ProcessMessageSubscriptionCorrelatedExportHandler(
      final CorrelatedMessageWriter correlatedMessageWriter) {
    this.correlatedMessageWriter = correlatedMessageWriter;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public boolean canExport(final Record<ProcessMessageSubscriptionRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public void export(final Record<ProcessMessageSubscriptionRecordValue> record) {
    final var value = record.getValue();
    correlatedMessageWriter.create(mapFromRecord(record, value));
  }

  private CorrelatedMessageDbModel mapFromRecord(
      final Record<ProcessMessageSubscriptionRecordValue> record,
      final ProcessMessageSubscriptionRecordValue value) {
    
    return new CorrelatedMessageDbModel(
        value.getMessageKey(),
        record.getKey(), // Use record's key as subscription key (already unique)
        value.getMessageName(),
        value.getCorrelationKey(),
        value.getProcessInstanceKey(),
        value.getElementInstanceKey(), // flowNodeInstanceKey
        value.getElementId(), // flowNodeId (merged from elementId/startEventId)
        value.isInterrupting(), // isInterrupting
        null, // processDefinitionKey - not available in this record type
        value.getBpmnProcessId(),
        null, // version - would need process cache to retrieve
        null, // versionTag - would need process cache to retrieve
        convertVariablesToJson(value.getVariables()),
        value.getTenantId(),
        toOffsetDateTime(record.getTimestamp()),
        record.getPartitionId(),
        null); // historyCleanupDate will be set by cleanup service
  }

  private String convertVariablesToJson(final Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (final JsonProcessingException e) {
      // Log error and return null or empty JSON object
      return "{}";
    }
  }
}