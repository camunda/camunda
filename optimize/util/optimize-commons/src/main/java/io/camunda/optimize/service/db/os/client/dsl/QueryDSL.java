/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.dsl;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.ScriptField;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.PrefixQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;
import org.opensearch.client.opensearch._types.query_dsl.WildcardQuery;
import org.opensearch.client.opensearch.core.search.SourceConfig;

public interface QueryDSL {

  String DEFAULT_SCRIPT_LANG = "painless";

  private static <A> List<A> nonNull(final A[] items) {
    return nonNull(Arrays.asList(items));
  }

  private static <A> List<A> nonNull(final Collection<A> items) {
    return items.stream().filter(Objects::nonNull).toList();
  }

  private static Map<String, JsonData> jsonParams(final Map<String, Object> params) {
    if (params != null) {
      return params.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> json(e.getValue())));
    } else {
      return Map.of();
    }
  }

  static Query and(final Query... queries) {
    return and(Arrays.asList(queries));
  }

  static Query and(final Collection<Query> queries) {
    return BoolQuery.of(q -> q.must(nonNull(queries))).toQuery();
  }

  static <C extends Collection<Boolean>> Query boolTerms(final String field, final C values) {
    return terms(field, values, FieldValue::of);
  }

  static Query exists(final String field) {
    return ExistsQuery.of(q -> q.field(field)).toQuery();
  }

  static Query filter(final List<Query> queries) {
    return BoolQuery.of(q -> q.filter(nonNull(queries))).toQuery();
  }

  static Query filter(final Query... queries) {
    return filter(Arrays.asList(queries));
  }

  static <A> Query gt(final String field, final A gt) {
    return RangeQuery.of(q -> q.field(field).gt(json(gt))).toQuery();
  }

  static <A> Query gte(final String field, final A gte) {
    return RangeQuery.of(q -> q.field(field).gte(json(gte))).toQuery();
  }

  static <A> Query gteLte(final String field, final A gte, final A lte) {
    return RangeQuery.of(q -> q.field(field).gte(json(gte)).lte(json(lte))).toQuery();
  }

  static <A> Query gtLte(final String field, final A gt, final A lte) {
    return RangeQuery.of(q -> q.field(field).gt(json(gt)).lte(json(lte))).toQuery();
  }

  static Query ids(final List<String> ids) {
    return IdsQuery.of(q -> q.values(nonNull(ids))).toQuery();
  }

  static Query ids(final Collection<String> ids) {
    return IdsQuery.of(q -> q.values(ids.stream().toList())).toQuery();
  }

  static Query ids(final String... ids) {
    return ids(List.of(ids));
  }

  static <C extends Collection<Integer>> Query intTerms(final String field, final C values) {
    return terms(field, values, FieldValue::of);
  }

  static <A> JsonData json(final A value) {
    return JsonData.of(value);
  }

  static <C extends Collection<Long>> Query longTerms(final String field, final C values) {
    return terms(field, values, FieldValue::of);
  }

  static <A> Query terms(
      final String field, final Collection<A> values, final Function<A, FieldValue> toFieldValue) {
    final List<FieldValue> fieldValues = values.stream().map(toFieldValue).toList();
    return TermsQuery.of(q -> q.field(field).terms(TermsQueryField.of(f -> f.value(fieldValues))))
        .toQuery();
  }

  static <A> Query terms(final String field, final Collection<A> values) {
    final Function<A, FieldValue> toFieldValue =
        (x) -> {
          if (x instanceof final String s) {
            return FieldValue.of(s);
          } else if (x instanceof final Integer i) {
            return FieldValue.of(i);
          } else if (x instanceof final Long l) {
            return FieldValue.of(l);
          } else if (x instanceof final Boolean b) {
            return FieldValue.of(b);
          } else {
            throw new OptimizeRuntimeException("Unsupported terms type: " + x.getClass());
          }
        };

    final List<FieldValue> fieldValues = values.stream().map(toFieldValue).toList();
    return TermsQuery.of(q -> q.field(field).terms(TermsQueryField.of(f -> f.value(fieldValues))))
        .toQuery();
  }

  static <A> Query lt(final String field, final A lt) {
    return RangeQuery.of(q -> q.field(field).lte(json(lt))).toQuery();
  }

  static <A> Query lte(final String field, final A lte) {
    return RangeQuery.of(q -> q.field(field).lte(json(lte))).toQuery();
  }

  static <A> Query match(
      final String field,
      final A value,
      final Operator operator,
      final Function<A, FieldValue> toFieldValue) {
    return new MatchQuery.Builder()
        .field(field)
        .query(toFieldValue.apply(value))
        .operator(operator)
        .build()
        .toQuery();
  }

  static Query match(final String field, final String value, final Operator operator) {
    return match(field, value, operator, FieldValue::of);
  }

  static Query match(final String field, final String value) {
    return match(field, value, Operator.And);
  }

  static Query matchAll() {
    return new MatchAllQuery.Builder().build().toQuery();
  }

  static Query nested(final String path, final Query query, final ChildScoreMode scoreMode) {
    return NestedQuery.of(q -> q.path(path).query(query).scoreMode(scoreMode)).toQuery();
  }

  static Query not(final Query... queries) {
    return BoolQuery.of(q -> q.mustNot(nonNull(queries))).toQuery();
  }

  static Query or(final Query... queries) {
    return or(Arrays.asList(queries));
  }

  static Query or(final Collection<Query> queries) {
    return BoolQuery.of(q -> q.should(nonNull(queries))).toQuery();
  }

  static Query or(final String minimumShouldMatch, final Query... queries) {
    final BoolQuery.Builder builder =
        new Builder().should(nonNull(queries)).minimumShouldMatch(minimumShouldMatch);
    return builder.build().toQuery();
  }

  static Query prefix(final String field, final String value) {
    return PrefixQuery.of(q -> q.field(field).value(value)).toQuery();
  }

  static SortOrder reverseOrder(final SortOrder sortOrder) {
    return sortOrder == SortOrder.Asc ? SortOrder.Desc : SortOrder.Asc;
  }

  static SortOrder transformSortOrder(
      final io.camunda.optimize.dto.optimize.query.sorting.SortOrder sortOrder) {
    return sortOrder == sortOrder.ASC ? SortOrder.Asc : SortOrder.Desc;
  }

  static Script script(final String script) {
    return scriptFromJsonData(script, Map.of());
  }

  static Script script(final String script, final Map<String, Object> params) {
    return scriptFromJsonData(script, jsonParams(params));
  }

  static ScriptField scriptField(final Script script) {
    return ScriptField.of(b -> b.script(script));
  }

  static Script scriptFromJsonData(final String script, final Map<String, JsonData> params) {
    return new Script.Builder()
        .inline(b -> b.source(script).params(params).lang(DEFAULT_SCRIPT_LANG))
        .build();
  }

  static SourceConfig sourceInclude(final String... fields) {
    return sourceInclude(List.of(fields));
  }

  static SourceConfig sourceExclude(final String... fields) {
    return sourceExclude(List.of(fields));
  }

  static SourceConfig sourceExcluded() {
    return SourceConfig.of(s -> s.fetch(false));
  }

  static SourceConfig sourceExclude(final List<String> fields) {
    return SourceConfig.of(s -> s.filter(f -> f.excludes(fields)));
  }

  static SourceConfig sourceInclude(final List<String> fields) {
    return SourceConfig.of(s -> s.filter(f -> f.includes(fields)));
  }

  static <C extends Collection<String>> Query stringTerms(final String field, final C values) {
    return terms(field, values, FieldValue::of);
  }

  static Query term(final String field, final Integer value) {
    return term(field, value, FieldValue::of);
  }

  static Query term(final String field, final Long value) {
    return term(field, value, FieldValue::of);
  }

  static Query term(final String field, final String value) {
    return term(field, value, FieldValue::of);
  }

  static Query term(final String field, final boolean value) {
    return term(field, value, FieldValue::of);
  }

  static <A> Query term(
      final String field, final A value, final Function<A, FieldValue> toFieldValue) {
    return TermQuery.of(q -> q.field(field).value(toFieldValue.apply(value))).toQuery();
  }

  static Query wildcardQuery(final String field, final String value) {
    return WildcardQuery.of(q -> q.field(field).value(value)).toQuery();
  }
}
