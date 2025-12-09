/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationResult;
import io.camunda.zeebe.exporter.common.handlers.auditlog.AuditLogCommonHandler;
import io.camunda.zeebe.exporter.common.handlers.auditlog.AuditLogOperationTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.DateUtil;
import org.apache.commons.lang3.RandomStringUtils;

public class AuditLogExportHandler<R extends RecordValue> implements RdbmsExportHandler<R> {

  private final AuditLogWriter auditLogWriter;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final AuditLogOperationTransformer<? extends Intent, R, AuditLogDbModel.Builder>
      operationTransformer;

  public AuditLogExportHandler(
      final AuditLogWriter auditLogWriter,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final AuditLogOperationTransformer<? extends Intent, R, AuditLogDbModel.Builder>
          operationTransformer) {
    this.auditLogWriter = auditLogWriter;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.operationTransformer = operationTransformer;
  }

  @Override
  public boolean canExport(final Record<R> record) {
    final var recordIntent = record.getIntent();
    return AuditLogCommonHandler.hasActorData(record)
        && (operationTransformer.getSupportedIntents().contains(recordIntent)
            || (operationTransformer.getSupportedCommandRejections().contains(recordIntent)
                && RecordType.COMMAND_REJECTION.equals(record.getRecordType())
                && operationTransformer
                    .getSupportedRejectionTypes()
                    .contains(record.getRejectionType())));
  }

  @Override
  public void export(final Record<R> record) {
    auditLogWriter.create(map(record));
  }

  private AuditLogDbModel map(final Record<R> record) {
    final var auditLog =
        new AuditLogDbModel.Builder()
            .auditLogKey(
                RandomStringUtils.insecure()
                    .nextAlphanumeric(vendorDatabaseProperties.userCharColumnSize()))
            .entityKey(String.valueOf(record.getKey()))
            .entityType(AuditLogCommonHandler.getEntityType(record.getValueType()))
            .operationType(AuditLogCommonHandler.getOperationType(record.getIntent()))
            .entityVersion(record.getRecordVersion())
            .entityValueType(record.getValueType().value())
            .entityOperationIntent(record.getIntent().value())
            .timestamp(DateUtil.toOffsetDateTime(record.getTimestamp()))
            .category(AuditLogCommonHandler.getOperationCategory(record.getValueType()));
    setActorData(auditLog, record);
    setBatchOperationData(auditLog, record);

    if (RecordType.COMMAND_REJECTION.equals(record.getRecordType())) {
      setRejectionData(auditLog, record);
    } else {
      auditLog.result(AuditLogOperationResult.SUCCESS);
      operationTransformer.transform(auditLog, record);
    }
    return auditLog.build();
  }

  private void setBatchOperationData(
      final AuditLogDbModel.Builder builder, final Record<R> record) {
    final var batchOperationKey = AuditLogCommonHandler.extractBatchOperationKey(record);
    batchOperationKey.ifPresent(builder::batchOperationKey);
  }

  private void setRejectionData(final AuditLogDbModel.Builder builder, final Record<R> record) {
    builder.result(AuditLogOperationResult.FAIL);
    // TODO: set rejection type and reason to AuditLogEntity#details
  }

  private void setActorData(final AuditLogDbModel.Builder builder, final Record<R> record) {
    final var actorData = AuditLogCommonHandler.extractActorData(record);
    builder.actorId(actorData.actorId()).actorType(actorData.actorType());
  }
}
