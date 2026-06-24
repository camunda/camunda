/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregation;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator.DateHistogramInterval;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class SearchDateHistogramAggregatorTransformer
    extends AggregatorTransformer<SearchDateHistogramAggregator, Aggregation> {

  public SearchDateHistogramAggregatorTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public Aggregation apply(final SearchDateHistogramAggregator value) {
    final var aggBuilder = new DateHistogramAggregation.Builder().field(value.field());

    switch (value.interval()) {
      case final DateHistogramInterval.Fixed fixed ->
          aggBuilder.fixedInterval(i -> i.time(fixed.toExpression()));
      case final DateHistogramInterval.Calendar cal ->
          aggBuilder.calendarInterval(toEsCalendarInterval(cal));
    }

    final var builder = new Aggregation.Builder().dateHistogram(aggBuilder.build());
    applySubAggregations(builder, value);
    return builder.build();
  }

  private static CalendarInterval toEsCalendarInterval(final DateHistogramInterval.Calendar value) {
    return switch (value) {
      case Second -> CalendarInterval.Second;
      case Minute -> CalendarInterval.Minute;
      case Hour -> CalendarInterval.Hour;
      case Day -> CalendarInterval.Day;
      case Week -> CalendarInterval.Week;
      case Month -> CalendarInterval.Month;
      case Quarter -> CalendarInterval.Quarter;
      case Year -> CalendarInterval.Year;
    };
  }
}
