/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class WaitStateWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public WaitStateWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final WaitStateDbModel waitState) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.WAIT_STATE,
            WriteStatementType.INSERT,
            waitState.waitStateKey(),
            "io.camunda.db.rdbms.sql.WaitStateMapper.insert",
            waitState));
  }

  public void update(final WaitStateDbModel waitState) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.WAIT_STATE,
            WriteStatementType.UPDATE,
            waitState.waitStateKey(),
            "io.camunda.db.rdbms.sql.WaitStateMapper.update",
            waitState));
  }

  public void delete(final long waitStateKey) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.WAIT_STATE,
            WriteStatementType.DELETE,
            waitStateKey,
            "io.camunda.db.rdbms.sql.WaitStateMapper.delete",
            waitStateKey));
  }
}
