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
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;

public class VariableWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public VariableWriter(
      final ExecutionQueue executionQueue,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final VariableDbModel variable) {

    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            variable.variableKey(),
            "io.camunda.db.rdbms.sql.VariableMapper.insert",
            variable.truncateValue(vendorDatabaseProperties.variableValuePreviewSize())));
  }

  public void update(final VariableDbModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            variable.variableKey(),
            "io.camunda.db.rdbms.sql.VariableMapper.update",
            variable.truncateValue(vendorDatabaseProperties.variableValuePreviewSize())));
  }

  public void migrateToProcess(final long variableKey, final String processDefinitionId) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            variableKey,
            "io.camunda.db.rdbms.sql.VariableMapper.migrateToProcess",
            new VariableMapper.MigrateToProcessDto.Builder()
                .variableKey(variableKey)
                .processDefinitionId(processDefinitionId)));
  }
}
