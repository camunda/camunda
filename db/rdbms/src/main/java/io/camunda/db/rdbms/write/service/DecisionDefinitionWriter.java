/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;

public class DecisionDefinitionWriter implements RdbmsWriter {

  private final DecisionDefinitionMapper mapper;
  private final ExecutionQueue executionQueue;

  public DecisionDefinitionWriter(
      final DecisionDefinitionMapper mapper, final ExecutionQueue executionQueue) {
    this.mapper = mapper;
    this.executionQueue = executionQueue;
  }

  public void create(final DecisionDefinitionDbModel decisionDefinition) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DECISION_DEFINITION,
            WriteStatementType.INSERT,
            decisionDefinition.decisionDefinitionKey(),
            "io.camunda.db.rdbms.sql.DecisionDefinitionMapper.insert",
            decisionDefinition));
  }

  public int deleteByDecisionRequirementsKeys(
      final List<Long> decisionRequirementsKeys, final int limit) {
    return mapper.deleteByDecisionRequirementsKeys(decisionRequirementsKeys, limit);
  }
}
