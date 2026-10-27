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

import io.camunda.db.rdbms.sql.AgentDefinitionMapper;
import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel.AgentDefinitionDbModelBuilder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import org.junit.jupiter.api.Test;

class AgentDefinitionWriterTest {

  private final AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final AgentDefinitionWriter writer = new AgentDefinitionWriter(mapper, executionQueue);

  @Test
  void shouldCreateAgentDefinition() {
    // given
    final var model =
        new AgentDefinitionDbModelBuilder()
            .agentDefinitionKey(1L)
            .agentType(AgentType.AI_AGENT_TASK)
            .name("name")
            .elementId("element-id")
            .processDefinitionId("process-definition-id")
            .processDefinitionKey(2L)
            .processDefinitionVersion(3)
            .processDefinitionVersionTag("version-tag")
            .tenantId("tenant-id")
            .build();

    // when
    writer.create(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AGENT_DEFINITION,
                    WriteStatementType.INSERT,
                    1L,
                    "io.camunda.db.rdbms.sql.AgentDefinitionMapper.insert",
                    model)));
  }
}
