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

import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class AuthorizationWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final AuthorizationWriter writer = new AuthorizationWriter(executionQueue);

  @Test
  void shouldCreateAuthorization() {
    final var model = new AuthorizationDbModel.Builder().authorizationKey(123L).build();

    writer.createAuthorization(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AUTHORIZATION,
                    WriteStatementType.INSERT,
                    model.authorizationKey().toString(),
                    "io.camunda.db.rdbms.sql.AuthorizationMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateAuthorization() {
    final var model = new AuthorizationDbModel.Builder().authorizationKey(123L).build();

    writer.updateAuthorization(model);

    // Update triggers delete + create
    final InOrder inOrder = Mockito.inOrder(executionQueue);
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AUTHORIZATION,
                    WriteStatementType.DELETE,
                    model.authorizationKey().toString(),
                    "io.camunda.db.rdbms.sql.AuthorizationMapper.delete",
                    model)));
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AUTHORIZATION,
                    WriteStatementType.INSERT,
                    model.authorizationKey().toString(),
                    "io.camunda.db.rdbms.sql.AuthorizationMapper.insert",
                    model)));
  }

  @Test
  void shouldDeleteAuthorization() {
    final var model = new AuthorizationDbModel.Builder().authorizationKey(123L).build();

    writer.deleteAuthorization(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AUTHORIZATION,
                    WriteStatementType.DELETE,
                    model.authorizationKey().toString(),
                    "io.camunda.db.rdbms.sql.AuthorizationMapper.delete",
                    model)));
  }
}
