/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;

public class AgentDefinitionEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity,
        AgentDefinitionEntity> {

  @Override
  public AgentDefinitionEntity apply(
      final io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionEntity source) {
    return new AgentDefinitionEntity(
        source.getKey(),
        toAgentType(source.getAgentType()),
        source.getName(),
        source.getElementId(),
        source.getBpmnProcessId(),
        source.getProcessDefinitionKey(),
        source.getProcessDefinitionVersion(),
        source.getProcessDefinitionVersionTag(),
        source.getTenantId());
  }

  private static AgentType toAgentType(
      final io.camunda.webapps.schema.entities.agentdefinition.AgentDefinitionType source) {
    return switch (source) {
      case AI_AGENT_SUB_PROCESS -> AgentType.AI_AGENT_SUB_PROCESS;
      case AI_AGENT_TASK -> AgentType.AI_AGENT_TASK;
      case EXTERNAL_AGENT -> AgentType.EXTERNAL_AGENT;
    };
  }
}
