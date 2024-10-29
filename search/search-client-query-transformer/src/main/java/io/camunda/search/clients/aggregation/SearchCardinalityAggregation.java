/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;

public record SearchCardinalityAggregation(String field, Integer precisionThreshold)
    implements SearchAggregationOption {

  public static final class Builder implements ObjectBuilder<SearchCardinalityAggregation> {

    private String field;
    private Integer precisionThreshold;

    public Builder field(final String field) {
      this.field = field;
      return this;
    }

    public Builder precisionThreshold(final Integer precisionThreshold) {
      this.precisionThreshold = precisionThreshold;
      return this;
    }

    @Override
    public SearchCardinalityAggregation build() {
      return new SearchCardinalityAggregation(
          Objects.requireNonNull(field, "Expected non-null field for cardinality aggregation"),
          precisionThreshold);
    }
  }
}
