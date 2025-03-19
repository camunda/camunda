/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;

public record SearchFilterAggregator(
    String name, SearchQuery query, List<SearchAggregator> aggregations)
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
      implements ObjectBuilder<SearchFilterAggregator> {

    private SearchQuery query;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder query(final SearchQuery value) {
      query = value;
      return this;
    }

    @Override
    public SearchFilterAggregator build() {
      return new SearchFilterAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(query, "Expected non-null field for query."),
          aggregations);
    }
  }
}
