/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.exporter.rdbms.utils.ExportUtil.buildTreePath;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.service.IncidentWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentExportHandler implements RdbmsExportHandler<IncidentRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentExportHandler.class);

  private final IncidentWriter incidentWriter;

  public IncidentExportHandler(final IncidentWriter incidentWriter) {
    this.incidentWriter = incidentWriter;
  }

  @Override
  public boolean canExport(final Record<IncidentRecordValue> record) {
    return record.getValueType() == ValueType.INCIDENT;
  }

  @Override
  public void export(final Record<IncidentRecordValue> record) {
    if (record.getIntent().equals(IncidentIntent.CREATED)) {
      incidentWriter.create(map(record));
    } else if (record.getIntent().equals(IncidentIntent.RESOLVED)) {
      incidentWriter.resolve(record.getKey());
    } else if (record.getIntent().equals(IncidentIntent.MIGRATED)) {
      incidentWriter.update(map(record));
    } else {
      LOGGER.warn(
          "Unexpected incident intent {} for record {}/{}",
          record.getIntent(),
          record.getPartitionId(),
          record.getPosition());
    }
  }

  private IncidentDbModel map(final Record<IncidentRecordValue> record) {
    final var value = record.getValue();
    return new IncidentDbModel.Builder()
        .incidentKey(record.getKey())
        .flowNodeInstanceKey(value.getElementInstanceKey())
        .flowNodeId(value.getElementId())
        .processInstanceKey(mapIfGreaterZero(value.getProcessInstanceKey()))
        .processDefinitionKey(mapIfGreaterZero(value.getProcessDefinitionKey()))
        .processDefinitionId(value.getBpmnProcessId())
        .state(IncidentState.ACTIVE)
        .errorType(mapErrorType(value.getErrorType()))
        .errorMessage(value.getErrorMessage())
        .errorMessageHash(Optional.of(value.getErrorMessage()).map(String::hashCode).orElse(0))
        .creationDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .jobKey(mapIfGreaterZero(value.getJobKey()))
        .treePath(
            buildTreePath(
                record.getKey(), value.getProcessInstanceKey(), value.getElementInstancePath()))
        .tenantId(value.getTenantId())
        .build();
  }

  private Long mapIfGreaterZero(final Long key) {
    return key > 0 ? key : null;
  }

  private ErrorType mapErrorType(final io.camunda.zeebe.protocol.record.value.ErrorType errorType) {
    if (errorType == null) {
      return ErrorType.UNSPECIFIED;
    }
    try {
      return ErrorType.valueOf(errorType.name());
    } catch (final IllegalArgumentException ex) {
      return ErrorType.UNKNOWN;
    }
  }
}
