/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * One row of the agent inventory report: an agent aggregated over its completed runs, not a single
 * instance. The agent is identified across tenants by {@code agentId}, so the runs of all tenants
 * the user may see are added together.
 */
public class RawDataAgentDto implements RawDataInstanceDto {

  private String agentId;
  private String agentName;
  private String agentType;
  private String processDefinitionKey;
  private String processName;
  private long agentInstances;
  private Double averageTokensPerAgentInstance;
  private long totalTokens;
  private OffsetDateTime lastAgentInstanceEndDate;

  public RawDataAgentDto() {}

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getAgentName() {
    return agentName;
  }

  public void setAgentName(final String agentName) {
    this.agentName = agentName;
  }

  public String getAgentType() {
    return agentType;
  }

  public void setAgentType(final String agentType) {
    this.agentType = agentType;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessName() {
    return processName;
  }

  public void setProcessName(final String processName) {
    this.processName = processName;
  }

  public long getAgentInstances() {
    return agentInstances;
  }

  public void setAgentInstances(final long agentInstances) {
    this.agentInstances = agentInstances;
  }

  public Double getAverageTokensPerAgentInstance() {
    return averageTokensPerAgentInstance;
  }

  public void setAverageTokensPerAgentInstance(final Double averageTokensPerAgentInstance) {
    this.averageTokensPerAgentInstance = averageTokensPerAgentInstance;
  }

  public long getTotalTokens() {
    return totalTokens;
  }

  public void setTotalTokens(final long totalTokens) {
    this.totalTokens = totalTokens;
  }

  public OffsetDateTime getLastAgentInstanceEndDate() {
    return lastAgentInstanceEndDate;
  }

  public void setLastAgentInstanceEndDate(final OffsetDateTime lastAgentInstanceEndDate) {
    this.lastAgentInstanceEndDate = lastAgentInstanceEndDate;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final RawDataAgentDto that)) {
      return false;
    }
    return agentInstances == that.agentInstances
        && totalTokens == that.totalTokens
        && Objects.equals(agentId, that.agentId)
        && Objects.equals(agentName, that.agentName)
        && Objects.equals(agentType, that.agentType)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processName, that.processName)
        && Objects.equals(averageTokensPerAgentInstance, that.averageTokensPerAgentInstance)
        && Objects.equals(lastAgentInstanceEndDate, that.lastAgentInstanceEndDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        agentId,
        agentName,
        agentType,
        processDefinitionKey,
        processName,
        agentInstances,
        averageTokensPerAgentInstance,
        totalTokens,
        lastAgentInstanceEndDate);
  }

  @Override
  public String toString() {
    return "RawDataAgentDto(agentId="
        + agentId
        + ", agentName="
        + agentName
        + ", agentType="
        + agentType
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processName="
        + processName
        + ", agentInstances="
        + agentInstances
        + ", averageTokensPerAgentInstance="
        + averageTokensPerAgentInstance
        + ", totalTokens="
        + totalTokens
        + ", lastAgentInstanceEndDate="
        + lastAgentInstanceEndDate
        + ")";
  }
}
