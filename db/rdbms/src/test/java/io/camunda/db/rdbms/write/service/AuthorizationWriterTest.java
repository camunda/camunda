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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import org.junit.jupiter.api.Test;

class AuthorizationWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final AuthorizationWriter writer = new AuthorizationWriter(executionQueue);

  @Test
  void shouldCreateAuthorization() {
    final var model = mock(AuthorizationDbModel.class);

    writer.createAuthorization(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateAuthorization() {
    final var model = mock(AuthorizationDbModel.class);

    writer.updateAuthorization(model);

    // Update triggers delete + create
    verify(executionQueue, times(2)).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldDeleteAuthorization() {
    final var model = mock(AuthorizationDbModel.class);

    writer.deleteAuthorization(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
