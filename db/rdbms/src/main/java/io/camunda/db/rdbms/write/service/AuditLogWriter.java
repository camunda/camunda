/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.HistoryCleanupMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertAuditLogMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import java.time.OffsetDateTime;
import java.util.List;

public class AuditLogWriter extends RootProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final AuditLogMapper mapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterConfig config;

  public AuditLogWriter(
      final ExecutionQueue executionQueue,
      final AuditLogMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.mapper = mapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.config = config;
  }

  public void create(final AuditLogDbModel auditLog) {
    // standalone decisions are completed on evaluation and should be cleaned up based on the
    // decision instance TTL
    AuditLogDbModel finalAuditLog;
    if (AuditLogEntityType.DECISION.equals(auditLog.entityType())
        && (auditLog.processInstanceKey() == null || auditLog.processInstanceKey() == -1L)
        && auditLog.historyCleanupDate() == null) {
      finalAuditLog =
          auditLog.toBuilder()
              .historyCleanupDate(auditLog.timestamp().plus(config.history().decisionInstanceTTL()))
              .build();
    } else {
      finalAuditLog = auditLog;
    }

    final var charColumnBytes = vendorDatabaseProperties.charColumnMaxBytes();
    final var userCharColumnSize = vendorDatabaseProperties.userCharColumnSize();
    finalAuditLog = finalAuditLog.truncateEntityDescription(userCharColumnSize, charColumnBytes);

    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new InsertAuditLogMerger(
                finalAuditLog, config.insertBatchingConfig().auditLogInsertBatchSize()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.AUDIT_LOG,
              WriteStatementType.INSERT,
              finalAuditLog.auditLogKey(),
              "io.camunda.db.rdbms.sql.AuditLogMapper.insert",
              new BatchInsertDto<>(finalAuditLog)));
    }
  }

  public int deleteProcessDefinitionRelatedData(
      final List<Long> processDefinitionKeys, final int limit) {
    return mapper.deleteProcessDefinitionRelatedData(processDefinitionKeys, limit);
  }

  public int cleanupHistory(
      final int partitionId, final OffsetDateTime cleanupDate, final int limit) {
    return mapper.cleanupHistory(
        new HistoryCleanupMapper.CleanupHistoryDto(partitionId, cleanupDate, limit));
  }
}
