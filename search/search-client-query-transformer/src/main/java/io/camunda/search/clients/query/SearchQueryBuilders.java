/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import static io.camunda.util.CollectionUtil.collectValues;
import static io.camunda.util.CollectionUtil.withoutNull;

import io.camunda.search.clients.query.SearchHasParentQuery.Builder;
import io.camunda.search.clients.query.SearchMatchQuery.SearchMatchQueryOperator;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class SearchQueryBuilders {

  private SearchQueryBuilders() {}

  private static SearchQuery must(final List<SearchQuery> queries) {
    return bool().must(queries).build().toSearchQuery();
  }

  private static SearchQuery should(final List<SearchQuery> queries) {
    return bool().should(queries).build().toSearchQuery();
  }

  private static SearchQuery mustNot(final List<SearchQuery> queries) {
    return bool().mustNot(queries).build().toSearchQuery();
  }

  private static SearchQuery map(
      final List<SearchQuery> queries, final Function<List<SearchQuery>, SearchQuery> mapper) {
    final var nonNullQueries = withoutNull(queries);
    if (nonNullQueries == null || nonNullQueries.isEmpty()) {
      return null;
    } else if (nonNullQueries.size() == 1) {
      return nonNullQueries.get(0);
    } else {
      return mapper.apply(nonNullQueries);
    }
  }

  public static SearchBoolQuery.Builder bool() {
    return new SearchBoolQuery.Builder();
  }

  public static SearchBoolQuery bool(
      final Function<SearchBoolQuery.Builder, ObjectBuilder<SearchBoolQuery>> fn) {
    return fn.apply(bool()).build();
  }

  public static SearchQuery and(final SearchQuery query, final SearchQuery... queries) {
    return and(collectValues(query, queries));
  }

  public static SearchQuery and(final List<SearchQuery> queries) {
    return map(queries, SearchQueryBuilders::must);
  }

  public static SearchQuery not(final SearchQuery query, final SearchQuery... queries) {
    return not(collectValues(query, queries));
  }

  public static SearchQuery not(final List<SearchQuery> queries) {
    final var nonNullQueries = withoutNull(queries);
    if (nonNullQueries != null && !nonNullQueries.isEmpty()) {
      return SearchQueryBuilders.mustNot(queries);
    }
    return null;
  }

  public static SearchQuery or(final SearchQuery query, final SearchQuery... queries) {
    return or(collectValues(query, queries));
  }

  public static SearchQuery or(final List<SearchQuery> queries) {
    return map(queries, SearchQueryBuilders::should);
  }

  public static SearchConstantScoreQuery.Builder constantScore() {
    return new SearchConstantScoreQuery.Builder();
  }

  public static SearchConstantScoreQuery constantScore(
      final Function<SearchConstantScoreQuery.Builder, ObjectBuilder<SearchConstantScoreQuery>>
          fn) {
    return fn.apply(constantScore()).build();
  }

  public static SearchQuery constantScore(final SearchQuery query) {
    return constantScore(q -> q.filter(query)).toSearchQuery();
  }

  public static SearchExistsQuery.Builder exists() {
    return new SearchExistsQuery.Builder();
  }

  public static SearchExistsQuery exists(
      final Function<SearchExistsQuery.Builder, ObjectBuilder<SearchExistsQuery>> fn) {
    return fn.apply(exists()).build();
  }

  public static SearchQuery exists(final String field) {
    return exists(q -> q.field(field)).toSearchQuery();
  }

  public static SearchHasChildQuery.Builder hasChild() {
    return new SearchHasChildQuery.Builder();
  }

  public static SearchHasChildQuery hasChild(
      final Function<SearchHasChildQuery.Builder, ObjectBuilder<SearchHasChildQuery>> fn) {
    return fn.apply(hasChild()).build();
  }

  public static SearchIdsQuery.Builder ids() {
    return new SearchIdsQuery.Builder();
  }

  public static SearchIdsQuery ids(
      final Function<SearchIdsQuery.Builder, ObjectBuilder<SearchIdsQuery>> fn) {
    return fn.apply(ids()).build();
  }

  public static SearchQuery ids(final List<String> ids) {
    return ids(q -> q.values(withoutNull(ids))).toSearchQuery();
  }

  public static SearchQuery ids(final Collection<String> ids) {
    return ids(new ArrayList<>(Objects.requireNonNullElse(ids, List.of())));
  }

  public static SearchQuery ids(final String... ids) {
    return ids(List.of(Objects.requireNonNullElse(ids, new String[0])));
  }

  public static SearchMatchQuery.Builder match() {
    return new SearchMatchQuery.Builder();
  }

  public static SearchMatchQuery match(
      final Function<SearchMatchQuery.Builder, ObjectBuilder<SearchMatchQuery>> fn) {
    return fn.apply(match()).build();
  }

  public static <A> SearchQuery match(
      final String field, final String value, final SearchMatchQueryOperator operator) {
    return match((q) -> q.field(field).query(value).operator(operator)).toSearchQuery();
  }

  public static SearchQuery matchAll() {
    return new SearchMatchAllQuery.Builder().build().toSearchQuery();
  }

  public static SearchQuery matchNone() {
    return new SearchMatchNoneQuery.Builder().build().toSearchQuery();
  }

  public static SearchPrefixQuery.Builder prefix() {
    return new SearchPrefixQuery.Builder();
  }

  public static SearchPrefixQuery prefix(
      final Function<SearchPrefixQuery.Builder, ObjectBuilder<SearchPrefixQuery>> fn) {
    return fn.apply(prefix()).build();
  }

  public static SearchQuery prefix(final String field, final String value) {
    return prefix(q -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery.Builder query() {
    return new SearchQuery.Builder();
  }

  public static SearchQuery query(
      final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
    return fn.apply(query()).build();
  }

  public static SearchRangeQuery.Builder range() {
    return new SearchRangeQuery.Builder();
  }

  public static SearchRangeQuery range(
      final Function<SearchRangeQuery.Builder, ObjectBuilder<SearchRangeQuery>> fn) {
    return fn.apply(range()).build();
  }

  public static <A> SearchQuery gt(final String field, final A gt) {
    return SearchRangeQuery.of(q -> q.field(field).gt(gt)).toSearchQuery();
  }

  public static <A> SearchQuery lte(final String field, final A lte) {
    return SearchRangeQuery.of(q -> q.field(field).lte(lte)).toSearchQuery();
  }

  public static <A> SearchQuery gteLte(final String field, final A gte, final A lte) {
    return SearchRangeQuery.of(q -> q.field(field).gte(gte).lte(lte)).toSearchQuery();
  }

  public static <A> SearchQuery gtLte(final String field, final A gt, final A lte) {
    return SearchRangeQuery.of(q -> q.field(field).gt(gt).lte(lte)).toSearchQuery();
  }

  public static SearchQuery hasChildQuery(final String type, final SearchQuery query) {
    return hasChild(q -> q.query(query).type(type)).toSearchQuery();
  }

  public static SearchTermQuery.Builder term() {
    return new SearchTermQuery.Builder();
  }

  public static SearchTermQuery term(
      final Function<SearchTermQuery.Builder, ObjectBuilder<SearchTermQuery>> fn) {
    return fn.apply(term()).build();
  }

  public static SearchQuery term(final String field, final Integer value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final Long value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final Double value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final String value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchQuery term(final String field, final boolean value) {
    return term((q) -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchTermsQuery.Builder terms() {
    return new SearchTermsQuery.Builder();
  }

  public static SearchTermsQuery terms(
      final Function<SearchTermsQuery.Builder, ObjectBuilder<SearchTermsQuery>> fn) {
    return fn.apply(terms()).build();
  }

  public static <C extends Collection<Integer>> SearchQuery intTerms(
      final String field, final C values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return SearchTermsQuery.of(q -> q.field(field).intTerms(fieldValues)).toSearchQuery();
    }
  }

  public static <C extends Collection<Long>> SearchQuery longTerms(
      final String field, final C values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return SearchTermsQuery.of(q -> q.field(field).longTerms(fieldValues)).toSearchQuery();
    }
  }

  public static SearchQuery stringTerms(final String field, final Collection<String> values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return SearchTermsQuery.of(q -> q.field(field).stringTerms(fieldValues)).toSearchQuery();
    }
  }

  public static SearchWildcardQuery.Builder wildcard() {
    return new SearchWildcardQuery.Builder();
  }

  public static SearchWildcardQuery wildcard(
      final Function<SearchWildcardQuery.Builder, ObjectBuilder<SearchWildcardQuery>> fn) {
    return fn.apply(wildcard()).build();
  }

  public static SearchQuery wildcardQuery(final String field, final String value) {
    return wildcard(q -> q.field(field).value(value)).toSearchQuery();
  }

  public static SearchHasParentQuery.Builder hasParent() {
    return new SearchHasParentQuery.Builder();
  }

  public static SearchHasParentQuery hasParent(
      final Function<Builder, ObjectBuilder<SearchHasParentQuery>> fn) {
    return fn.apply(hasParent()).build();
  }

  public static SearchQuery hasParentQuery(final String parent, final SearchQuery query) {
    return hasParent(q -> q.parentType(parent).query(query)).toSearchQuery();
  }
}
