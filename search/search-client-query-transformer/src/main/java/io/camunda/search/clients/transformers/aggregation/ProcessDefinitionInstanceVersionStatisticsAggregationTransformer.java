/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_PAGE;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_PROCESS_DEFINITION_VERSION;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITH_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_VERSION_CARDINALITY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.bucketSort;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.cardinality;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filter;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.topHits;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION;

import io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator.Builder;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class ProcessDefinitionInstanceVersionStatisticsAggregationTransformer
    implements AggregationTransformer<ProcessDefinitionInstanceVersionStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<ProcessDefinitionInstanceVersionStatisticsAggregation, ServiceTransformers>
          value) {
    final var aggregation = value.getLeft();

    final var byProcessDefinitionVersionAggBuilder =
        terms()
            .name(ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_BY_VERSION)
            .field(PROCESS_VERSION)
            .size(AGGREGATION_TERMS_SIZE)
            .sorting(
                List.of(
                    new FieldSorting(AGGREGATION_FIELD_KEY, io.camunda.search.sort.SortOrder.ASC)));

    final Builder<ProcessInstanceForListViewEntity> topHits = topHits();
    final SearchTopHitsAggregator<ProcessInstanceForListViewEntity> processDefinitionAgg =
        topHits
            .name(AGGREGATION_NAME_PROCESS_DEFINITION_VERSION)
            .field(PROCESS_VERSION)
            .size(1)
            .documentClass(ProcessInstanceForListViewEntity.class)
            .build();

    final var totalWithIncidentsAgg =
        filter().name(AGGREGATION_NAME_TOTAL_WITH_INCIDENT).query(term(INCIDENT, true)).build();

    final var totalWithoutIncidentsAgg =
        filter().name(AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT).query(term(INCIDENT, false)).build();

    final var bucketSort =
        bucketSort()
            .name(AGGREGATION_NAME_PAGE)
            .sorting(getCountSuffixSortings(aggregation))
            .from(aggregation.page() != null ? aggregation.page().from() : null)
            .size(aggregation.page() != null ? aggregation.page().size() : null)
            .build();

    final var byProcessDefinitionVersionAgg =
        byProcessDefinitionVersionAggBuilder
            .aggregations(
                processDefinitionAgg, totalWithIncidentsAgg, totalWithoutIncidentsAgg, bucketSort)
            .build();

    final var processDefinitionKeyCardinalityAgg =
        cardinality().name(AGGREGATION_NAME_VERSION_CARDINALITY).field(PROCESS_VERSION).build();

    return List.of(byProcessDefinitionVersionAgg, processDefinitionKeyCardinalityAgg);
  }

  private static List<FieldSorting> getCountSuffixSortings(
      final ProcessDefinitionInstanceVersionStatisticsAggregation aggregation) {
    return aggregation.sort().getFieldSortings().stream()
        .map(ordering -> new FieldSorting(ordering.field() + "._count", ordering.order()))
        .toList();
  }
}
