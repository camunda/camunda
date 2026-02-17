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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.GlobalListenerMapper;
import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GlobalListenerWriterTest {

  private ExecutionQueue executionQueue;
  private GlobalListenerMapper globalListenerMapper;
  private GlobalListenerWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    globalListenerMapper = mock(GlobalListenerMapper.class);
    writer = new GlobalListenerWriter(executionQueue, globalListenerMapper);
  }

  @Test
  void shouldCreateGlobalListener() {
    // given
    final var model = createTestModel();

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
                    123L,
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
                    123L,
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.insertEventTypes",
                    model)));
  }

  @Test
  void shouldUpdateGlobalListener() {
    // given
    final var model = createTestModel();

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
                    123L,
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
                    123L,
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
                    123L,
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.insertEventTypes",
                    model)));
  }

  @Test
  void shouldDeleteGlobalListener() {
    // given
    final var model = createTestModel();

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
                    123L,
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
                    123L,
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.delete",
                    model)));
  }

  @Test
  void shouldCreateGlobalListenerWithCorrectContextType() {
    // given
    final var model = createTestModel();

    // when
    writer.create(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.INSERT,
                    123L,
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.insert",
                    model)));
  }

  @Test
  void shouldUseGlobalListenerKeyAsQueueKey() {
    // given
    final var model =
        new GlobalListenerDbModel(
            999L,
            "different-id",
            "different-type",
            5,
            List.of("UPDATING"),
            false,
            30,
            GlobalListenerSource.CONFIGURATION,
            GlobalListenerType.USER_TASK);

    // when
    writer.create(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GLOBAL_LISTENER,
                    WriteStatementType.INSERT,
                    999L, // globalListenerKey used as queue key
                    "io.camunda.db.rdbms.sql.GlobalListenerMapper.insert",
                    model)));
  }

  private GlobalListenerDbModel createTestModel() {
    return new GlobalListenerDbModel(
        123L,
        "listener-id",
        "job-type",
        3,
        List.of("CREATING", "COMPLETING"),
        true,
        50,
        GlobalListenerSource.API,
        GlobalListenerType.USER_TASK);
  }
}
