/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentInstanceEntity(
    Long agentInstanceKey,
    List<Long> elementInstanceKeys,
    AgentInstanceStatus status,
    AgentInstanceDefinition definition,
    AgentInstanceMetrics metrics,
    AgentInstanceLimits limits,
    List<AgentInstanceTool> tools,
    String elementId,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    Integer processDefinitionVersion,
    @Nullable String versionTag,
    String tenantId,
    OffsetDateTime creationDate,
    OffsetDateTime lastUpdatedDate,
    @Nullable OffsetDateTime completionDate)
    implements TenantOwnedEntity {

  public AgentInstanceEntity {
    Objects.requireNonNull(agentInstanceKey, "agentInstanceKey");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(metrics, "metrics");
    Objects.requireNonNull(limits, "limits");
    Objects.requireNonNull(elementId, "elementId");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(rootProcessInstanceKey, "rootProcessInstanceKey");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(processDefinitionVersion, "processDefinitionVersion");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(creationDate, "creationDate");
    Objects.requireNonNull(lastUpdatedDate, "lastUpdatedDate");
    // Mutable lists required: MyBatis hydrates by calling .add()
    elementInstanceKeys =
        elementInstanceKeys != null ? new ArrayList<>(elementInstanceKeys) : new ArrayList<>();
    tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AgentInstanceDefinition(String model, String provider, String systemPrompt) {
    public AgentInstanceDefinition {
      Objects.requireNonNull(model, "model");
      Objects.requireNonNull(provider, "provider");
      Objects.requireNonNull(systemPrompt, "systemPrompt");
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AgentInstanceLimits(long maxTokens, int maxModelCalls, int maxToolCalls) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AgentInstanceMetrics(
      long inputTokens, long outputTokens, int modelCalls, int toolCalls) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AgentInstanceTool(
      String name, @Nullable String description, @Nullable String elementId) {
    public AgentInstanceTool {
      Objects.requireNonNull(name, "name");
    }
  }

  public enum AgentInstanceStatus {
    INITIALIZING,
    TOOL_DISCOVERY,
    THINKING,
    TOOL_CALLING,
    IDLE,
    COMPLETED,
    UNKNOWN
  }
}
