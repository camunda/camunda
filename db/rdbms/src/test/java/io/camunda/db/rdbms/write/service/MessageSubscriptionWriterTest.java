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

import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageSubscriptionWriterTest {

  private ExecutionQueue executionQueue;
  private MessageSubscriptionMapper mapper;
  private MessageSubscriptionWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    mapper = mock(MessageSubscriptionMapper.class);
    writer = new MessageSubscriptionWriter(executionQueue, mapper);
  }

  @Test
  void shouldCreateMessageSubscription() {
    final var model =
        new MessageSubscriptionDbModel.Builder().messageSubscriptionKey(123L).build();

    writer.create(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateMessageSubscription() {
    final var model =
        new MessageSubscriptionDbModel.Builder().messageSubscriptionKey(123L).build();

    writer.update(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
