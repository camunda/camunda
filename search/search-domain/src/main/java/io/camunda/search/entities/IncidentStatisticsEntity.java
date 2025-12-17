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
public record IncidentStatisticsEntity(
    String processDefinitionId,
    Long processDefinitionKey,
    String processDefinitionName,
    Integer processDefinitionVersion,
    String tenantId,
    Integer errorHashCode,
    String errorMessage,
    Long activeInstancesWithErrorCount) {

  public static final class Builder implements ObjectBuilder<IncidentStatisticsEntity> {
    private String processDefinitionId;
    private Long processDefinitionKey;
    private String processDefinitionName;
    private Integer processDefinitionVersion;
    private String tenantId;
    private Integer errorHashCode;
    private String errorMessage;
    private Long activeInstancesWithErrorCount;

    public Builder processDefinitionId(final String value) {
      processDefinitionId = value;
      return this;
    }

    public Builder processDefinitionKey(final Long value) {
      processDefinitionKey = value;
      return this;
    }

    public Builder processDefinitionName(final String value) {
      processDefinitionName = value;
      return this;
    }

    public Builder processDefinitionVersion(final Integer value) {
      processDefinitionVersion = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder errorHashCode(final Integer value) {
      errorHashCode = value;
      return this;
    }

    public Builder errorMessage(final String value) {
      errorMessage = value;
      return this;
    }

    public Builder activeInstancesWithErrorCount(final Long value) {
      activeInstancesWithErrorCount = value;
      return this;
    }

    @Override
    public IncidentStatisticsEntity build() {
      return new IncidentStatisticsEntity(
          processDefinitionId,
          processDefinitionKey,
          processDefinitionName,
          processDefinitionVersion,
          tenantId,
          errorHashCode,
          errorMessage,
          activeInstancesWithErrorCount);
    }
  }
}
