/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Narrows agent run reports to some agents and/or models. Empty lists mean "no restriction". Only
 * applies to the agent-instance index; process-instance queries ignore it.
 */
public class AgentRunFilterDataDto implements FilterDataDto {

  private List<String> agentIds = new ArrayList<>();
  private List<String> models = new ArrayList<>();
  private List<String> providers = new ArrayList<>();

  public AgentRunFilterDataDto() {}

  public AgentRunFilterDataDto(
      final List<String> agentIds, final List<String> models, final List<String> providers) {
    this.agentIds = agentIds;
    this.models = models;
    this.providers = providers;
  }

  public List<String> getAgentIds() {
    return agentIds;
  }

  public void setAgentIds(final List<String> agentIds) {
    this.agentIds = agentIds;
  }

  public List<String> getModels() {
    return models;
  }

  public void setModels(final List<String> models) {
    this.models = models;
  }

  public List<String> getProviders() {
    return providers;
  }

  public void setProviders(final List<String> providers) {
    this.providers = providers;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AgentRunFilterDataDto that = (AgentRunFilterDataDto) o;
    return Objects.equals(agentIds, that.agentIds)
        && Objects.equals(models, that.models)
        && Objects.equals(providers, that.providers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(agentIds, models, providers);
  }

  @Override
  public String toString() {
    return "AgentRunFilterDataDto(agentIds="
        + agentIds
        + ", models="
        + models
        + ", providers="
        + providers
        + ")";
  }
}
