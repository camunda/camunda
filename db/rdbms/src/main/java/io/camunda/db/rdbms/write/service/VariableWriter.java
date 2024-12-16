/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;

public class VariableWriter {

  private final ExecutionQueue executionQueue;

  public VariableWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final VariableDbModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            variable.variableKey(),
            "io.camunda.db.rdbms.sql.VariableMapper.insert",
            variable));
  }

  public void update(final VariableDbModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            variable.variableKey(),
            "io.camunda.db.rdbms.sql.VariableMapper.update",
            variable));
  }
}
