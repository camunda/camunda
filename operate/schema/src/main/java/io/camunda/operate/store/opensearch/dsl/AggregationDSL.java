/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.dsl;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;
import static java.lang.String.format;

import io.camunda.operate.exceptions.OperateRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.*;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.lang.Nullable;

public interface AggregationDSL {
  static BucketSortAggregation bucketSortAggregation(
      @Nullable Integer size, SortOptions... sortOptions) {
    return BucketSortAggregation.of(a -> a.sort(Arrays.asList(sortOptions)).size(size));
  }

  static CardinalityAggregation cardinalityAggregation(String field) {
    return CardinalityAggregation.of(a -> a.field(field));
  }

  static CardinalityAggregation cardinalityAggregation(String field, int precisionThreshold) {
    return CardinalityAggregation.of(a -> a.field(field).precisionThreshold(precisionThreshold));
  }

  static CalendarInterval calendarIntervalByAlias(String alias) {
    return Arrays.stream(CalendarInterval.values())
        .filter(ci -> Arrays.asList(ci.aliases()).contains(alias))
        .findFirst()
        .orElseThrow(
            () -> {
              final List<String> legalAliases =
                  Arrays.stream(CalendarInterval.values())
                      .flatMap(v -> Arrays.stream(v.aliases()))
                      .sorted()
                      .toList();
              return new OperateRuntimeException(
                  format(
                      "Unknown CalendarInterval alias %s! Legal aliases: %s", alias, legalAliases));
            });
  }

  static DateHistogramAggregation dateHistogramAggregation(
      String field, String calendarIntervalAlias, String format, boolean keyed) {
    return DateHistogramAggregation.of(
        a ->
            a.field(field)
                .calendarInterval(calendarIntervalByAlias(calendarIntervalAlias))
                .format(format)
                .keyed(keyed));
  }

  static FiltersAggregation filtersAggregation(Map<String, Query> queries) {
    return FiltersAggregation.of(a -> a.filters(Buckets.of(b -> b.keyed(queries))));
  }

  static TermsAggregation termAggregation(String field, int size) {
    return TermsAggregation.of(a -> a.field(field).size(size));
  }

  static TermsAggregation termAggregation(String field, int size, Map<String, SortOrder> orderBy) {
    return TermsAggregation.of(a -> a.field(field).size(size).order(orderBy));
  }

  static SumAggregation sumAggregation(String field) {
    return SumAggregation.of(a -> a.field(field));
  }

  static TopHitsAggregation topHitsAggregation(
      List<String> sourceFields, int size, SortOptions... sortOptions) {
    return TopHitsAggregation.of(
        a -> a.source(sourceInclude(sourceFields)).size(size).sort(List.of(sortOptions)));
  }

  static TopHitsAggregation topHitsAggregation(int size, SortOptions... sortOptions) {
    return TopHitsAggregation.of(a -> a.size(size).sort(List.of(sortOptions)));
  }

  static Aggregation withSubaggregations(
      DateHistogramAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.dateHistogram(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      FiltersAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filters(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      ChildrenAggregation childrenAggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.children(childrenAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(Query query, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filter(query).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      TermsAggregation aggregation, Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.terms(aggregation).aggregations(aggregations));
  }

  static ParentAggregation parent(String type) {
    return ParentAggregation.of(p -> p.type(type));
  }

  static ChildrenAggregation children(String type) {
    return ChildrenAggregation.of(c -> c.type(type));
  }
}
