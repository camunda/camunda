/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_BY_ERROR;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_COMPOSITE_SIZE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_SOURCE_NAME_ERROR_CODE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_SOURCE_NAME_ERROR_MESSAGE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_WORKERS;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.FIELD_ERROR_CODE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.FIELD_ERROR_MESSAGE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.FIELD_WORKER;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.cardinality;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.composite;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;

import io.camunda.search.aggregation.JobErrorStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.Optional;

public class JobErrorStatisticsAggregationTransformer
    implements AggregationTransformer<JobErrorStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<JobErrorStatisticsAggregation, ServiceTransformers> value) {

    final var aggregation = value.getLeft();

    // Cardinality sub-aggregation to count distinct workers per (errorCode, errorMessage) bucket
    final var workersAgg = cardinality(AGGREGATION_WORKERS, FIELD_WORKER);

    // Two terms sources to composite-bucket by (errorCode, errorMessage)
    final var byErrorCodeSource =
        terms().name(AGGREGATION_SOURCE_NAME_ERROR_CODE).field(FIELD_ERROR_CODE).build();
    final var byErrorMessageSource =
        terms().name(AGGREGATION_SOURCE_NAME_ERROR_MESSAGE).field(FIELD_ERROR_MESSAGE).build();

    // Composite aggregation for pagination support
    final var page = aggregation.page();
    final var byErrorAgg =
        composite()
            .name(AGGREGATION_BY_ERROR)
            .size(
                Optional.ofNullable(page)
                    .map(SearchQueryPage::size)
                    .orElse(AGGREGATION_COMPOSITE_SIZE))
            .after(Optional.ofNullable(page).map(SearchQueryPage::after).orElse(null))
            .sources(List.of(byErrorCodeSource, byErrorMessageSource))
            .aggregations(workersAgg)
            .build();

    return List.of(byErrorAgg);
  }
}
