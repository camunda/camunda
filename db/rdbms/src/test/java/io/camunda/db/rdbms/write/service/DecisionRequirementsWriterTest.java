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

import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionRequirementsWriterTest {

  private final DecisionRequirementsMapper mapper = mock(DecisionRequirementsMapper.class);
  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final DecisionRequirementsWriter writer =
      new DecisionRequirementsWriter(mapper, executionQueue);

  @Test
  void shouldCreateDecisionRequirements() {
    final var model =
        new DecisionRequirementsDbModel.Builder()
            .decisionRequirementsKey(123L)
            .decisionRequirementsId("drd1")
            .build();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.DECISION_DEFINITION,
                    WriteStatementType.INSERT,
                    model.decisionRequirementsKey(),
                    "io.camunda.db.rdbms.sql.DecisionRequirementsMapper.insert",
                    model)));
  }

  @Test
  void shouldDeleteDecisionRequirementsByKeys() {
    final var decisionRequirementsKeys = List.of(1L, 2L, 3L);

    writer.deleteByKeys(decisionRequirementsKeys);

    verify(mapper).deleteByKeys(eq(decisionRequirementsKeys));
  }
}
