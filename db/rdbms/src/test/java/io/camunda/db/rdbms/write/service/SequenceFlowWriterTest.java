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
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class SequenceFlowWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final SequenceFlowMapper mapper = mock(SequenceFlowMapper.class);
  private final SequenceFlowWriter writer = new SequenceFlowWriter(executionQueue, mapper);

  @Test
  void shouldCreateSequenceFlow() {
    final var model = mock(SequenceFlowDbModel.class);
    when(model.sequenceFlowId()).thenReturn("flow1");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.SEQUENCE_FLOW,
                    WriteStatementType.INSERT,
                    "flow1",
                    "io.camunda.db.rdbms.sql.SequenceFlowMapper.insert",
                    model)));
  }

  @Test
  void shouldCreateSequenceFlowIfNotExists() {
    final var model = mock(SequenceFlowDbModel.class);
    when(model.sequenceFlowId()).thenReturn("flow1");

    writer.createIfNotExists(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.SEQUENCE_FLOW,
                    WriteStatementType.INSERT,
                    "flow1",
                    "io.camunda.db.rdbms.sql.SequenceFlowMapper.createIfNotExists",
                    model)));
  }

  @Test
  void shouldDeleteSequenceFlow() {
    final var model = mock(SequenceFlowDbModel.class);
    when(model.sequenceFlowId()).thenReturn("flow1");

    writer.delete(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.SEQUENCE_FLOW,
                    WriteStatementType.DELETE,
                    "flow1",
                    "io.camunda.db.rdbms.sql.SequenceFlowMapper.delete",
                    model)));
  }
}
