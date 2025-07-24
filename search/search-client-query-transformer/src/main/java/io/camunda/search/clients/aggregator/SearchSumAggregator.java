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

public record SearchSumAggregator(String name, String field, List<SearchAggregator> aggregations)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<SearchSumAggregator> {

    private String field;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder field(final String field) {
      this.field = field;
      return this;
    }

    @Override
    public SearchSumAggregator build() {
      return new SearchSumAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(field, "Expected non-null field for field."),
          aggregations);
    }
  }
}
