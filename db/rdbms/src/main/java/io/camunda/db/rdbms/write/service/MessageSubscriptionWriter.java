/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class MessageSubscriptionWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public MessageSubscriptionWriter(
      final ExecutionQueue executionQueue, final MessageSubscriptionMapper mapper) {
    super(mapper);
    this.executionQueue = executionQueue;
  }

  public void create(final MessageSubscriptionDbModel messageSubscription) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_SUBSCRIPTION,
            WriteStatementType.INSERT,
            messageSubscription.messageSubscriptionKey(),
            "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.insert",
            messageSubscription));
  }

  public void update(final MessageSubscriptionDbModel messageSubscription) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_SUBSCRIPTION,
            WriteStatementType.UPDATE,
            messageSubscription.messageSubscriptionKey(),
            "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.update",
            messageSubscription));
  }
}
