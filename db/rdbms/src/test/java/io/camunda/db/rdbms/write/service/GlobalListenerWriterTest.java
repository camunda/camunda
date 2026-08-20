/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel.GlobalListenerDbModelBuilder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GlobalListenerWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final GlobalListenerWriter writer = new GlobalListenerWriter(executionQueue);

  @Test
  void shouldCreateGlobalListener() {
    // given
    final var model =
        new GlobalListenerDbModelBuilder()
            .listenerId("listener-id")
            .type("job-type")
            .retries(3)
            .eventTypes(List.of("creating", "completing"))
            .afterNonGlobal(true)
            .priority(50)
            .source(GlobalListenerSource.CONFIGURATION)
            .listenerType(GlobalListenerType.USER_TASK)
            .build();

    // when
    writer.create(model);

    // then
    final InOrder inOrder = inOrder(executionQueue);

    // First, insert the global listener
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.INSERT,
                    "USER_TASK-listener-id", // combined id used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.insert",
                    model)));

    // Then, insert the event types
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.INSERT,
                    "USER_TASK-listener-id", // combined id used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.insertEventTypes",
                    model)));
  }

  @Test
  void shouldUpdateGlobalListener() {
    // given
    final var model =
        new GlobalListenerDbModelBuilder()
            .listenerId("listener-id")
            .type("job-type")
            .retries(3)
            .eventTypes(List.of("creating", "completing"))
            .afterNonGlobal(true)
            .priority(50)
            .source(GlobalListenerSource.CONFIGURATION)
            .listenerType(GlobalListenerType.USER_TASK)
            .build();

    // when
    writer.update(model);

    // then
    final InOrder inOrder = inOrder(executionQueue);

    // First, update the global listener
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.UPDATE,
                    "USER_TASK-listener-id", // combined id used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.update",
                    model)));

    // Then, delete existing event types
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.DELETE,
                    "USER_TASK-listener-id", // combined id used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.deleteEventTypes",
                    model)));

    // Finally, insert new event types
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.INSERT,
                    "USER_TASK-listener-id", // combined id used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.insertEventTypes",
                    model)));
  }

  @Test
  void shouldDeleteGlobalListener() {
    // given
    final var model =
        new GlobalListenerDbModelBuilder()
            .listenerId("listener-id")
            .type("job-type")
            .retries(3)
            .eventTypes(List.of("creating", "completing"))
            .afterNonGlobal(true)
            .priority(50)
            .source(GlobalListenerSource.CONFIGURATION)
            .listenerType(GlobalListenerType.USER_TASK)
            .build();

    // when
    writer.delete(model);

    // then
    final InOrder inOrder = inOrder(executionQueue);

    // First, delete event types
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.DELETE,
                    "USER_TASK-listener-id", // combined id used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.deleteEventTypes",
                    model)));

    // Then, delete the global listener
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.DELETE,
                    "USER_TASK-listener-id", // combined id used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.delete",
                    model)));
  }
}
