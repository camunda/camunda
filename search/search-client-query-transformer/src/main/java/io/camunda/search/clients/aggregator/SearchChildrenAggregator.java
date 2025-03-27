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

public record SearchChildrenAggregator(
    String name, String type, List<SearchAggregator> aggregations) implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends SearchAggregator.AbstractBuilder<Builder>
      implements ObjectBuilder<SearchChildrenAggregator> {

    private String type;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public SearchChildrenAggregator build() {
      return new SearchChildrenAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(type, "Expected non-null field for type."),
          aggregations);
    }
  }
}
