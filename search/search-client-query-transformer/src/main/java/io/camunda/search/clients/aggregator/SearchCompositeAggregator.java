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

public record SearchCompositeAggregator(
    String name,
    Integer size,
    String after,
    List<SearchAggregator> aggregations,
    List<SearchAggregator> sources)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends AbstractBuilder<SearchCompositeAggregator.Builder>
      implements ObjectBuilder<SearchCompositeAggregator> {
    private Integer size = 10000;
    private String after;
    private List<SearchAggregator> sources;

    @Override
    protected SearchCompositeAggregator.Builder self() {
      return this;
    }

    public SearchCompositeAggregator.Builder size(final Integer value) {
      // Validate size to ensure it's a positive integer
      if (value != null && value < 0) {
        throw new IllegalArgumentException("Size must be a positive integer.");
      }
      size = value;
      return this;
    }

    public SearchCompositeAggregator.Builder after(final String value) {
      after = value;
      return this;
    }

    public SearchCompositeAggregator.Builder sources(final List<SearchAggregator> sources) {
      this.sources = sources;
      return this;
    }

    @Override
    public SearchCompositeAggregator build() {
      return new SearchCompositeAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          size,
          after,
          aggregations,
          Objects.requireNonNull(sources, "Expected non-null field for sources."));
    }
  }
}
