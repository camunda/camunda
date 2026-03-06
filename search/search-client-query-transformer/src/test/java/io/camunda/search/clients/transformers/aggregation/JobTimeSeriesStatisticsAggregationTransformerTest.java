/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_BY_TIME;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_SOURCE_NAME_TIME;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.FIELD_COMPLETED_COUNT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.FIELD_CREATED_COUNT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.FIELD_FAILED_COUNT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.FIELD_LAST_COMPLETED_AT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.FIELD_LAST_CREATED_AT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.FIELD_LAST_FAILED_AT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.FIELD_START_TIME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator.DateHistogramInterval;
import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.aggregator.SearchMaxAggregator;
import io.camunda.search.clients.aggregator.SearchSumAggregator;
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobTimeSeriesStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobTimeSeriesStatisticsAggregationTransformerTest {

  private static final OffsetDateTime FROM = OffsetDateTime.parse("2024-07-28T00:00:00Z");
  private static final OffsetDateTime TO = OffsetDateTime.parse("2024-07-29T00:00:00Z");

  private final JobTimeSeriesStatisticsAggregationTransformer transformer =
      new JobTimeSeriesStatisticsAggregationTransformer();

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  /** Builds a filter with the given explicit resolution (may be null for auto-computed). */
  private static JobTimeSeriesStatisticsFilter filter(final Duration resolution) {
    return FilterBuilders.jobTimeSeriesStatistics(
        b -> b.from(FROM).to(TO).jobType("test-job").resolution(resolution));
  }

  @Test
  void shouldBuildExpectedAggregationStructure() {
    // given
    final var page = SearchQueryPage.of(b -> b.size(50).after("cursor123"));
    final var aggregation =
        new JobTimeSeriesStatisticsAggregation(page, filter(Duration.ofMinutes(1)));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregations).hasSize(1);

    assertThat(aggregations.get(0))
        .isInstanceOfSatisfying(
            SearchCompositeAggregator.class,
            compositeAgg -> {
              assertThat(compositeAgg.name()).isEqualTo(AGGREGATION_BY_TIME);
              assertThat(compositeAgg.size()).isEqualTo(50);
              assertThat(compositeAgg.after()).isEqualTo("cursor123");
              assertThat(compositeAgg.sources()).hasSize(1);
              assertThat(compositeAgg.aggregations()).hasSize(3);
            });
  }

  @Test
  void shouldBuildDateHistogramSourceWithCorrectFieldAndInterval() {
    // given
    final var page = SearchQueryPage.of(b -> b.size(100));
    final var aggregation =
        new JobTimeSeriesStatisticsAggregation(page, filter(Duration.ofMinutes(5)));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var compositeAgg = (SearchCompositeAggregator) aggregations.get(0);

    assertThat(compositeAgg.sources()).hasSize(1);
    assertThat(compositeAgg.sources().get(0))
        .isInstanceOfSatisfying(
            SearchDateHistogramAggregator.class,
            dateHistogramSource -> {
              assertThat(dateHistogramSource.name()).isEqualTo(AGGREGATION_SOURCE_NAME_TIME);
              assertThat(dateHistogramSource.field()).isEqualTo(FIELD_START_TIME);
              assertThat(dateHistogramSource.interval())
                  .isEqualTo(new DateHistogramInterval.Fixed(Duration.ofMinutes(5)));
            });
  }

  @Test
  void shouldBuildFilterBucketsForEachStatus() {
    // given
    final var page = SearchQueryPage.of(b -> b.size(100));
    final var aggregation =
        new JobTimeSeriesStatisticsAggregation(page, filter(Duration.ofHours(1)));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var compositeAgg = (SearchCompositeAggregator) aggregations.get(0);
    final var subAggregations = compositeAgg.aggregations();

    assertThat(subAggregations.get(0))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            createdAgg -> {
              assertThat(createdAgg.name()).isEqualTo(AGGREGATION_CREATED);
              assertThat(createdAgg.query().queryOption()).isInstanceOf(SearchMatchAllQuery.class);
              assertSubAggregations(
                  createdAgg.aggregations(), FIELD_CREATED_COUNT, FIELD_LAST_CREATED_AT);
            });

    assertThat(subAggregations.get(1))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            completedAgg -> {
              assertThat(completedAgg.name()).isEqualTo(AGGREGATION_COMPLETED);
              assertThat(completedAgg.query().queryOption())
                  .isInstanceOf(SearchMatchAllQuery.class);
              assertSubAggregations(
                  completedAgg.aggregations(), FIELD_COMPLETED_COUNT, FIELD_LAST_COMPLETED_AT);
            });

    assertThat(subAggregations.get(2))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            failedAgg -> {
              assertThat(failedAgg.name()).isEqualTo(AGGREGATION_FAILED);
              assertThat(failedAgg.query().queryOption()).isInstanceOf(SearchMatchAllQuery.class);
              assertSubAggregations(
                  failedAgg.aggregations(), FIELD_FAILED_COUNT, FIELD_LAST_FAILED_AT);
            });
  }

  @Test
  void shouldBuildSubAggregationsWithCorrectTypes() {
    // given
    final var page = SearchQueryPage.of(b -> b.size(100));
    final var aggregation =
        new JobTimeSeriesStatisticsAggregation(page, filter(Duration.ofMinutes(1)));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var compositeAgg = (SearchCompositeAggregator) aggregations.get(0);

    compositeAgg.aggregations().stream()
        .filter(SearchFilterAggregator.class::isInstance)
        .map(SearchFilterAggregator.class::cast)
        .forEach(
            filterAgg -> {
              assertThat(filterAgg.aggregations()).hasSize(2);

              assertThat(filterAgg.aggregations().get(0))
                  .isInstanceOfSatisfying(
                      SearchSumAggregator.class,
                      sumAgg -> assertThat(sumAgg.name()).isEqualTo(AGGREGATION_COUNT));

              assertThat(filterAgg.aggregations().get(1))
                  .isInstanceOfSatisfying(
                      SearchMaxAggregator.class,
                      maxAgg -> assertThat(maxAgg.name()).isEqualTo(AGGREGATION_LAST_UPDATED_AT));
            });
  }

  @Test
  void shouldUsePageSizeAndCursor() {
    // given
    final var page = SearchQueryPage.of(b -> b.size(42).after("someCursor"));
    final var aggregation =
        new JobTimeSeriesStatisticsAggregation(page, filter(Duration.ofMinutes(1)));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var compositeAgg = (SearchCompositeAggregator) aggregations.get(0);
    assertThat(compositeAgg.size()).isEqualTo(42);
    assertThat(compositeAgg.after()).isEqualTo("someCursor");
  }

  @Test
  void shouldUseDefaultSizeWhenPageIsNull() {
    // given
    final var aggregation =
        new JobTimeSeriesStatisticsAggregation(null, filter(Duration.ofMinutes(1)));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var compositeAgg = (SearchCompositeAggregator) aggregations.get(0);
    assertThat(compositeAgg.size()).isEqualTo(10000);
    assertThat(compositeAgg.after()).isNull();
  }

  @Test
  void shouldAutoComputeResolutionWhenNotProvided() {
    // given — 1 day window, no explicit resolution → (24h / 1000) = ~86s, floored to 1m minimum
    final var aggregation =
        new JobTimeSeriesStatisticsAggregation(SearchQueryPage.of(b -> b.size(100)), filter(null));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then — resolution must be at least the minimum (1 minute)
    final var compositeAgg = (SearchCompositeAggregator) aggregations.get(0);
    final var source = (SearchDateHistogramAggregator) compositeAgg.sources().get(0);
    assertThat(source.interval())
        .isInstanceOfSatisfying(
            DateHistogramInterval.Fixed.class,
            fixed ->
                assertThat(fixed.duration())
                    .isGreaterThanOrEqualTo(JobTimeSeriesStatisticsFilter.MIN_RESOLUTION));
  }

  private void assertSubAggregations(
      final List<SearchAggregator> subAggs,
      final String expectedCountField,
      final String expectedLastUpdatedAtField) {
    assertThat(subAggs).hasSize(2);

    assertThat(subAggs.get(0))
        .isInstanceOfSatisfying(
            SearchSumAggregator.class,
            sumAgg -> {
              assertThat(sumAgg.name()).isEqualTo(AGGREGATION_COUNT);
              assertThat(sumAgg.field()).isEqualTo(expectedCountField);
            });

    assertThat(subAggs.get(1))
        .isInstanceOfSatisfying(
            SearchMaxAggregator.class,
            maxAgg -> {
              assertThat(maxAgg.name()).isEqualTo(AGGREGATION_LAST_UPDATED_AT);
              assertThat(maxAgg.field()).isEqualTo(expectedLastUpdatedAtField);
            });
  }
}
