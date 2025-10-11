/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessDefinitionInstanceStatisticsEntity(
    String processDefinitionId,
    String latestProcessDefinitionName,
    Boolean hasMultipleVersions,
    Long activeInstancesWithoutIncidentCount,
    Long activeInstancesWithIncidentCount) {

  public static class Builder implements ObjectBuilder<ProcessDefinitionInstanceStatisticsEntity> {
    private String processDefinitionId;
    private String latestProcessDefinitionName;
    private Boolean hasMultipleVersions;
    private Long activeInstancesWithoutIncidentCount;
    private Long activeInstancesWithIncidentCount;

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder latestProcessDefinitionName(final String latestProcessDefinitionName) {
      this.latestProcessDefinitionName = latestProcessDefinitionName;
      return this;
    }

    public Builder hasMultipleVersions(final Boolean hasMultipleVersions) {
      this.hasMultipleVersions = hasMultipleVersions;
      return this;
    }

    public Builder activeInstancesWithoutIncidentCount(final Long count) {
      activeInstancesWithoutIncidentCount = count;
      return this;
    }

    public Builder activeInstancesWithIncidentCount(final Long count) {
      activeInstancesWithIncidentCount = count;
      return this;
    }

    @Override
    public ProcessDefinitionInstanceStatisticsEntity build() {
      return new ProcessDefinitionInstanceStatisticsEntity(
          processDefinitionId,
          latestProcessDefinitionName,
          hasMultipleVersions,
          activeInstancesWithoutIncidentCount,
          activeInstancesWithIncidentCount);
    }
  }
}
