/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_ERROR_MESSAGE_SCRIPT;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_AFFECTED_INSTANCES;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_BY_ERROR;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_ERROR_HASH;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_SORT_AND_PAGE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_TOTAL_ESTIMATE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_SCRIPT_LANG;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_TOTAL_ESTIMATE_SCRIPT;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.bucketSort;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.cardinality;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_MSG_HASH;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_INSTANCE_KEY;

import io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.Optional;

public class IncidentProcessInstanceStatisticsByErrorAggregationTransformer
    implements AggregationTransformer<IncidentProcessInstanceStatisticsByErrorAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<IncidentProcessInstanceStatisticsByErrorAggregation, ServiceTransformers> value) {

    final var aggregation = value.getLeft();

    // Bucket by error message (hash collisions split into separate buckets)
    final var byErrorAgg =
        terms()
            .name(AGGREGATION_NAME_BY_ERROR)
            .size(
                Optional.ofNullable(aggregation.page())
                    .map(SearchQueryPage::size)
                    .orElse(AGGREGATION_TERMS_SIZE))
            .script(AGGREGATION_ERROR_MESSAGE_SCRIPT)
            .lang(AGGREGATION_SCRIPT_LANG)
            .aggregations(
                // read error hash per error-message bucket
                terms().name(AGGREGATION_NAME_ERROR_HASH).field(ERROR_MSG_HASH).size(1).build(),
                // count distinct affected process instances
                cardinality()
                    .name(AGGREGATION_NAME_AFFECTED_INSTANCES)
                    .field(PROCESS_INSTANCE_KEY)
                    .build(),
                // sorting + pagination
                bucketSort()
                    .name(AGGREGATION_NAME_SORT_AND_PAGE)
                    .sorting(toBucketSortSortings(aggregation))
                    .from(aggregation.page() != null ? aggregation.page().from() : null)
                    .size(aggregation.page() != null ? aggregation.page().size() : null)
                    .build())
            .build();

    // Estimate total unique error entries (hash + message)
    final var totalEstimateAgg =
        cardinality()
            .name(AGGREGATION_NAME_TOTAL_ESTIMATE)
            .script(AGGREGATION_TOTAL_ESTIMATE_SCRIPT)
            .lang(AGGREGATION_SCRIPT_LANG)
            .build();

    return List.of(byErrorAgg, totalEstimateAgg);
  }

  private static List<FieldSorting> toBucketSortSortings(
      final IncidentProcessInstanceStatisticsByErrorAggregation aggregation) {
    return aggregation.sort().getFieldSortings().stream()
        .map(
            ordering ->
                new FieldSorting(
                    IncidentProcessInstanceStatisticsByErrorAggregation.toBucketSortField(
                        ordering.field()),
                    ordering.order()))
        .toList();
  }
}
