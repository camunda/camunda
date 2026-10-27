/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_INCOMPLETE;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.FIELD_COMPLETED_COUNT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.FIELD_CREATED_COUNT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.FIELD_FAILED_COUNT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.FIELD_INCOMPLETE_BATCH;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.FIELD_LAST_COMPLETED_AT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.FIELD_LAST_CREATED_AT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.FIELD_LAST_FAILED_AT;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filter;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.max;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.sum;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;

import io.camunda.search.aggregation.GlobalJobStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class GlobalJobStatisticsAggregationTransformer
    implements AggregationTransformer<GlobalJobStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<GlobalJobStatisticsAggregation, ServiceTransformers> value) {

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

    // Max aggregation to check if any batch was incomplete
    final var incompleteAgg = max(AGGREGATION_INCOMPLETE, FIELD_INCOMPLETE_BATCH);

    return List.of(createdAgg, completedAgg, failedAgg, incompleteAgg);
  }
}
