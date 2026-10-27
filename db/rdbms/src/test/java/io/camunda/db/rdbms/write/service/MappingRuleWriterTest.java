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

import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class MappingRuleWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final MappingRuleWriter writer = new MappingRuleWriter(executionQueue);

  @Test
  void shouldCreateMappingRule() {
    final var model = new MappingRuleDbModel("rule1", 123L, "claim1", "value1", "Test Mapping");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.MAPPING_RULE,
                    WriteStatementType.INSERT,
                    model.mappingRuleId(),
                    "io.camunda.db.rdbms.sql.MappingRuleMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateMappingRule() {
    final var model = new MappingRuleDbModel("rule1", 123L, "claim1", "value1", "Test Mapping");

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.MAPPING_RULE,
                    WriteStatementType.UPDATE,
                    model.mappingRuleId(),
                    "io.camunda.db.rdbms.sql.MappingRuleMapper.update",
                    model)));
  }

  @Test
  void shouldDeleteMappingRule() {
    final String ruleId = "rule1";

    writer.delete(ruleId);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.MAPPING_RULE,
                    WriteStatementType.DELETE,
                    ruleId,
                    "io.camunda.db.rdbms.sql.MappingRuleMapper.delete",
                    ruleId)));
  }
}
