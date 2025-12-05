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
public record ProcessDefinitionInstanceVersionStatisticsEntity(
    String processDefinitionId,
    Long processDefinitionKey,
    Integer processDefinitionVersion,
    String processDefinitionName,
    String tenantId,
    Long activeInstancesWithoutIncidentCount,
    Long activeInstancesWithIncidentCount) {

  public static class Builder
      implements ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsEntity> {
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Integer processDefinitionVersion;
    private String processDefinitionName;
    private String tenantId;
    private Long activeInstancesWithoutIncidentCount;
    private Long activeInstancesWithIncidentCount;

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    public Builder processDefinitionName(final String processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
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
    public ProcessDefinitionInstanceVersionStatisticsEntity build() {
      return new ProcessDefinitionInstanceVersionStatisticsEntity(
          processDefinitionId,
          processDefinitionKey,
          processDefinitionVersion,
          processDefinitionName,
          tenantId,
          activeInstancesWithoutIncidentCount,
          activeInstancesWithIncidentCount);
    }
  }
}
