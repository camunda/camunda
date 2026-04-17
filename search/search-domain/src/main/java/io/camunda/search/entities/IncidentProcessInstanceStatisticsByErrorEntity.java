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
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentProcessInstanceStatisticsByErrorEntity(
    Integer errorHashCode,
    // null for the `__NULL__` sentinel bucket (docs without an error message).
    @Nullable String errorMessage,
    Long activeInstancesWithErrorCount) {

  public IncidentProcessInstanceStatisticsByErrorEntity {
    Objects.requireNonNull(errorHashCode, "errorHashCode");
    Objects.requireNonNull(activeInstancesWithErrorCount, "activeInstancesWithErrorCount");
  }

  public static final class Builder
      implements ObjectBuilder<IncidentProcessInstanceStatisticsByErrorEntity> {
    private Integer errorHashCode;
    private String errorMessage;
    private Long activeInstancesWithErrorCount;

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
    public IncidentProcessInstanceStatisticsByErrorEntity build() {
      return new IncidentProcessInstanceStatisticsByErrorEntity(
          errorHashCode, errorMessage, activeInstancesWithErrorCount);
    }
  }
}
