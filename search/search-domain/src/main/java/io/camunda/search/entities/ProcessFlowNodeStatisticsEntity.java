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
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessFlowNodeStatisticsEntity(
    String flowNodeId, Long active, Long canceled, Long incidents, Long completed) {

  public static class Builder implements ObjectBuilder<ProcessFlowNodeStatisticsEntity> {

    private String flowNodeId;
    private long active;
    private long canceled;
    private long incidents;
    private long completed;

    public Builder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public Builder active(final long active) {
      this.active = active;
      return this;
    }

    public Builder canceled(final long canceled) {
      this.canceled = canceled;
      return this;
    }

    public Builder incidents(final long incidents) {
      this.incidents = incidents;
      return this;
    }

    public Builder completed(final long completed) {
      this.completed = completed;
      return this;
    }

    @Override
    public ProcessFlowNodeStatisticsEntity build() {
      return new ProcessFlowNodeStatisticsEntity(
          Objects.requireNonNull(flowNodeId, "Expected non-null field for flowNodeId."),
          active,
          canceled,
          incidents,
          completed);
    }
  }
}
