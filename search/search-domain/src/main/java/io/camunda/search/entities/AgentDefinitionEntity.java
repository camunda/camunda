/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.security.core.authz.TenantOwnedEntity;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDefinitionEntity(
    Long agentDefinitionKey,
    AgentType agentType,
    String name,
    String elementId,
    String processDefinitionId,
    Long processDefinitionKey,
    Integer processDefinitionVersion,
    @Nullable String processDefinitionVersionTag,
    String tenantId)
    implements TenantOwnedEntity {

  public AgentDefinitionEntity {
    Objects.requireNonNull(agentDefinitionKey, "agentDefinitionKey");
    Objects.requireNonNull(agentType, "agentType");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(elementId, "elementId");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionVersion, "processDefinitionVersion");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  public enum AgentType {
    AI_AGENT_SUB_PROCESS,
    AI_AGENT_TASK,
    EXTERNAL_AGENT
  }
}
