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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SearchFiltersAggregator(
    String name, Map<String, SearchQuery> queries, List<SearchAggregator> aggregations)
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
      implements ObjectBuilder<SearchFiltersAggregator> {

    private Map<String, SearchQuery> queries;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder namedQuery(final String name, final SearchQuery query) {
      if (queries == null) {
        queries = new HashMap<>();
      }
      queries.put(name, query);
      return this;
    }

    @Override
    public SearchFiltersAggregator build() {
      return new SearchFiltersAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          Objects.requireNonNull(queries, "Expected non-null field for queries."),
          aggregations);
    }
  }
}
