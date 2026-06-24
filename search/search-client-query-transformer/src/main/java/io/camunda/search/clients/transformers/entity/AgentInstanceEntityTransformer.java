/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceDefinition;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceLimits;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceMetrics;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceTool;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity.AgentInstanceToolValue;
import java.util.List;

public class AgentInstanceEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity, AgentInstanceEntity> {

  @Override
  public AgentInstanceEntity apply(
      final io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity source) {

    final var definition =
        new AgentInstanceDefinition(
            source.getModel(), source.getProvider(), source.getSystemPrompt());

    final var metrics =
        new AgentInstanceMetrics(
            source.getInputTokens(),
            source.getOutputTokens(),
            source.getModelCalls(),
            source.getToolCalls());

    final var limits =
        new AgentInstanceLimits(
            source.getMaxTokens(), source.getMaxModelCalls(), source.getMaxToolCalls());

    final var tools = toTools(source.getTools());

    return new AgentInstanceEntity(
        source.getKey(),
        source.getElementInstanceKeys(),
        toStatus(source.getStatus()),
        definition,
        metrics,
        limits,
        tools,
        source.getElementId(),
        source.getProcessInstanceKey(),
        source.getRootProcessInstanceKey(),
        source.getProcessDefinitionKey(),
        source.getBpmnProcessId(),
        source.getProcessDefinitionVersion(),
        source.getVersionTag(),
        source.getTenantId(),
        source.getCreationDate(),
        source.getLastUpdatedDate(),
        source.getCompletionDate());
  }

  private static List<AgentInstanceTool> toTools(final List<AgentInstanceToolValue> source) {
    if (source == null) {
      return List.of();
    }
    return source.stream()
        .map(t -> new AgentInstanceTool(t.name(), t.description(), t.elementId()))
        .toList();
  }

  private static AgentInstanceStatus toStatus(
      final io.camunda.webapps.schema.entities.agentinstance.AgentInstanceStatus source) {
    if (source == null) {
      return AgentInstanceStatus.UNKNOWN;
    }
    return switch (source) {
      case INITIALIZING -> AgentInstanceStatus.INITIALIZING;
      case TOOL_DISCOVERY -> AgentInstanceStatus.TOOL_DISCOVERY;
      case THINKING -> AgentInstanceStatus.THINKING;
      case TOOL_CALLING -> AgentInstanceStatus.TOOL_CALLING;
      case IDLE -> AgentInstanceStatus.IDLE;
      case COMPLETED -> AgentInstanceStatus.COMPLETED;
      case UNKNOWN -> AgentInstanceStatus.UNKNOWN;
    };
  }
}
