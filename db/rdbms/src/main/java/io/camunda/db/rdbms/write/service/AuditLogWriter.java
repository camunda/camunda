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
import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertAuditLogMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpdateHistoryCleanupDateMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import java.time.OffsetDateTime;

public class AuditLogWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final AuditLogMapper mapper;
  private final ExecutionQueue executionQueue;
  private final RdbmsWriterConfig config;

  public AuditLogWriter(
      final ExecutionQueue executionQueue,
      final AuditLogMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.mapper = mapper;
    this.config = config;
  }

  public void create(final AuditLogDbModel auditLog) {
    // standalone decisions are completed on evaluation and should be cleaned up based on the
    // decision instance TTL
    final AuditLogDbModel finalAuditLog;
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
              new AuditLogMapper.BatchInsertAuditLogsDto.Builder()
                  .auditLog(finalAuditLog)
                  .build()));
    }
  }

  public void scheduleProcessInstanceLogsForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new UpdateHistoryCleanupDateMerger(
                ContextType.AUDIT_LOG, processInstanceKey, historyCleanupDate));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.AUDIT_LOG,
              WriteStatementType.UPDATE,
              processInstanceKey,
              "io.camunda.db.rdbms.sql.AuditLogMapper.updateProcessHistoryCleanupDate",
              new ProcessBasedHistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                  .processInstanceKey(processInstanceKey)
                  .historyCleanupDate(historyCleanupDate)
                  .build()));
    }
  }

  public int cleanupHistory(
      final int partitionId, final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupHistory(
        new CleanupHistoryDto.Builder()
            .partitionId(partitionId)
            .cleanupDate(cleanupDate)
            .limit(rowsToRemove)
            .build());
  }
}
