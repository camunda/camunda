/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.externalcode.client.dsl;

import static java.lang.String.format;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.BucketSortAggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;
import org.opensearch.client.opensearch._types.aggregations.CardinalityAggregation;
import org.opensearch.client.opensearch._types.aggregations.ChildrenAggregation;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramAggregation;
import org.opensearch.client.opensearch._types.aggregations.FiltersAggregation;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.ParentAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregation;
import org.opensearch.client.opensearch._types.aggregations.ValueCountAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.lang.Nullable;

public interface AggregationDSL {
  static BucketSortAggregation bucketSortAggregation(
      @Nullable final Integer size, final SortOptions... sortOptions) {
    return BucketSortAggregation.of(a -> a.sort(Arrays.asList(sortOptions)).size(size));
  }

  static CardinalityAggregation cardinalityAggregation(final String field) {
    return CardinalityAggregation.of(a -> a.field(field));
  }

  static CardinalityAggregation cardinalityAggregation(
      final String field, final int precisionThreshold) {
    return CardinalityAggregation.of(a -> a.field(field).precisionThreshold(precisionThreshold));
  }

  static ValueCountAggregation valueCountAggregation(final String field) {
    return ValueCountAggregation.of(a -> a.field(field));
  }

  static CalendarInterval calendarIntervalByAlias(final String alias) {
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
              return new OptimizeRuntimeException(
                  format(
                      "Unknown CalendarInterval alias %s! Legal aliases: %s", alias, legalAliases));
            });
  }

  static DateHistogramAggregation dateHistogramAggregation(
      final String field,
      final String calendarIntervalAlias,
      final String format,
      final boolean keyed) {
    return DateHistogramAggregation.of(
        a ->
            a.field(field)
                .calendarInterval(calendarIntervalByAlias(calendarIntervalAlias))
                .format(format)
                .keyed(keyed));
  }

  static FiltersAggregation filtersAggregation(final Map<String, Query> queries) {
    return FiltersAggregation.of(a -> a.filters(Buckets.of(b -> b.keyed(queries))));
  }

  static TermsAggregation termAggregation(final String field, final int size) {
    return TermsAggregation.of(a -> a.field(field).size(size));
  }

  static TermsAggregation termAggregation(
      final String field, final int size, final Map<String, SortOrder> orderBy) {
    return TermsAggregation.of(a -> a.field(field).size(size).order(orderBy));
  }

  static TopHitsAggregation topHitsAggregation(
      final List<String> sourceFields, final int size, final SortOptions... sortOptions) {
    return TopHitsAggregation.of(
        a -> a.source(QueryDSL.sourceInclude(sourceFields)).size(size).sort(List.of(sortOptions)));
  }

  static TopHitsAggregation topHitsAggregation(final int size, final SortOptions... sortOptions) {
    return TopHitsAggregation.of(a -> a.size(size).sort(List.of(sortOptions)));
  }

  static Aggregation withSubaggregations(
      final DateHistogramAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.dateHistogram(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final NestedAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.nested(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final FiltersAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filters(aggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final ChildrenAggregation childrenAggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.children(childrenAggregation).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final Query query, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.filter(query).aggregations(aggregations));
  }

  static Aggregation withSubaggregations(
      final TermsAggregation aggregation, final Map<String, Aggregation> aggregations) {
    return Aggregation.of(a -> a.terms(aggregation).aggregations(aggregations));
  }

  static ParentAggregation parent(final String type) {
    return ParentAggregation.of(p -> p.type(type));
  }

  static ChildrenAggregation children(final String type) {
    return ChildrenAggregation.of(c -> c.type(type));
  }
}
