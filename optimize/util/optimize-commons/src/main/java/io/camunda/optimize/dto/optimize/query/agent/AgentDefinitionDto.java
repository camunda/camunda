/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agent;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One logical agent (tenant + process definition key + flow node id), across all process versions.
 *
 * <p>Identity ({@code name}, {@code agentType}, versions) comes from the engine's agent definition
 * records. Model, provider, limits and first/last seen are rolled up from the agent's runs.
 */
public class AgentDefinitionDto implements OptimizeDto {

  private String agentId;
  private String tenantId;
  private String processDefinitionKey;
  private String flowNodeId;
  private String name;
  private String agentType;
  private List<String> agentDefinitionIds = new ArrayList<>();
  private List<String> processDefinitionVersions = new ArrayList<>();

  public AgentDefinitionDto() {}

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getAgentType() {
    return agentType;
  }

  public void setAgentType(final String agentType) {
    this.agentType = agentType;
  }

  public List<String> getAgentDefinitionIds() {
    return agentDefinitionIds;
  }

  public void setAgentDefinitionIds(final List<String> agentDefinitionIds) {
    this.agentDefinitionIds = agentDefinitionIds;
  }

  public List<String> getProcessDefinitionVersions() {
    return processDefinitionVersions;
  }

  public void setProcessDefinitionVersions(final List<String> processDefinitionVersions) {
    this.processDefinitionVersions = processDefinitionVersions;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AgentDefinitionDto that = (AgentDefinitionDto) o;
    return Objects.equals(agentId, that.agentId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(name, that.name)
        && Objects.equals(agentType, that.agentType)
        && Objects.equals(agentDefinitionIds, that.agentDefinitionIds)
        && Objects.equals(processDefinitionVersions, that.processDefinitionVersions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentId,
        tenantId,
        processDefinitionKey,
        flowNodeId,
        name,
        agentType,
        agentDefinitionIds,
        processDefinitionVersions);
  }

  @Override
  public String toString() {
    return "AgentDefinitionDto("
        + "agentId="
        + agentId
        + ", tenantId="
        + tenantId
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", flowNodeId="
        + flowNodeId
        + ", name="
        + name
        + ", agentType="
        + agentType
        + ", agentDefinitionIds="
        + agentDefinitionIds
        + ", processDefinitionVersions="
        + processDefinitionVersions
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String agentId = "agentId";
    public static final String tenantId = "tenantId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String flowNodeId = "flowNodeId";
    public static final String name = "name";
    public static final String agentType = "agentType";
    public static final String agentDefinitionIds = "agentDefinitionIds";
    public static final String processDefinitionVersions = "processDefinitionVersions";
  }
}
