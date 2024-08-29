/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregation;

import io.camunda.util.ObjectBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record SearchTermsAggregation(
    String field, Integer size, Map<String, SearchAggregation> subAggregations)
    implements SearchAggregationOption {

  public static final class Builder implements ObjectBuilder<SearchTermsAggregation> {
    private String field;
    private Integer size;
    private Map<String, SearchAggregation> subAggregations = new HashMap<>();

    public Builder field(final String field) {
      this.field = field;
      return this;
    }

    public Builder size(final Integer size) {
      this.size = size;
      return this;
    }

    public Builder subAggregations(final Map<String, SearchAggregation> subAggregations) {
      this.subAggregations = subAggregations;
      return this;
    }

    public Builder subAggregation(final String name, final SearchAggregation subAggregation) {
      subAggregations.put(name, subAggregation);
      return this;
    }

    @Override
    public SearchTermsAggregation build() {
      return new SearchTermsAggregation(
          Objects.requireNonNull(field, "Expected non-null field for cardinality aggregation"),
          size,
          subAggregations);
    }
  }
}
