/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class UserTaskWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final UserTaskMapper mapper = mock(UserTaskMapper.class);
  private final UserTaskWriter writer = new UserTaskWriter(executionQueue, mapper);

  @Test
  void shouldCreateUserTaskWithoutCandidatesOrTags() {
    final var model = new UserTaskDbModel.Builder().userTaskKey(123L).elementId("task1").build();

    writer.create(model);

    // Should execute 1 queue item when no candidates or tags are present
    verify(executionQueue, times(1))
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.USER_TASK,
                    WriteStatementType.INSERT,
                    model.userTaskKey(),
                    "io.camunda.db.rdbms.sql.UserTaskMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateUserTask() {
    final var model = new UserTaskDbModel.Builder().userTaskKey(123L).elementId("task1").build();

    writer.update(model);

    // Update executes: 1 update + 2 delete operations (candidates + groups) = 3 items
    verify(executionQueue, times(3)).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateUserTaskState() {
    writer.updateState(123L, UserTaskState.COMPLETED);

    // Verify a QueueItem was enqueued for updating state
    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
