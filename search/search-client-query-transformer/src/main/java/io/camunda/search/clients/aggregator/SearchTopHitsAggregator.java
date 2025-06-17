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

public record SearchTopHitsAggregator<T>(
    String name,
    String field,
    Integer size,
    List<SearchAggregator> aggregations,
    Class<T> documentClass)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder<T>
      extends SearchAggregator.AbstractBuilder<SearchTopHitsAggregator.Builder<T>>
      implements ObjectBuilder<SearchTopHitsAggregator<T>> {
    private String field;
    private Integer size = 1; // Default to 1 hits
    private Class<T> documentClass;

    @Override
    protected SearchTopHitsAggregator.Builder<T> self() {
      return this;
    }

    public SearchTopHitsAggregator.Builder<T> field(final String value) {
      field = value;
      return this;
    }

    public SearchTopHitsAggregator.Builder<T> documentClass(final Class<T> documentClass) {
      this.documentClass = documentClass;
      return this;
    }

    public SearchTopHitsAggregator.Builder<T> size(final Integer value) {
      // Validate size to ensure it's a positive integer
      if (value != null && value < 0) {
        throw new IllegalArgumentException("Size must be a positive integer.");
      }
      size = value;
      return this;
    }

    @Override
    public SearchTopHitsAggregator<T> build() {
      return new SearchTopHitsAggregator<T>(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(field, "Expected non-null field for field."),
          size,
          aggregations,
          documentClass);
    }
  }
}
