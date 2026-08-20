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
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_COMPOSITE_SIZE;
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
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.composite;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.dateHistogram;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filter;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.max;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.sum;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;

import io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator.DateHistogramInterval;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.Optional;

public class JobTimeSeriesStatisticsAggregationTransformer
    implements AggregationTransformer<JobTimeSeriesStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<JobTimeSeriesStatisticsAggregation, ServiceTransformers> value) {

    final var aggregation = value.getLeft();
    final var filter = aggregation.filter();

    // Created filter bucket with count and lastUpdatedAt sub-aggregations
    final var createdAgg =
        filter()
            .name(AGGREGATION_CREATED)
            .query(matchAll())
            .aggregations(
                sum(AGGREGATION_COUNT, FIELD_CREATED_COUNT),
                max(AGGREGATION_LAST_UPDATED_AT, FIELD_LAST_CREATED_AT))
            .build();

    // Completed filter bucket with count and lastUpdatedAt sub-aggregations
    final var completedAgg =
        filter()
            .name(AGGREGATION_COMPLETED)
            .query(matchAll())
            .aggregations(
                sum(AGGREGATION_COUNT, FIELD_COMPLETED_COUNT),
                max(AGGREGATION_LAST_UPDATED_AT, FIELD_LAST_COMPLETED_AT))
            .build();

    // Failed filter bucket with count and lastUpdatedAt sub-aggregations
    final var failedAgg =
        filter()
            .name(AGGREGATION_FAILED)
            .query(matchAll())
            .aggregations(
                sum(AGGREGATION_COUNT, FIELD_FAILED_COUNT),
                max(AGGREGATION_LAST_UPDATED_AT, FIELD_LAST_FAILED_AT))
            .build();

    // Date histogram source for composite aggregation bucketed by time
    final var byTimeSource =
        dateHistogram()
            .name(AGGREGATION_SOURCE_NAME_TIME)
            .field(FIELD_START_TIME)
            .interval(new DateHistogramInterval.Fixed(filter.resolution()))
            .build();

    // Composite aggregation for pagination support
    final var page = aggregation.page();
    final var byTimeAgg =
        composite()
            .name(AGGREGATION_BY_TIME)
            .size(
                Optional.ofNullable(page)
                    .map(SearchQueryPage::size)
                    .orElse(AGGREGATION_COMPOSITE_SIZE))
            .after(Optional.ofNullable(page).map(SearchQueryPage::after).orElse(null))
            .sources(List.of(byTimeSource))
            .aggregations(createdAgg, completedAgg, failedAgg)
            .build();

    return List.of(byTimeAgg);
  }
}
