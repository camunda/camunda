/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.*;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.bucketSort;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.cardinality;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_INSTANCE_KEY;

import io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class IncidentProcessInstanceStatisticsByDefinitionAggregationTransformer
    implements AggregationTransformer<IncidentProcessInstanceStatisticsByDefinitionAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<IncidentProcessInstanceStatisticsByDefinitionAggregation, ServiceTransformers>
          value) {

    final var aggregation = value.getLeft();

    // Phase 1: group incidents (already filtered by the query's IncidentFilter, including
    // state=ACTIVE + required errorHashCode; optional errorMessage/tenantId could be added later)
    // by processDefinitionKey + tenantId and count distinct affected process instances.
    final var byDefinitionAgg =
        terms()
            .name(AGGREGATION_NAME_BY_DEFINITION)
            .script(PROCESS_DEFINITION_AND_TENANT_KEY)
            .lang(AGGREGATION_SCRIPT_LANG)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(
                // distinct affected process instances
                cardinality()
                    .name(AGGREGATION_NAME_AFFECTED_INSTANCES)
                    .field(PROCESS_INSTANCE_KEY)
                    .build(),
                // sorting + pagination (handled via bucket_sort)
                bucketSort()
                    .name(AGGREGATION_NAME_SORT_AND_PAGE)
                    .sorting(toBucketSortSortings(aggregation))
                    .from(aggregation.page() != null ? aggregation.page().from() : null)
                    .size(aggregation.page() != null ? aggregation.page().size() : null)
                    .build())
            .build();

    // Estimate total unique (processDefinitionKey + tenantId) combinations for paging UX.
    final var totalEstimateAgg =
        cardinality()
            .name(AGGREGATION_NAME_TOTAL_ESTIMATE)
            .script(PROCESS_DEFINITION_AND_TENANT_KEY)
            .lang(AGGREGATION_SCRIPT_LANG)
            .build();

    return List.of(byDefinitionAgg, totalEstimateAgg);
  }

  private static List<FieldSorting> toBucketSortSortings(
      final IncidentProcessInstanceStatisticsByDefinitionAggregation aggregation) {
    return aggregation.sort().getFieldSortings().stream()
        .map(
            ordering ->
                new FieldSorting(
                    IncidentProcessInstanceStatisticsByDefinitionAggregation.toBucketSortField(
                        ordering.field()),
                    ordering.order()))
        .toList();
  }
}
