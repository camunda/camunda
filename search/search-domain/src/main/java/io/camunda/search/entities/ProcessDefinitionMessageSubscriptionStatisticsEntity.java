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
public record ProcessDefinitionMessageSubscriptionStatisticsEntity(
    String processDefinitionId,
    String tenantId,
    Long processDefinitionKey,
    Long processInstancesWithActiveSubscriptions,
    Long activeSubscriptions) {

  public static class Builder
      implements ObjectBuilder<ProcessDefinitionMessageSubscriptionStatisticsEntity> {

    private String processDefinitionId;
    private String tenantId;
    private Long processDefinitionKey;
    private Long processInstancesWithActiveSubscriptions;
    private Long activeSubscriptions;

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstancesWithActiveSubscriptions(
        final Long processInstancesWithActiveSubscriptions) {
      this.processInstancesWithActiveSubscriptions = processInstancesWithActiveSubscriptions;
      return this;
    }

    public Builder activeSubscriptions(final Long activeSubscriptions) {
      this.activeSubscriptions = activeSubscriptions;
      return this;
    }

    @Override
    public ProcessDefinitionMessageSubscriptionStatisticsEntity build() {
      return new ProcessDefinitionMessageSubscriptionStatisticsEntity(
          processDefinitionId,
          tenantId,
          processDefinitionKey,
          processInstancesWithActiveSubscriptions,
          activeSubscriptions);
    }
  }
}
