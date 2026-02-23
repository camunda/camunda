/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;

public class DecisionRequirementsWriter implements RdbmsWriter {

  private final DecisionRequirementsMapper mapper;
  private final ExecutionQueue executionQueue;

  public DecisionRequirementsWriter(
      final DecisionRequirementsMapper mapper, final ExecutionQueue executionQueue) {
    this.mapper = mapper;
    this.executionQueue = executionQueue;
  }

  public void create(final DecisionRequirementsDbModel decisionRequirements) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DECISION_DEFINITION,
            WriteStatementType.INSERT,
            decisionRequirements.decisionRequirementsKey(),
            "io.camunda.db.rdbms.sql.DecisionRequirementsMapper.insert",
            decisionRequirements));
  }

  public void deleteByKeys(final List<Long> decisionRequirementsKeys) {
    mapper.deleteByKeys(decisionRequirementsKeys);
  }
}
