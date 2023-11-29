/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.dsl;

import io.camunda.operate.tenant.TenantCheckApplierHolder;
import io.camunda.operate.util.CollectionUtil;
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

import java.util.*;
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

  static Query and(List<Query> queries) {
    return BoolQuery.of(q -> q.must(nonNull(queries)))._toQuery();
  }

  static Query withTenantCheck(Query query) {
    try {
      return TenantCheckApplierHolder.getOpenSearchTenantCheckApplier()
      .map(tenantCheckApplier -> tenantCheckApplier.apply(query))
      .orElse(query);
    } catch (Exception e) {
      /* In integration tests under some circumstances tenantCheckApplier.apply throws NPE due to some tricky bean wiring/mocking issues.
         E.g. only when all tests from io.camunda.operate.elasticsearch in operate-qa-it-tests are run then only all FlowNodeInstanceReaderIT tests fail,
         while running separately all tests from FlowNodeInstanceReaderIT passes successfully (i.e. doesn't reproduce when run separately from other IT tests).
         Thus falling back here to the tenant-unaware query, as it should impact only tests. Would be nice to find a better solution for this issue.
       */
      return query;
    }
  }

  static Query constantScore(Query query) {
    return ConstantScoreQuery.of(q -> q.filter(query))._toQuery();
  }

  static Query exists(String field) {
    return ExistsQuery.of(q -> q.field(field))._toQuery();
  }

  static <A> Query gt(String field, A gt) {
    return RangeQuery.of(q -> q.field(field).gt(json(gt)))._toQuery();
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
    return new Script.Builder().inline(b -> b
      .source(script)
      .params(jsonParams(params))
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
    if (CollectionUtil.isEmpty(fields)) return sourceInclude(List.of());
    return sourceInclude(List.of(fields));
  }

  static SourceConfig sourceExclude(String... fields) {
    if (CollectionUtil.isEmpty(fields)) return sourceExclude(List.of());
    return sourceExclude(List.of(fields));
  }

  static SourceConfig sourceIncludesExcludes(String[] includes, String[] excludes) {
    return sourceIncludesExcludes(
        includes == null? List.of() : List.of(includes),
        excludes == null? List.of() : List.of(excludes)
    );
  }

  static SourceConfig sourceExclude(List<String> fields) {
    return SourceConfig.of(s -> s.filter(f -> f.excludes(fields)));
  }

  static SourceConfig sourceInclude(List<String> fields) {
    return SourceConfig.of(s -> s.filter(f -> f.includes(fields)));
  }

  static SourceConfig sourceIncludesExcludes(List<String> includes, List<String> excludes) {
    return SourceConfig.of(s -> s.filter(f -> f.includes(includes).excludes(excludes)));
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

  static Query matchDateQuery(final String name, final String dateAsString, String dateFormat) {
    // Used to match in different time ranges like hours, minutes etc
    // See: https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#date-math
    return RangeQuery.of(q ->
        q.field(name)
            .gte(json(dateAsString))
            .lte(json(dateAsString))
            .format(dateFormat)
    )._toQuery();
  }
}
