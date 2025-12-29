/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

/**
 * Filter for incident process instance statistics by definition.
 *
 * <p>This filter is intentionally narrow: it only supports filtering by incident state and error
 * hash code.
 */
public record IncidentProcessInstanceStatisticsByDefinitionFilter(Integer errorHashCode, String state)
    implements FilterBase {

  public static IncidentProcessInstanceStatisticsByDefinitionFilter of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionFilter>>
          fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      implements ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionFilter> {

    private Integer errorHashCode;
    private String state;

    public Builder errorHashCode(final Integer value) {
      errorHashCode = value;
      return this;
    }

    public Builder state(final String value) {
      state = value;
      return this;
    }

    @Override
    public IncidentProcessInstanceStatisticsByDefinitionFilter build() {
      return new IncidentProcessInstanceStatisticsByDefinitionFilter(errorHashCode, state);
    }
  }
}
