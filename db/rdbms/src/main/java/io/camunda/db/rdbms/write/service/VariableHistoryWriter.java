/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.VariableHistoryMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.VariableHistoryDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertVariableHistoryMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

/**
 * PoC: appends variable changes to the VARIABLE_HISTORY table, batched like the audit log inserts.
 */
public class VariableHistoryWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterConfig config;

  public VariableHistoryWriter(
      final ExecutionQueue executionQueue,
      final VariableHistoryMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.config = config;
  }

  public void create(final VariableHistoryDbModel model) {
    final var truncated =
        model.truncateValue(
            vendorDatabaseProperties.variableValuePreviewSize(),
            vendorDatabaseProperties.charColumnMaxBytes());

    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new InsertVariableHistoryMerger(
                truncated, config.insertBatchingConfig().auditLogInsertBatchSize()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.VARIABLE_HISTORY,
              WriteStatementType.INSERT,
              truncated.variableHistoryKey(),
              "io.camunda.db.rdbms.sql.VariableHistoryMapper.insert",
              new BatchInsertDto<>(truncated)));
    }
  }
}
