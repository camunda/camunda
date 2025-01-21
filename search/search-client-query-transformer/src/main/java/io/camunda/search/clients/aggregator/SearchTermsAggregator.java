/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;

public record SearchTermsAggregator(String name, String field, Integer size, Integer minDocCount)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  public static final class Builder
      extends SearchAggregator.AbstractBuilder<SearchTermsAggregator.Builder>
      implements ObjectBuilder<SearchTermsAggregator> {

    private String field;
    private Integer size = 10; // Default to 10 buckets
    private Integer minDocCount = 1; // Default to showing at least 1 document

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
    protected SearchTermsAggregator.Builder self() {
      return this;
    }

    @Override
    public SearchTermsAggregator build() {
      return new SearchTermsAggregator(
          Objects.requireNonNull(name, "Expected non-null name for terms aggregation."),
          Objects.requireNonNull(field, "Expected non-null field for terms aggregation."),
          size,
          minDocCount);
    }
  }
}
