/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.AgentDefinitionEntity;

public enum AgentDefinitionSearchColumn implements SearchColumn<AgentDefinitionEntity> {
  AGENT_DEFINITION_KEY("agentDefinitionKey"),
  AGENT_TYPE("agentType"),
  NAME("name"),
  ELEMENT_ID("elementId"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_DEFINITION_VERSION("processDefinitionVersion"),
  PROCESS_DEFINITION_VERSION_TAG("processDefinitionVersionTag"),
  TENANT_ID("tenantId");

  private final String property;

  AgentDefinitionSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<AgentDefinitionEntity> getEntityClass() {
    return AgentDefinitionEntity.class;
  }
}
