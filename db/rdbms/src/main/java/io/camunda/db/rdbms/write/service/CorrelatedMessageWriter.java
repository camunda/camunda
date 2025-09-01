/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.CorrelatedMessageMapper;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class CorrelatedMessageWriter {

  private final ExecutionQueue executionQueue;
  private final CorrelatedMessageMapper mapper;

  public CorrelatedMessageWriter(
      final ExecutionQueue executionQueue, final CorrelatedMessageMapper mapper) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
  }

  public void create(final CorrelatedMessageDbModel correlatedMessage) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CORRELATED_MESSAGE,
            WriteStatementType.INSERT,
            correlatedMessage.messageKey(),
            "io.camunda.db.rdbms.sql.CorrelatedMessageMapper.insert",
            correlatedMessage));
  }

  public void delete(final Long messageKey) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CORRELATED_MESSAGE,
            WriteStatementType.DELETE,
            messageKey,
            "io.camunda.db.rdbms.sql.CorrelatedMessageMapper.delete",
            messageKey));
  }
}