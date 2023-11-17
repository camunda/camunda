/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.dsl;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.ConstantScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.HasChildQuery;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchNoneQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.PrefixQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;
import org.opensearch.client.opensearch._types.query_dsl.WildcardQuery;
import org.opensearch.client.opensearch.core.search.SourceConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


public interface QueryDSL {
  String DEFAULT_SCRIPT_LANG = "painless";

  private static <A> List<A> nonNull(A[] items) {
    return nonNull(Arrays.asList(items));
  }
  private static <A> List<A> nonNull(Collection<A> items) {
    return items.stream().filter(Objects::nonNull).toList();
  }

  private static Map<String, JsonData> jsonParams(Map<String, Object> params) {
    return params.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> json(e.getValue())));
  }

  static Query and(Query... queries) {
    return BoolQuery.of(q -> q.must(nonNull(queries)))._toQuery();
  }

  static Query constantScore(Query query) {
    return ConstantScoreQuery.of(q -> q.filter(query))._toQuery();
  }

  static Query exists(String field) {
    return ExistsQuery.of(q -> q.field(field))._toQuery();
  }

  static <A> Query gt(String field, A gt) {
    return RangeQuery.of(q -> q.field(field).gte(json(gt)))._toQuery();
  }

  static <A> Query gteLte(String field, A gte, A lte) {
    return RangeQuery.of(q -> q.field(field).gte(json(gte)).lte(json(lte)))._toQuery();
  }

  static <A> Query gtLte(String field, A gt, A lte) {
    return RangeQuery.of(q -> q.field(field).gt(json(gt)).lte(json(lte)))._toQuery();
  }

  static Query hasChildQuery(String type, Query query) {
    return HasChildQuery.of(q -> q.query(query).type(type).scoreMode(ChildScoreMode.None))._toQuery();
  }

  static Query ids(List<String> ids) {
    return IdsQuery.of(q -> q.values(nonNull(ids)))._toQuery();
  }

  static Query ids(Collection<String> ids) {
    return IdsQuery.of(q -> q.values(ids.stream().toList()))._toQuery();
  }

  static Query ids(String... ids) {
    return ids(List.of(ids));
  }

  static <C extends Collection<Integer>> Query intTerms(String field, C values) {
    return terms(field, values, FieldValue::of);
  }

  static <A> JsonData json(A value) {
    return JsonData.of(value);
  }

  static <C extends Collection<Long>> Query longTerms(String field, C values) {
    return terms(field, values, FieldValue::of);
  }

  static <A> Query terms(String field, Collection<A> values, Function<A, FieldValue> toFieldValue) {
    final List<FieldValue> fieldValues = values.stream().map(toFieldValue).toList();
    return TermsQuery.of(q -> q
      .field(field)
      .terms(TermsQueryField.of(f -> f.value(fieldValues)))
    )._toQuery();
  }

  static <A> Query lte(String field, A lte) {
    return RangeQuery.of(q -> q.field(field).lte(json(lte)))._toQuery();
  }

  static <A> Query match(String field, A value, Operator operator, Function<A, FieldValue> toFieldValue) {
    return new MatchQuery.Builder().field(field).query(toFieldValue.apply(value)).operator(operator) .build()._toQuery();
  }

  static Query match(String field, String value, Operator operator) {
    return match(field, value, operator, FieldValue::of);
  }

  static Query matchAll() {
    return new MatchAllQuery.Builder().build()._toQuery();
  }

  static Query matchNone() {
    return new MatchNoneQuery.Builder().build()._toQuery();
  }

  static Query not(Query... queries) {
    return BoolQuery.of(q -> q.mustNot(nonNull(queries)))._toQuery();
  }

  static Query or(Query... queries) {
    return BoolQuery.of(q -> q.should(nonNull(queries)))._toQuery();
  }

  static Query prefix(String field, String value) {
    return PrefixQuery.of(q -> q.field(field).value(value))._toQuery();
  }

  static SortOrder reverseOrder(final SortOrder sortOrder) {
    return sortOrder == SortOrder.Asc ? SortOrder.Desc : SortOrder.Asc;
  }

  static Script script(String script, Map<String, Object> params) {
    return scriptFromJsonData(script, jsonParams(params));
  }

  static Script scriptFromJsonData(String script, Map<String, JsonData> params) {
    return new Script.Builder().inline(b -> b
      .source(script)
      .params(params)
      .lang(DEFAULT_SCRIPT_LANG)
    ).build();
  }

  static SortOptions sortOptions(String field, SortOrder sortOrder) {
    return SortOptions.of(so -> so.field(sf -> sf.field(field).order(sortOrder)));
  }

  static SortOptions sortOptions(String field, SortOrder sortOrder, String missing) {
    return SortOptions.of(so -> so.field(sf -> sf.field(field).order(sortOrder).missing(m -> m.stringValue(missing))));
  }

  static SourceConfig sourceInclude(String... fields) {
    return sourceInclude(List.of(fields));
  }

  static SourceConfig sourceExclude(String... fields) {
    return sourceExclude(List.of(fields));
  }

  static SourceConfig sourceExclude(List<String> fields) {
    return SourceConfig.of(s -> s.filter(f -> f.excludes(fields)));
  }

  static SourceConfig sourceInclude(List<String> fields) {
    return SourceConfig.of(s -> s.filter(f -> f.includes(fields)));
  }

  static <C extends Collection<String>> Query stringTerms(String field, C values) {
    return terms(field, values, FieldValue::of);
  }

  static Query term(String field, Integer value) {
    return term(field, value, FieldValue::of);
  }

  static Query term(String field, Long value) {
    return term(field, value, FieldValue::of);
  }

  static Query term(String field, String value) {
    return term(field, value, FieldValue::of);
  }

  static Query term(String field, boolean value) {
    return term(field, value, FieldValue::of);
  }

  static <A> Query term(String field, A value, Function<A, FieldValue> toFieldValue) {
    return TermQuery.of(q -> q.field(field).value(toFieldValue.apply(value)))._toQuery();
  }

  static Query wildcardQuery(String field, String value) {
    return WildcardQuery.of(q -> q.field(field).value(value))._toQuery();
  }
}
