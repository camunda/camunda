/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.AgentDefinitionMapper;
import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;

public class AgentDefinitionWriter implements RdbmsWriter {

  private final AgentDefinitionMapper mapper;
  private final ExecutionQueue executionQueue;

  public AgentDefinitionWriter(
      final AgentDefinitionMapper mapper, final ExecutionQueue executionQueue) {
    this.mapper = mapper;
    this.executionQueue = executionQueue;
  }

  public void create(final AgentDefinitionDbModel agentDefinition) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AGENT_DEFINITION,
            WriteStatementType.INSERT,
            agentDefinition.agentDefinitionKey(),
            "io.camunda.db.rdbms.sql.AgentDefinitionMapper.insert",
            agentDefinition));
  }

  public int deleteByProcessDefinitionKeys(
      final List<Long> processDefinitionKeys, final int limit) {
    return mapper.deleteByProcessDefinitionKeys(processDefinitionKeys, limit);
  }
}
