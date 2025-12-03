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
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import org.junit.jupiter.api.Test;

class UserWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final UserWriter writer = new UserWriter(executionQueue);

  @Test
  void shouldCreateUser() {
    final var model =
        new UserDbModel.Builder()
            .userKey(123L)
            .username("testuser")
            .name("Test User")
            .email("test@example.com")
            .password("password")
            .build();

    writer.create(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateUserWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    final var model =
        new UserDbModel.Builder()
            .userKey(123L)
            .username("testuser")
            .name("Updated Name")
            .email("updated@example.com")
            .build();

    writer.update(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldDeleteUser() {
    writer.delete("testuser");

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
