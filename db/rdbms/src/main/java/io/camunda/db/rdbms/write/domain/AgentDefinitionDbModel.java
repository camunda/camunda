/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.util.ObjectBuilder;

public record AgentDefinitionDbModel(
    Long agentDefinitionKey,
    AgentType agentType,
    String name,
    String elementId,
    String processDefinitionId,
    Long processDefinitionKey,
    int processDefinitionVersion,
    String processDefinitionVersionTag,
    String tenantId) {

  // create a builder for this record extending ObjectBuilder
  public static class AgentDefinitionDbModelBuilder
      implements ObjectBuilder<AgentDefinitionDbModel> {

    private Long agentDefinitionKey;
    private AgentType agentType;
    private String name;
    private String elementId;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private int processDefinitionVersion;
    private String processDefinitionVersionTag;
    private String tenantId;

    public AgentDefinitionDbModelBuilder agentDefinitionKey(final Long agentDefinitionKey) {
      this.agentDefinitionKey = agentDefinitionKey;
      return this;
    }

    public AgentDefinitionDbModelBuilder agentType(final AgentType agentType) {
      this.agentType = agentType;
      return this;
    }

    public AgentDefinitionDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public AgentDefinitionDbModelBuilder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public AgentDefinitionDbModelBuilder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public AgentDefinitionDbModelBuilder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public AgentDefinitionDbModelBuilder processDefinitionVersion(
        final int processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    public AgentDefinitionDbModelBuilder processDefinitionVersionTag(
        final String processDefinitionVersionTag) {
      this.processDefinitionVersionTag = processDefinitionVersionTag;
      return this;
    }

    public AgentDefinitionDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public AgentDefinitionDbModel build() {
      return new AgentDefinitionDbModel(
          agentDefinitionKey,
          agentType,
          name,
          elementId,
          processDefinitionId,
          processDefinitionKey,
          processDefinitionVersion,
          processDefinitionVersionTag,
          tenantId);
    }
  }
}
