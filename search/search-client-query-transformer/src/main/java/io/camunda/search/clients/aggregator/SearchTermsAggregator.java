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

public record SearchTermsAggregator(
    String name,
    String field,
    Integer size,
    Integer minDocCount,
    List<SearchAggregator> aggregations)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends SearchAggregator.AbstractBuilder<Builder>
      implements ObjectBuilder<SearchTermsAggregator> {

    private String field;
    private Integer size = 10; // Default to 10 buckets
    private Integer minDocCount = 1; // Default to showing at least 1 document

    @Override
    protected Builder self() {
      return this;
    }

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder size(final Integer value) {
      // Validate size to ensure it's a positive integer
      if (value != null && value < 0) {
        throw new IllegalArgumentException("Size must be a positive integer.");
      }
      size = value;
      return this;
    }

    public Builder minDocCount(final Integer value) {
      minDocCount = value;
      return this;
    }

    @Override
    public SearchTermsAggregator build() {
      return new SearchTermsAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(field, "Expected non-null field for field."),
          size,
          minDocCount,
          aggregations);
    }
  }
}
