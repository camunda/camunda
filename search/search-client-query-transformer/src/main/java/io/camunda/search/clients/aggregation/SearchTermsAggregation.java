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

public record SearchTermsAggregation(String name, String field, Integer size, Integer minDocCount)
    implements SearchAggregation {

  @Override
  public String getName() {
    return name;
  }

  public static final class Builder extends SearchAggregation.AbstractBuilder<Builder>
      implements ObjectBuilder<SearchTermsAggregation> {

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
    public SearchTermsAggregation build() {
      return new SearchTermsAggregation(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(field, "Expected non-null field for terms aggregation."),
          size,
          minDocCount);
    }

    @Override
    protected Builder self() {
      return this;
    }
  }
}
