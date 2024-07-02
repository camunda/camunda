/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final record SearchBoolQuery(
    List<SearchQuery> filter,
    List<SearchQuery> must,
    List<SearchQuery> mustNot,
    List<SearchQuery> should)
    implements SearchQueryOption {

  static SearchBoolQuery of(final Function<Builder, ObjectBuilder<SearchBoolQuery>> fn) {
    return SearchQueryBuilders.bool(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchBoolQuery> {

    private List<SearchQuery> filter;
    private List<SearchQuery> must;
    private List<SearchQuery> mustNot;
    private List<SearchQuery> should;

    public Builder filter(final List<SearchQuery> queries) {
      filter = addValuesToList(filter, queries);
      return this;
    }

    public Builder filter(final SearchQuery query, final SearchQuery... queries) {
      return filter(collectValues(query, queries));
    }

    public Builder filter(final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
      return filter(SearchQueryBuilders.query(fn));
    }

    public Builder must(final List<SearchQuery> queries) {
      must = addValuesToList(must, queries);
      return this;
    }

    public Builder must(final SearchQuery query, final SearchQuery... queries) {
      return must(collectValues(query, queries));
    }

    public Builder must(final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
      return must(SearchQueryBuilders.query(fn));
    }

    public Builder mustNot(final List<SearchQuery> queries) {
      mustNot = addValuesToList(mustNot, queries);
      return this;
    }

    public Builder mustNot(final SearchQuery query, final SearchQuery... queries) {
      return mustNot(collectValues(query, queries));
    }

    public Builder mustNot(final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
      return mustNot(SearchQueryBuilders.query(fn));
    }

    public Builder should(final List<SearchQuery> queries) {
      should = addValuesToList(should, queries);
      return this;
    }

    public Builder should(final SearchQuery query, final SearchQuery... queries) {
      return should(collectValues(query, queries));
    }

    public Builder should(final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
      return should(SearchQueryBuilders.query(fn));
    }

    @Override
    public SearchBoolQuery build() {
      return new SearchBoolQuery(
          Objects.requireNonNullElse(filter, Collections.emptyList()),
          Objects.requireNonNullElse(must, Collections.emptyList()),
          Objects.requireNonNullElse(mustNot, Collections.emptyList()),
          Objects.requireNonNullElse(should, Collections.emptyList()));
    }
  }
}
