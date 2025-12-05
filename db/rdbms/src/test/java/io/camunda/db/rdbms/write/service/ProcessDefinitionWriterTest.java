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

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class ProcessDefinitionWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final ProcessDefinitionWriter writer = new ProcessDefinitionWriter(executionQueue);

  @Test
  void shouldCreateProcessDefinition() {
    final var model =
        new ProcessDefinitionDbModel(
            123L,
            "process1",
            "resource.bpmn",
            "Process Name",
            "<default>",
            "1.0",
            1,
            "<bpmn>...</bpmn>",
            "form1");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.PROCESS_DEFINITION,
                    WriteStatementType.INSERT,
                    model.processDefinitionKey(),
                    "io.camunda.db.rdbms.sql.ProcessDefinitionMapper.insert",
                    model)));
  }
}
