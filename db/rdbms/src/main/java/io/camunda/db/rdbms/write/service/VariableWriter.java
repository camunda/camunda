/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class VariableWriter {

  private final ExecutionQueue executionQueue;
  private final VariableMapper mapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public VariableWriter(
      final ExecutionQueue executionQueue,
      final VariableMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final VariableDbModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.INSERT,
            variable.variableKey(),
            "io.camunda.db.rdbms.sql.VariableMapper.insert",
            variable.truncateValue(
                vendorDatabaseProperties.variableValuePreviewSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
  }

  public void update(final VariableDbModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            variable.variableKey(),
            "io.camunda.db.rdbms.sql.VariableMapper.update",
            variable.truncateValue(
                vendorDatabaseProperties.variableValuePreviewSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.VariableMapper.updateHistoryCleanupDate",
            new ProcessBasedHistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                .processInstanceKey(processInstanceKey)
                .historyCleanupDate(historyCleanupDate)
                .build()));
  }

  public void migrateToProcess(final long variableKey, final String processDefinitionId) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            variableKey,
            "io.camunda.db.rdbms.sql.VariableMapper.migrateToProcess",
            new VariableMapper.MigrateToProcessDto.Builder()
                .variableKey(variableKey)
                .processDefinitionId(processDefinitionId)));
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
