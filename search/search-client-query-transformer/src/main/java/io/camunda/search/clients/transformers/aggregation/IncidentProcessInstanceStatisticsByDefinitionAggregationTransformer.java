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
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.TENANT_ID;

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
    // Group incidents by processDefinitionKey, then by tenantId; count distinct affected process
    // instances per tenant.
    final var byDefinitionAgg =
        terms()
            .name(AGGREGATION_NAME_BY_DEFINITION)
            .field(PROCESS_DEFINITION_KEY)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(
                terms()
                    .name(AGGREGATION_NAME_BY_TENANT)
                    .field(TENANT_ID)
                    .size(AGGREGATION_TERMS_SIZE)
                    .aggregations(
                        cardinality()
                            .name(AGGREGATION_NAME_AFFECTED_INSTANCES)
                            .field(PROCESS_INSTANCE_KEY)
                            .build())
                    .build(),
                bucketSort()
                    .name(AGGREGATION_NAME_SORT_AND_PAGE)
                    .sorting(toBucketSortSortings(aggregation))
                    .from(aggregation.page() != null ? aggregation.page().from() : null)
                    .size(aggregation.page() != null ? aggregation.page().size() : null)
                    .build())
            .build();

    return List.of(byDefinitionAgg);
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
