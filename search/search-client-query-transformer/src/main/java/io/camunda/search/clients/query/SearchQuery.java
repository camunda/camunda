/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import static io.camunda.search.clients.query.SearchQueryBuilders.query;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final record SearchQuery(SearchQueryOption queryOption) {

  public static SearchQuery of(final Function<Builder, ObjectBuilder<SearchQuery>> fn) {
    return query(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchQuery> {

    private SearchQueryOption queryOption;

    public Builder bool(final SearchBoolQuery query) {
      queryOption = query;
      return this;
    }

    public Builder bool(
        final Function<SearchBoolQuery.Builder, ObjectBuilder<SearchBoolQuery>> fn) {
      return bool(SearchQueryBuilders.bool(fn));
    }

    public Builder constantScore(final SearchConstantScoreQuery query) {
      queryOption = query;
      return this;
    }

    public Builder constantScore(
        final Function<SearchConstantScoreQuery.Builder, ObjectBuilder<SearchConstantScoreQuery>>
            fn) {
      return constantScore(SearchQueryBuilders.constantScore(fn));
    }

    public Builder exists(final SearchExistsQuery query) {
      queryOption = query;
      return this;
    }

    public Builder exists(
        final Function<SearchExistsQuery.Builder, ObjectBuilder<SearchExistsQuery>> fn) {
      return exists(SearchQueryBuilders.exists(fn));
    }

    public Builder hasChild(final SearchHasChildQuery query) {
      queryOption = query;
      return this;
    }

    public Builder hasChild(
        final Function<SearchHasChildQuery.Builder, ObjectBuilder<SearchHasChildQuery>> fn) {
      return hasChild(SearchQueryBuilders.hasChild(fn));
    }

    public Builder ids(final SearchIdsQuery query) {
      queryOption = query;
      return this;
    }

    public Builder ids(final Function<SearchIdsQuery.Builder, ObjectBuilder<SearchIdsQuery>> fn) {
      return ids(SearchQueryBuilders.ids(fn));
    }

    public Builder match(final SearchMatchQuery query) {
      queryOption = query;
      return this;
    }

    public Builder match(
        final Function<SearchMatchQuery.Builder, ObjectBuilder<SearchMatchQuery>> fn) {
      return match(SearchQueryBuilders.match(fn));
    }

    public Builder matchAll() {
      queryOption = new SearchMatchAllQuery.Builder().build();
      return this;
    }

    public Builder matchNone(final SearchMatchNoneQuery query) {
      queryOption = new SearchMatchNoneQuery.Builder().build();
      return this;
    }

    public Builder prefix(final SearchPrefixQuery query) {
      queryOption = query;
      return this;
    }

    public Builder prefix(
        final Function<SearchPrefixQuery.Builder, ObjectBuilder<SearchPrefixQuery>> fn) {
      return prefix(SearchQueryBuilders.prefix(fn));
    }

    public Builder range(final SearchRangeQuery query) {
      queryOption = query;
      return this;
    }

    public Builder range(
        final Function<SearchRangeQuery.Builder, ObjectBuilder<SearchRangeQuery>> fn) {
      return range(SearchQueryBuilders.range(fn));
    }

    public Builder term(final SearchTermQuery query) {
      queryOption = query;
      return this;
    }

    public Builder term(
        final Function<SearchTermQuery.Builder, ObjectBuilder<SearchTermQuery>> fn) {
      return term(SearchQueryBuilders.term(fn));
    }

    public Builder terms(final SearchTermsQuery query) {
      queryOption = query;
      return this;
    }

    public Builder terms(
        final Function<SearchTermsQuery.Builder, ObjectBuilder<SearchTermsQuery>> fn) {
      return terms(SearchQueryBuilders.terms(fn));
    }

    public Builder wildcard(final SearchWildcardQuery query) {
      queryOption = query;
      return this;
    }

    public Builder wildcard(
        final Function<SearchWildcardQuery.Builder, ObjectBuilder<SearchWildcardQuery>> fn) {
      return wildcard(SearchQueryBuilders.wildcard(fn));
    }

    public Builder hasParent(final SearchHasParentQuery query) {
      queryOption = query;
      return this;
    }

    public Builder hasParent(
        final Function<SearchHasParentQuery.Builder, ObjectBuilder<SearchHasParentQuery>> fn) {
      return hasParent(SearchQueryBuilders.hasParent(fn));
    }

    @Override
    public SearchQuery build() {
      return new SearchQuery(queryOption);
    }
  }
}
