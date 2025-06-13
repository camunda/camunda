/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;

public record SearchTopHitsAggregator(
    String name, String field, Integer size, List<SearchAggregator> aggregations)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder
      extends SearchAggregator.AbstractBuilder<SearchTopHitsAggregator.Builder>
      implements ObjectBuilder<SearchTopHitsAggregator> {
    private String field;
    private Integer size = 1; // Default to 1 hits

    @Override
    protected SearchTopHitsAggregator.Builder self() {
      return this;
    }

    public SearchTopHitsAggregator.Builder field(final String value) {
      field = value;
      return this;
    }

    public SearchTopHitsAggregator.Builder size(final Integer value) {
      // Validate size to ensure it's a positive integer
      if (value != null && value < 0) {
        throw new IllegalArgumentException("Size must be a positive integer.");
      }
      size = value;
      return this;
    }

    @Override
    public SearchTopHitsAggregator build() {
      return new SearchTopHitsAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(field, "Expected non-null field for field."),
          size,
          aggregations);
    }
  }
}
