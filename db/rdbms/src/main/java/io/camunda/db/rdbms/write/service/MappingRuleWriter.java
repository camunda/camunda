/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class MappingRuleWriter {

  private final ExecutionQueue executionQueue;

  public MappingRuleWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final MappingRuleDbModel mappingRule) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MAPPING_RULE,
            WriteStatementType.INSERT,
            mappingRule.mappingRuleId(),
            "io.camunda.db.rdbms.sql.MappingRuleMapper.insert",
            mappingRule));
  }

  public void delete(final String mappingRuleId) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MAPPING_RULE,
            WriteStatementType.DELETE,
            mappingRuleId,
            "io.camunda.db.rdbms.sql.MappingRuleMapper.delete",
            mappingRuleId));
  }

  public void update(final MappingRuleDbModel mappingRule) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MAPPING_RULE,
            WriteStatementType.UPDATE,
            mappingRule.mappingRuleId(),
            "io.camunda.db.rdbms.sql.MappingRuleMapper.update",
            mappingRule));
  }
}
