/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.AuditLogConfiguration;
import io.camunda.webapps.schema.entities.operation.AuditLogEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import java.util.Map;
import java.util.Set;

public class ProcessInstanceModificationAuditLogHandler
    extends AbstractAuditLogHandler<ProcessInstanceModificationRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(ProcessInstanceModificationIntent.MODIFIED);
  private static final Set<RejectionType> SUPPORTED_REJECTION_TYPES =
      Set.of(RejectionType.INVALID_STATE);

  public ProcessInstanceModificationAuditLogHandler(
      final String indexName,
      final AuditLogConfiguration auditLogConfiguration,
      final ObjectMapper objectMapper) {
    super(indexName, SUPPORTED_REJECTION_TYPES, auditLogConfiguration, objectMapper);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE_MODIFICATION;
  }

  @Override
  protected boolean handlesIntents(final Record<ProcessInstanceModificationRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  protected void getOperationSpecificData(
      final ProcessInstanceModificationRecordValue recordValue, final AuditLogEntity entity) {
    final var instructionsMap =
        Map.of(
            "activateInstructions",
            recordValue.getActivateInstructions(),
            "terminateInstructions",
            recordValue.getTerminateInstructions());
    entity.setDetails(toJsonDetails(instructionsMap));

    // TODO: add note field to record metadata
    //    if (recordValue.getOperationNote() != null && !recordValue.getOperationNote().isEmpty()) {
    //      entity.setNote(recordValue.getOperationNote());
    //    }
  }
}
