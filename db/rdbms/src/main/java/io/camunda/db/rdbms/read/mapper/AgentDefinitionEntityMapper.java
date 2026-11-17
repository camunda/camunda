/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static io.camunda.db.rdbms.read.NullSafeStrings.nullToEmpty;

import io.camunda.db.rdbms.write.domain.AgentDefinitionDbModel;
import io.camunda.search.entities.AgentDefinitionEntity;

public class AgentDefinitionEntityMapper {

  public static AgentDefinitionEntity toEntity(final AgentDefinitionDbModel dbModel) {
    if (dbModel == null) {
      return null;
    }
    return new AgentDefinitionEntity(
        dbModel.agentDefinitionKey(),
        dbModel.agentType(),
        nullToEmpty(dbModel.name()),
        nullToEmpty(dbModel.elementId()),
        nullToEmpty(dbModel.processDefinitionId()),
        dbModel.processDefinitionKey(),
        dbModel.processDefinitionVersion(),
        dbModel.processDefinitionVersionTag(),
        nullToEmpty(dbModel.tenantId()));
  }
}
