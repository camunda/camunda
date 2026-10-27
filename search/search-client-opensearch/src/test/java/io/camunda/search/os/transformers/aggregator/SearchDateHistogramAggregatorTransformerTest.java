/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.aggregator.SearchAggregatorBuilders;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator.DateHistogramInterval;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchDateHistogramAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchDateHistogramAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        // fixed interval via Fixed(Duration)
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram(
                "byTime", "startTime", new DateHistogramInterval.Fixed(Duration.ofMinutes(1))),
            "{'date_histogram':{'field':'startTime','fixed_interval':'1m'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram(
                "byTime", "startTime", new DateHistogramInterval.Fixed(Duration.ofHours(1))),
            "{'date_histogram':{'field':'startTime','fixed_interval':'1h'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram(
                "byTime", "startTime", new DateHistogramInterval.Fixed(Duration.ofMinutes(5))),
            "{'date_histogram':{'field':'startTime','fixed_interval':'5m'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram(
                "byTime", "startTime", new DateHistogramInterval.Fixed(Duration.ofHours(2))),
            "{'date_histogram':{'field':'startTime','fixed_interval':'2h'}}"),
        // calendar interval
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram(
                "byTime", "startTime", DateHistogramInterval.Calendar.Day),
            "{'date_histogram':{'calendar_interval':'day','field':'startTime'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram(
                "byTime", "startTime", DateHistogramInterval.Calendar.Month),
            "{'date_histogram':{'calendar_interval':'month','field':'startTime'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram(
                "byTime", "startTime", DateHistogramInterval.Calendar.Year),
            "{'date_histogram':{'calendar_interval':'year','field':'startTime'}}"),
        // fixed interval with sub-aggregations
        Arguments.arguments(
            SearchAggregatorBuilders.dateHistogram()
                .name("byTime")
                .field("startTime")
                .interval(new DateHistogramInterval.Fixed(Duration.ofMinutes(5)))
                .aggregations(SearchAggregatorBuilders.sum("count", "createdCount"))
                .build(),
            "{'aggregations':{'count':{'sum':{'field':'createdCount'}}},'date_histogram':{'field':'startTime','fixed_interval':'5m'}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    assertThatThrownBy(
            () ->
                SearchAggregatorBuilders.dateHistogram()
                    .field("startTime")
                    .interval(new DateHistogramInterval.Fixed(Duration.ofMinutes(1)))
                    .build())
        .hasMessageContaining("name must not be null")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    assertThatThrownBy(
            () ->
                SearchAggregatorBuilders.dateHistogram()
                    .name("byTime")
                    .interval(new DateHistogramInterval.Fixed(Duration.ofMinutes(1)))
                    .build())
        .hasMessageContaining("field must not be null")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullInterval() {
    assertThatThrownBy(
            () ->
                SearchAggregatorBuilders.dateHistogram().name("byTime").field("startTime").build())
        .hasMessageContaining("interval must not be null")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnSubMillisecondDuration() {
    assertThatThrownBy(() -> new DateHistogramInterval.Fixed(Duration.ofNanos(1)).toExpression())
        .hasMessageContaining("sub-millisecond")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowErrorOnNonPositiveDuration() {
    assertThatThrownBy(() -> new DateHistogramInterval.Fixed(Duration.ZERO))
        .hasMessageContaining("duration must be positive")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DateHistogramInterval.Fixed(Duration.ofMinutes(-1)))
        .hasMessageContaining("duration must be positive")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Override
  protected Class<SearchDateHistogramAggregator> getTransformerClass() {
    return SearchDateHistogramAggregator.class;
  }
}
