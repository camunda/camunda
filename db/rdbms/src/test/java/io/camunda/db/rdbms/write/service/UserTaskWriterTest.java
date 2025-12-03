/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserTaskWriterTest {

  private ExecutionQueue executionQueue;
  private UserTaskMapper mapper;
  private UserTaskWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    mapper = mock(UserTaskMapper.class);
    writer = new UserTaskWriter(executionQueue, mapper);
  }

  @Test
  void shouldCreateUserTask() {
    final var model = new UserTaskDbModel.Builder().userTaskKey(123L).elementId("task1").build();

    writer.create(model);

    verify(executionQueue, atLeast(1)).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateUserTask() {
    final var model = new UserTaskDbModel.Builder().userTaskKey(123L).elementId("task1").build();

    writer.update(model);

    verify(executionQueue, atLeast(1)).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateUserTaskState() {
    writer.updateState(123L, UserTaskState.COMPLETED);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
