/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;

public record ProcessDefinitionInstanceVersionStatisticsFilter(
    String processDefinitionId, String tenantId) implements FilterBase {

  public Builder toBuilder() {
    return new Builder().processDefinitionId(processDefinitionId).tenantId(tenantId);
  }

  public static final class Builder
      implements ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsFilter> {
    private String processDefinitionId;
    private String tenantId;

    public Builder processDefinitionId(final String value) {
      processDefinitionId = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    @Override
    public ProcessDefinitionInstanceVersionStatisticsFilter build() {
      return new ProcessDefinitionInstanceVersionStatisticsFilter(processDefinitionId, tenantId);
    }
  }
}
