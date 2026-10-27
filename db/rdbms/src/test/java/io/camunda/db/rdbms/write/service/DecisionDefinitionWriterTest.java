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

import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionDefinitionWriterTest {

  private final DecisionDefinitionMapper mapper = mock(DecisionDefinitionMapper.class);
  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final DecisionDefinitionWriter writer =
      new DecisionDefinitionWriter(mapper, executionQueue);

  @Test
  void shouldCreateDecisionDefinition() {
    final var model =
        new DecisionDefinitionDbModel(
            123L, "decision1", "Decision Name", "1.0", 1, "<default>", 1L, null, -1);
    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.DECISION_DEFINITION,
                    WriteStatementType.INSERT,
                    model.decisionDefinitionKey(),
                    "io.camunda.db.rdbms.sql.DecisionDefinitionMapper.insert",
                    model)));
  }

  @Test
  void shouldDeleteDecisionDefinitionsByKeys() {
    final var decisionRequirementsKeys = List.of(1L, 2L, 3L);
    final var limit = 1000;

    writer.deleteByDecisionRequirementsKeys(decisionRequirementsKeys, limit);

    verify(mapper).deleteByDecisionRequirementsKeys(eq(decisionRequirementsKeys), eq(limit));
  }
}
