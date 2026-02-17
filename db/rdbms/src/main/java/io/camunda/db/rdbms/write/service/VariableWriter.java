/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertVariableMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class VariableWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterConfig config;

  public VariableWriter(
      final ExecutionQueue executionQueue,
      final VariableMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.config = config;
  }

  public void create(final VariableDbModel variable) {
    final var truncatedVariable =
        variable.truncateValue(
            vendorDatabaseProperties.variableValuePreviewSize(),
            vendorDatabaseProperties.charColumnMaxBytes());

    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new InsertVariableMerger(
                truncatedVariable, config.insertBatchingConfig().variableInsertBatchSize()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.VARIABLE,
              WriteStatementType.INSERT,
              truncatedVariable.variableKey(),
              "io.camunda.db.rdbms.sql.VariableMapper.insert",
              new BatchInsertDto<>(truncatedVariable)));
    }
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
}
