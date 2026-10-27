/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class CorrelatedMessageSubscriptionWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final CorrelatedMessageSubscriptionMapper mapper =
      mock(CorrelatedMessageSubscriptionMapper.class);
  private final CorrelatedMessageSubscriptionWriter writer =
      new CorrelatedMessageSubscriptionWriter(executionQueue, mapper);

  @Test
  void shouldCreateCorrelatedMessageSubscription() {
    final var model =
        new CorrelatedMessageSubscriptionDbModel.Builder()
            .messageKey(123L)
            .subscriptionKey(456L)
            .build();
    final String compositeId = model.messageKey() + "_" + model.subscriptionKey();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.CORRELATED_MESSAGE_SUBSCRIPTION,
                    WriteStatementType.INSERT,
                    compositeId,
                    "io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper.insert",
                    model)));
  }
}
