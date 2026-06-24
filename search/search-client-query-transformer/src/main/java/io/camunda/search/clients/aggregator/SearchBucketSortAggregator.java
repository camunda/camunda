/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.search.sort.SortOption.FieldSorting;
import java.util.List;
import java.util.Objects;

public record SearchBucketSortAggregator(
    String name,
    List<FieldSorting> sorting,
    Integer from,
    Integer size,
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

  public static final class Builder extends SearchAggregator.AbstractBuilder<Builder> {

    private List<FieldSorting> sorting;
    private Integer from;
    private Integer size;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder sorting(final List<FieldSorting> value) {
      sorting = value;
      return this;
    }

    public Builder from(final Integer value) {
      if (value != null && value < 0) {
        throw new IllegalArgumentException("From must be greater than or equal to 0.");
      }
      from = value;
      return this;
    }

    public Builder size(final Integer value) {
      if (value != null && value < 0) {
        throw new IllegalArgumentException("Size must be greater than or equal to 0.");
      }
      size = value;
      return this;
    }

    public SearchBucketSortAggregator build() {
      return new SearchBucketSortAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          sorting,
          from,
          size,
          aggregations);
    }
  }
}
