/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.AgentInstanceToolDbValue;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceDefinition;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceLimits;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceMetrics;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceTool;
import java.util.Collections;
import java.util.List;

public class AgentInstanceEntityMapper {

  public static AgentInstanceEntity toEntity(final AgentInstanceDbModel dbModel) {
    if (dbModel == null) {
      return null;
    }
    return new AgentInstanceEntity(
        dbModel.agentInstanceKey(),
        dbModel.elementInstanceKeys() != null ? dbModel.elementInstanceKeys() : List.of(),
        dbModel.status(),
        new AgentInstanceDefinition(dbModel.model(), dbModel.provider(), dbModel.systemPrompt()),
        new AgentInstanceMetrics(
            dbModel.inputTokens(),
            dbModel.outputTokens(),
            dbModel.modelCalls(),
            dbModel.toolCalls()),
        new AgentInstanceLimits(
            dbModel.maxTokens(), dbModel.maxModelCalls(), dbModel.maxToolCalls()),
        toTools(dbModel.toolValues()),
        dbModel.elementId(),
        dbModel.processInstanceKey(),
        dbModel.rootProcessInstanceKey() == -1L ? null : dbModel.rootProcessInstanceKey(),
        dbModel.processDefinitionKey(),
        dbModel.processDefinitionId(),
        dbModel.processDefinitionVersion(),
        dbModel.versionTag(),
        dbModel.tenantId(),
        dbModel.creationDate(),
        dbModel.lastUpdatedDate(),
        dbModel.completionDate());
  }

  private static List<AgentInstanceTool> toTools(final List<AgentInstanceToolDbValue> tools) {
    if (tools == null) {
      return Collections.emptyList();
    }
    return tools.stream()
        .map(t -> new AgentInstanceTool(t.name(), t.description(), t.elementId()))
        .toList();
  }
}
