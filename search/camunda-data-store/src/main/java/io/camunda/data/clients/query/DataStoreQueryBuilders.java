/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import static io.camunda.util.DataStoreCollectionUtil.listAdd;
import static io.camunda.util.DataStoreCollectionUtil.withoutNull;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class DataStoreQueryBuilders {

  private static DataStoreQuery must(final List<DataStoreQuery> queries) {
    return bool().must(queries).build().toQuery();
  }

  private static DataStoreQuery should(final List<DataStoreQuery> queries) {
    return bool().should(queries).build().toQuery();
  }

  private static DataStoreQuery mustNot(final List<DataStoreQuery> queries) {
    return bool().mustNot(queries).build().toQuery();
  }

  private static DataStoreQuery map(
      final List<DataStoreQuery> queries,
      final Function<List<DataStoreQuery>, DataStoreQuery> mapper) {
    final var nonNullQueries = withoutNull(queries);
    if (nonNullQueries == null || nonNullQueries.isEmpty()) {
      return null;
    } else if (nonNullQueries.size() == 1) {
      return nonNullQueries.get(0);
    } else {
      return mapper.apply(nonNullQueries);
    }
  }

  public static DataStoreBoolQuery.Builder bool() {
    return new DataStoreBoolQuery.Builder();
  }

  public static DataStoreBoolQuery bool(
      final Function<DataStoreBoolQuery.Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn) {
    return fn.apply(bool()).build();
  }

  public static DataStoreQuery and(final DataStoreQuery query, final DataStoreQuery... queries) {
    return and(listAdd(new ArrayList<DataStoreQuery>(), query, queries));
  }

  public static DataStoreQuery and(final List<DataStoreQuery> queries) {
    return map(queries, DataStoreQueryBuilders::must);
  }

  public static DataStoreQuery not(final DataStoreQuery query, final DataStoreQuery... queries) {
    return not(listAdd(new ArrayList<DataStoreQuery>(), query, queries));
  }

  public static DataStoreQuery not(final List<DataStoreQuery> queries) {
    return map(queries, DataStoreQueryBuilders::mustNot);
  }

  public static DataStoreQuery or(final DataStoreQuery query, final DataStoreQuery... queries) {
    return or(listAdd(new ArrayList<DataStoreQuery>(), query, queries));
  }

  public static DataStoreQuery or(final List<DataStoreQuery> queries) {
    return map(queries, DataStoreQueryBuilders::should);
  }

  public static DataStoreConstantScoreQuery.Builder constantScore() {
    return new DataStoreConstantScoreQuery.Builder();
  }

  public static DataStoreConstantScoreQuery constantScore(
      final Function<
              DataStoreConstantScoreQuery.Builder,
              DataStoreObjectBuilder<DataStoreConstantScoreQuery>>
          fn) {
    return fn.apply(constantScore()).build();
  }

  public static DataStoreQuery constantScore(final DataStoreQuery query) {
    return DataStoreConstantScoreQuery.of(q -> q.filter(query)).toQuery();
  }

  public static DataStoreExistsQuery.Builder exists() {
    return new DataStoreExistsQuery.Builder();
  }

  public static DataStoreExistsQuery exists(
      final Function<DataStoreExistsQuery.Builder, DataStoreObjectBuilder<DataStoreExistsQuery>>
          fn) {
    return fn.apply(exists()).build();
  }

  public static DataStoreQuery exists(final String field) {
    return DataStoreExistsQuery.of(q -> q.field(field)).toQuery();
  }

  public static DataStoreHasChildQuery.Builder hasChild() {
    return new DataStoreHasChildQuery.Builder();
  }

  public static DataStoreHasChildQuery hasChild(
      final Function<DataStoreHasChildQuery.Builder, DataStoreObjectBuilder<DataStoreHasChildQuery>>
          fn) {
    return fn.apply(hasChild()).build();
  }

  public static DataStoreIdsQuery.Builder ids() {
    return new DataStoreIdsQuery.Builder();
  }

  public static DataStoreIdsQuery ids(
      final Function<DataStoreIdsQuery.Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn) {
    return fn.apply(ids()).build();
  }

  public static DataStoreQuery ids(final List<String> ids) {
    return DataStoreIdsQuery.of(q -> q.values(withoutNull(ids))).toQuery();
  }

  public static DataStoreQuery ids(final Collection<String> ids) {
    return ids(ids.stream().toList());
  }

  public static DataStoreQuery ids(final String... ids) {
    return ids(List.of(ids));
  }

  public static DataStoreMatchQuery.Builder match() {
    return new DataStoreMatchQuery.Builder();
  }

  public static DataStoreMatchQuery match(
      final Function<DataStoreMatchQuery.Builder, DataStoreObjectBuilder<DataStoreMatchQuery>> fn) {
    return fn.apply(match()).build();
  }

  public static <A> DataStoreQuery match(
      final String field, final String value, final String operator) {
    return DataStoreMatchQuery.of((q) -> q.field(field).query(value).operator(operator)).toQuery();
  }

  public static DataStoreQuery matchAll() {
    return new DataStoreMatchAllQuery.Builder().build().toQuery();
  }

  public static DataStoreQuery matchNone() {
    return new DataStoreMatchNoneQuery.Builder().build().toQuery();
  }

  public static DataStorePrefixQuery.Builder prefix() {
    return new DataStorePrefixQuery.Builder();
  }

  public static DataStorePrefixQuery prefix(
      final Function<DataStorePrefixQuery.Builder, DataStoreObjectBuilder<DataStorePrefixQuery>>
          fn) {
    return fn.apply(prefix()).build();
  }

  public static DataStoreQuery prefix(final String field, final String value) {
    return DataStorePrefixQuery.of(q -> q.field(field).value(value)).toQuery();
  }

  public static DataStoreQuery.Builder query() {
    return new DataStoreQuery.Builder();
  }

  public static DataStoreQuery query(
      final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
    return fn.apply(query()).build();
  }

  public static DataStoreRangeQuery.Builder range() {
    return new DataStoreRangeQuery.Builder();
  }

  public static DataStoreRangeQuery range(
      final Function<DataStoreRangeQuery.Builder, DataStoreObjectBuilder<DataStoreRangeQuery>> fn) {
    return fn.apply(range()).build();
  }

  public static <A> DataStoreQuery gt(final String field, final A gt) {
    return DataStoreRangeQuery.of(q -> q.field(field).gt(gt)).toQuery();
  }

  public static <A> DataStoreQuery lte(final String field, final A lte) {
    return DataStoreRangeQuery.of(q -> q.field(field).lte(lte)).toQuery();
  }

  public static <A> DataStoreQuery gteLte(final String field, final A gte, final A lte) {
    return DataStoreRangeQuery.of(q -> q.field(field).gte(gte).lte(lte)).toQuery();
  }

  public static <A> DataStoreQuery gtLte(final String field, final A gt, final A lte) {
    return DataStoreRangeQuery.of(q -> q.field(field).gt(gt).lte(lte)).toQuery();
  }

  public static DataStoreQuery hasChildQuery(final String type, final DataStoreQuery query) {
    return DataStoreHasChildQuery.of(q -> q.query(query).type(type)).toQuery();
  }

  public static DataStoreTermQuery.Builder term() {
    return new DataStoreTermQuery.Builder();
  }

  public static DataStoreTermQuery term(
      final Function<DataStoreTermQuery.Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn) {
    return fn.apply(term()).build();
  }

  public static DataStoreQuery term(final String field, final Integer value) {
    return DataStoreTermQuery.of((q) -> q.field(field).value(value)).toQuery();
  }

  public static DataStoreQuery term(final String field, final Long value) {
    return DataStoreTermQuery.of((q) -> q.field(field).value(value)).toQuery();
  }

  public static DataStoreQuery term(final String field, final Double value) {
    return DataStoreTermQuery.of((q) -> q.field(field).value(value)).toQuery();
  }

  public static DataStoreQuery term(final String field, final String value) {
    return DataStoreTermQuery.of((q) -> q.field(field).value(value)).toQuery();
  }

  public static DataStoreQuery term(final String field, final boolean value) {
    return DataStoreTermQuery.of((q) -> q.field(field).value(value)).toQuery();
  }

  public static DataStoreTermsQuery.Builder terms() {
    return new DataStoreTermsQuery.Builder();
  }

  public static DataStoreTermsQuery terms(
      final Function<DataStoreTermsQuery.Builder, DataStoreObjectBuilder<DataStoreTermsQuery>> fn) {
    return fn.apply(terms()).build();
  }

  public static <C extends Collection<Integer>> DataStoreQuery intTerms(
      final String field, final C values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return DataStoreTermsQuery.of(q -> q.field(field).intTerms(fieldValues)).toQuery();
    }
  }

  public static <C extends Collection<Long>> DataStoreQuery longTerms(
      final String field, final C values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return DataStoreTermsQuery.of(q -> q.field(field).longTerms(fieldValues)).toQuery();
    }
  }

  public static DataStoreQuery stringTerms(final String field, final Collection<String> values) {
    final var fieldValues = withoutNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return DataStoreTermsQuery.of(q -> q.field(field).stringTerms(fieldValues)).toQuery();
    }
  }

  public static DataStoreWildcardQuery.Builder wildcard() {
    return new DataStoreWildcardQuery.Builder();
  }

  public static DataStoreWildcardQuery wildcard(
      final Function<DataStoreWildcardQuery.Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>>
          fn) {
    return fn.apply(wildcard()).build();
  }

  public static DataStoreQuery wildcardQuery(final String field, final String value) {
    return DataStoreWildcardQuery.of(q -> q.field(field).value(value)).toQuery();
  }
}
