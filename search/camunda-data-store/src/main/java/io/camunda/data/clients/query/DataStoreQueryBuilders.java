/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.data.clients.util.DataStoreQueryBuildersDelegate;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class DataStoreQueryBuilders {

  private static DataStoreQueryBuildersDelegate queryBuilders;

  private DataStoreQueryBuilders() {}

  public static void setQueryBuilders(final DataStoreQueryBuildersDelegate queryBuilders) {
    DataStoreQueryBuilders.queryBuilders = queryBuilders;
  }

  //  static Script script(String script, Map<String, Object> params) {
  //    return new Script.Builder()
  //        .inline(b -> b.source(script).params(jsonParams(params)).lang(DEFAULT_SCRIPT_LANG))
  //        .build();
  //  }

  //
  //  static SourceConfig sourceInclude(String... fields) {
  //    if (CollectionUtil.isEmpty(fields)) {
  //      return sourceInclude(List.of());
  //    }
  //    return sourceInclude(List.of(fields));
  //  }
  //
  //  static SourceConfig sourceExclude(String... fields) {
  //    if (CollectionUtil.isEmpty(fields)) {
  //      return sourceExclude(List.of());
  //    }
  //    return sourceExclude(List.of(fields));
  //  }
  //
  //  static SourceConfig sourceIncludesExcludes(String[] includes, String[] excludes) {
  //    return sourceIncludesExcludes(
  //        includes == null ? List.of() : List.of(includes),
  //        excludes == null ? List.of() : List.of(excludes));
  //  }
  //
  //  static SourceConfig sourceExclude(List<String> fields) {
  //    return SourceConfig.of(s -> s.filter(f -> f.excludes(fields)));
  //  }
  //
  //  static SourceConfig sourceInclude(List<String> fields) {
  //    return SourceConfig.of(s -> s.filter(f -> f.includes(fields)));
  //  }
  //
  //  static SourceConfig sourceIncludesExcludes(List<String> includes, List<String> excludes) {
  //    return SourceConfig.of(s -> s.filter(f -> f.includes(includes).excludes(excludes)));
  //  }

  //  static Query matchDateQuery(final String name, final String dateAsString, String dateFormat) {
  //    // Used to match in different time ranges like hours, minutes etc
  //    // See:
  //    //
  // https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#date-math
  //    return RangeQuery.of(
  //            q ->
  // q.field(name).gte(json(dateAsString)).lte(json(dateAsString)).format(dateFormat))
  //        ._toQuery();
  //  }

  public static DataStoreBoolQuery.Builder bool() {
    return queryBuilders.bool();
  }

  public static DataStoreBoolQuery bool(
      final Function<DataStoreBoolQuery.Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn) {
    return queryBuilders.bool(fn);
  }

  public static DataStoreQuery and(final DataStoreQuery... queries) {
    return and(nonNull(queries));
  }

  public static DataStoreQuery and(final List<DataStoreQuery> queries) {
    return DataStoreBoolQuery.of(q -> q.must(nonNull(queries))).toQuery();
  }

  public static DataStoreQuery not(final DataStoreQuery... queries) {
    return DataStoreBoolQuery.of(q -> q.mustNot(nonNull(queries))).toQuery();
  }

  public static DataStoreQuery or(final DataStoreQuery... queries) {
    return DataStoreBoolQuery.of(q -> q.should(nonNull(queries))).toQuery();
  }

  public static DataStoreConstantScoreQuery.Builder constantScore() {
    return queryBuilders.constantScore();
  }

  public static DataStoreConstantScoreQuery constantScore(
      final Function<
              DataStoreConstantScoreQuery.Builder,
              DataStoreObjectBuilder<DataStoreConstantScoreQuery>>
          fn) {
    return queryBuilders.constantScore(fn);
  }

  public static DataStoreQuery constantScore(final DataStoreQuery query) {
    return DataStoreConstantScoreQuery.of(q -> q.filter(query)).toQuery();
  }

  public static DataStoreExistsQuery.Builder exists() {
    return queryBuilders.exists();
  }

  public static DataStoreExistsQuery exists(
      final Function<DataStoreExistsQuery.Builder, DataStoreObjectBuilder<DataStoreExistsQuery>>
          fn) {
    return queryBuilders.exists(fn);
  }

  public static DataStoreQuery exists(final String field) {
    return DataStoreExistsQuery.of(q -> q.field(field)).toQuery();
  }

  public static DataStoreHasChildQuery.Builder hasChild() {
    return queryBuilders.hasChild();
  }

  public static DataStoreHasChildQuery hasChild(
      final Function<DataStoreHasChildQuery.Builder, DataStoreObjectBuilder<DataStoreHasChildQuery>>
          fn) {
    return queryBuilders.hasChild(fn);
  }

  public static DataStoreIdsQuery.Builder ids() {
    return queryBuilders.ids();
  }

  public static DataStoreIdsQuery ids(
      final Function<DataStoreIdsQuery.Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn) {
    return queryBuilders.ids(fn);
  }

  public static DataStoreQuery ids(final List<String> ids) {
    return DataStoreIdsQuery.of(q -> q.values(nonNull(ids))).toQuery();
  }

  public static DataStoreQuery ids(final Collection<String> ids) {
    return ids(ids.stream().toList());
  }

  public static DataStoreQuery ids(final String... ids) {
    return ids(List.of(ids));
  }

  public static DataStoreMatchQuery.Builder match() {
    return queryBuilders.match();
  }

  public static DataStoreMatchQuery match(
      final Function<DataStoreMatchQuery.Builder, DataStoreObjectBuilder<DataStoreMatchQuery>> fn) {
    return queryBuilders.match(fn);
  }

  public static <A> DataStoreQuery match(
      final String field, final String value, final String operator) {
    return DataStoreMatchQuery.of((q) -> q.field(field).query(value).operator(operator)).toQuery();
  }

  public static DataStoreQuery matchAll() {
    return queryBuilders.matchAll().build().toQuery();
  }

  public static DataStoreMatchAllQuery matchAll(
      final Function<DataStoreMatchAllQuery.Builder, DataStoreObjectBuilder<DataStoreMatchAllQuery>>
          fn) {
    return queryBuilders.matchAll(fn);
  }

  public static DataStoreMatchNoneQuery.Builder matchNone() {
    return queryBuilders.matchNone();
  }

  public static DataStoreMatchNoneQuery matchNone(
      final Function<
              DataStoreMatchNoneQuery.Builder, DataStoreObjectBuilder<DataStoreMatchNoneQuery>>
          fn) {
    return queryBuilders.matchNone(fn);
  }

  public static DataStorePrefixQuery.Builder prefix() {
    return queryBuilders.prefix();
  }

  public static DataStorePrefixQuery prefix(
      final Function<DataStorePrefixQuery.Builder, DataStoreObjectBuilder<DataStorePrefixQuery>>
          fn) {
    return queryBuilders.prefix(fn);
  }

  public static DataStoreQuery prefix(final String field, final String value) {
    return DataStorePrefixQuery.of(q -> q.field(field).value(value)).toQuery();
  }

  public static DataStoreQuery.Builder query() {
    return queryBuilders.query();
  }

  public static DataStoreQuery query(
      final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
    return queryBuilders.query(fn);
  }

  public static DataStoreRangeQuery.Builder range() {
    return queryBuilders.range();
  }

  public static DataStoreRangeQuery range(
      final Function<DataStoreRangeQuery.Builder, DataStoreObjectBuilder<DataStoreRangeQuery>> fn) {
    return queryBuilders.range(fn);
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
    return queryBuilders.term();
  }

  public static DataStoreTermQuery term(
      final Function<DataStoreTermQuery.Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn) {
    return queryBuilders.term(fn);
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
    return queryBuilders.terms();
  }

  public static DataStoreTermsQuery terms(
      final Function<DataStoreTermsQuery.Builder, DataStoreObjectBuilder<DataStoreTermsQuery>> fn) {
    return queryBuilders.terms(fn);
  }

  public static <C extends Collection<Integer>> DataStoreQuery intTerms(
      final String field, final C values) {
    final var fieldValues = nonNull(values);
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
    final var fieldValues = nonNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return DataStoreTermsQuery.of(q -> q.field(field).longTerms(fieldValues)).toQuery();
    }
  }

  public static DataStoreQuery stringTerms(final String field, final Collection<String> values) {
    final var fieldValues = nonNull(values);
    if (fieldValues == null || fieldValues.isEmpty()) {
      return null;
    } else if (fieldValues.size() == 1) {
      return term(field, fieldValues.get(0));
    } else {
      return DataStoreTermsQuery.of(q -> q.field(field).stringTerms(fieldValues)).toQuery();
    }
  }

  public static DataStoreWildcardQuery.Builder wildcard() {
    return queryBuilders.wildcard();
  }

  public static DataStoreWildcardQuery wildcard(
      final Function<DataStoreWildcardQuery.Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>>
          fn) {
    return queryBuilders.wildcard(fn);
  }

  public static DataStoreQuery wildcardQuery(final String field, final String value) {
    return DataStoreWildcardQuery.of(q -> q.field(field).value(value)).toQuery();
  }

  private static <A> List<A> nonNull(final A[] items) {
    return nonNull(Arrays.asList(items));
  }

  private static <A> List<A> nonNull(final Collection<A> items) {
    return items.stream().filter(Objects::nonNull).toList();
  }
}
