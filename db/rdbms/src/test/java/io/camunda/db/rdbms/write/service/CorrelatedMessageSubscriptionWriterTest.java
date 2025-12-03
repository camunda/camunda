/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorrelatedMessageSubscriptionWriterTest {

  private ExecutionQueue executionQueue;
  private CorrelatedMessageSubscriptionMapper mapper;
  private CorrelatedMessageSubscriptionWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    mapper = mock(CorrelatedMessageSubscriptionMapper.class);
    writer = new CorrelatedMessageSubscriptionWriter(executionQueue, mapper);
  }

  @Test
  void shouldCreateCorrelatedMessageSubscription() {
    final var model =
        new CorrelatedMessageSubscriptionDbModel.Builder()
            .messageKey(123L)
            .subscriptionKey(456L)
            .build();

    writer.create(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
