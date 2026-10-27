/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class GlobalListenerWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public GlobalListenerWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final GlobalListenerDbModel globalListener) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GLOBAL_LISTENER,
            WriteStatementType.INSERT,
            globalListener.id(),
            "io.camunda.db.rdbms.sql.GlobalListenerMapper.insert",
            globalListener));
    if (globalListener.eventTypes() != null && !globalListener.eventTypes().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.GLOBAL_LISTENER,
              WriteStatementType.INSERT,
              globalListener.id(),
              "io.camunda.db.rdbms.sql.GlobalListenerMapper.insertEventTypes",
              globalListener));
    }
  }

  public void update(final GlobalListenerDbModel globalListener) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GLOBAL_LISTENER,
            WriteStatementType.UPDATE,
            globalListener.id(),
            "io.camunda.db.rdbms.sql.GlobalListenerMapper.update",
            globalListener));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GLOBAL_LISTENER,
            WriteStatementType.DELETE,
            globalListener.id(),
            "io.camunda.db.rdbms.sql.GlobalListenerMapper.deleteEventTypes",
            globalListener));
    if (globalListener.eventTypes() != null && !globalListener.eventTypes().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.GLOBAL_LISTENER,
              WriteStatementType.INSERT,
              globalListener.id(),
              "io.camunda.db.rdbms.sql.GlobalListenerMapper.insertEventTypes",
              globalListener));
    }
  }

  public void delete(final GlobalListenerDbModel globalListener) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GLOBAL_LISTENER,
            WriteStatementType.DELETE,
            globalListener.id(),
            "io.camunda.db.rdbms.sql.GlobalListenerMapper.deleteEventTypes",
            globalListener));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GLOBAL_LISTENER,
            WriteStatementType.DELETE,
            globalListener.id(),
            "io.camunda.db.rdbms.sql.GlobalListenerMapper.delete",
            globalListener));
  }
}
