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
import io.camunda.db.rdbms.write.domain.AuditLogDbModel.Builder;
import io.camunda.db.rdbms.write.service.AuditLogWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.BatchOperation;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.util.DateUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLogExportHandler<R extends RecordValue> implements RdbmsExportHandler<R> {
  public static final Logger LOG = LoggerFactory.getLogger(AuditLogExportHandler.class);

  private final AuditLogWriter auditLogWriter;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final AuditLogTransformer<R, Builder> transformer;
  private final AuditLogConfiguration configuration;

  public AuditLogExportHandler(
      final AuditLogWriter auditLogWriter,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final AuditLogTransformer<R, AuditLogDbModel.Builder> transformer,
      final AuditLogConfiguration configuration) {
    this.auditLogWriter = auditLogWriter;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.transformer = transformer;
    this.configuration = configuration;
  }

  @VisibleForTesting
  public AuditLogTransformer<R, AuditLogDbModel.Builder> getTransformer() {
    return transformer;
  }

  @Override
  public boolean canExport(final Record<R> record) {
    final var info = AuditLogInfo.of(record);

    return transformer.supports(record) && configuration.isEnabled(info);
  }

  @Override
  public void export(final Record<R> record) {
    auditLogWriter.create(map(record));
  }

  private AuditLogDbModel map(final Record<R> record) {
    final var info = AuditLogInfo.of(record);
    final var key =
        RandomStringUtils.insecure()
            .nextAlphanumeric(vendorDatabaseProperties.userCharColumnSize());

    final var auditLog =
        new AuditLogDbModel.Builder()
            .auditLogKey(key)
            .entityKey(String.valueOf(record.getKey()))
            .entityType(info.entityType())
            .category(info.category())
            .operationType(info.operationType())
            .actorId(info.actor().actorId())
            .actorType(info.actor().actorType())
            .batchOperationKey(info.batchOperation().map(BatchOperation::key).orElse(null))
            .entityVersion(record.getRecordVersion())
            .entityValueType(record.getValueType().value())
            .entityOperationIntent(record.getIntent().value())
            .timestamp(DateUtil.toOffsetDateTime(record.getTimestamp()));

    if (RecordType.COMMAND_REJECTION.equals(record.getRecordType())) {
      auditLog.result(AuditLogEntity.AuditLogOperationResult.FAIL);
      // TODO: set rejection type and reason to AuditLogEntity#details
    } else {
      auditLog.result(AuditLogEntity.AuditLogOperationResult.SUCCESS);
      transformer.transform(record, auditLog);
    }
    return auditLog.build();
  }
}
