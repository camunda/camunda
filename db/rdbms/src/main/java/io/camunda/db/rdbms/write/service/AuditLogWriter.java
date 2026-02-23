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
import io.camunda.db.rdbms.sql.AuditLogMapper.UpdateHistoryCleanupDateDto;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public class AuditLogWriter extends ProcessInstanceDependant implements RdbmsWriter {

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
    final var charColumnBytes = vendorDatabaseProperties.charColumnMaxBytes();
    final var userCharColumnSize = vendorDatabaseProperties.userCharColumnSize();
    final var finalAuditLog =
        auditLog.truncateEntityDescription(userCharColumnSize, charColumnBytes);

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

  public void scheduleEntityRelatedAuditLogsHistoryCleanupByEndTime(
      final String entityKey, final AuditLogEntityType entityType, final OffsetDateTime endTime) {
    final var historyCleanupDate = endTime.plus(resolveRetentionTime(entityType));
    scheduleEntityRelatedAuditLogsHistoryCleanupTime(entityKey, entityType, historyCleanupDate);
  }

  public void scheduleEntityRelatedAuditLogsHistoryCleanupTime(
      final String entityKey,
      final AuditLogEntityType entityType,
      final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AUDIT_LOG,
            WriteStatementType.UPDATE,
            entityKey,
            "io.camunda.db.rdbms.sql.AuditLogMapper.updateAuditLogEntityHistoryCleanupDate",
            new UpdateHistoryCleanupDateDto.Builder()
                .entityKey(entityKey)
                .entityType(entityType.name())
                .historyCleanupDate(historyCleanupDate)
                .build()));
  }

  public int cleanupHistory(
      final int partitionId, final OffsetDateTime cleanupDate, final int limit) {
    return mapper.cleanupHistory(
        new HistoryCleanupMapper.CleanupHistoryDto(partitionId, cleanupDate, limit));
  }

  private Duration resolveRetentionTime(final AuditLogEntityType entityType) {
    switch (entityType) {
      case AuditLogEntityType.DECISION:
        return config.history().decisionInstanceTTL();
      case AuditLogEntityType.RESOURCE:
      case AuditLogEntityType.USER:
      case AuditLogEntityType.MAPPING_RULE:
      case AuditLogEntityType.AUTHORIZATION:
      case AuditLogEntityType.ROLE:
      case AuditLogEntityType.GROUP:
      case AuditLogEntityType.TENANT:
      default:
        return config.history().defaultHistoryTTL();
    }
  }
}
