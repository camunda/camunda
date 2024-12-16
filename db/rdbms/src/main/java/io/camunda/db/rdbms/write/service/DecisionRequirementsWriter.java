/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;

public class DecisionRequirementsWriter {

  private final ExecutionQueue executionQueue;

  public DecisionRequirementsWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final DecisionRequirementsDbModel decisionRequirements) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DECISION_DEFINITION,
            decisionRequirements.decisionRequirementsKey(),
            "io.camunda.db.rdbms.sql.DecisionRequirementsMapper.insert",
            decisionRequirements));
  }
}
