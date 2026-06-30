/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Metrics;
import java.util.List;

public class AgentHistoryEntityMapper {

  public static AgentInstanceHistoryEntity toEntity(final AgentHistoryDbModel dbModel) {
    if (dbModel == null) {
      return null;
    }
    return new AgentInstanceHistoryEntity(
        dbModel.agentHistoryKey(),
        dbModel.agentInstanceKey(),
        dbModel.elementInstanceKey(),
        dbModel.processInstanceKey(),
        dbModel.processDefinitionKey(),
        dbModel.processDefinitionId(),
        dbModel.tenantId(),
        dbModel.jobKey(),
        dbModel.jobLease(),
        dbModel.iteration(),
        dbModel.role(),
        dbModel.contentItems() != null ? dbModel.contentItems() : List.of(),
        dbModel.toolCallValues() != null ? dbModel.toolCallValues() : List.of(),
        new Metrics(dbModel.inputTokens(), dbModel.outputTokens(), dbModel.durationMs()),
        dbModel.commitStatus(),
        dbModel.producedAt());
  }
}
