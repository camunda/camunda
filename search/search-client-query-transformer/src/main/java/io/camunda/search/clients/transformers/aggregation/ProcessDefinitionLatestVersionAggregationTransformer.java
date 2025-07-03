/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_GROUP_BPMN_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_MAX_VERSION;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_NAME_BY_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_NAME_LATEST_DEFINITION;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_SOURCE_NAME_BPMN_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.composite;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.topHits;

import io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator.Builder;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.Optional;

public class ProcessDefinitionLatestVersionAggregationTransformer
    implements AggregationTransformer<ProcessDefinitionLatestVersionAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<ProcessDefinitionLatestVersionAggregation, ServiceTransformers> value) {
    final var aggregation = value.getLeft();
    final var transformers = value.getRight();
    final var page = aggregation.page();
    final var sort = aggregation.sort();
    final Builder<ProcessEntity> topHits = topHits();

    // get the MAX version
    final SearchTopHitsAggregator<ProcessEntity> maxVersionsAgg =
        topHits
            .name(AGGREGATION_NAME_LATEST_DEFINITION)
            .field(AGGREGATION_MAX_VERSION)
            .documentClass(ProcessEntity.class)
            .build();

    // aggregate terms by process id
    final SearchTermsAggregator.Builder byProcessIdAggSourceBuilder =
        terms()
            .name(AGGREGATION_SOURCE_NAME_BPMN_PROCESS_ID)
            .field(AGGREGATION_GROUP_BPMN_PROCESS_ID);
    Optional.ofNullable(sort)
        .map(ProcessDefinitionSort::orderings)
        .map(
            orderings ->
                orderings.stream()
                    .map(
                        fs ->
                            new FieldSorting(
                                transformers
                                    .getFieldSortingTransformer(ProcessDefinitionSort.class)
                                    .apply(fs.field()),
                                fs.order()))
                    .toList())
        .ifPresent(byProcessIdAggSourceBuilder::sorting);

    final var finalAggregation =
        composite()
            .name(AGGREGATION_NAME_BY_PROCESS_ID)
            .size(
                Optional.ofNullable(page).map(SearchQueryPage::size).orElse(AGGREGATION_TERMS_SIZE))
            .after(Optional.ofNullable(page).map(SearchQueryPage::after).orElse(null))
            .sources(List.of(byProcessIdAggSourceBuilder.build()))
            .aggregations(maxVersionsAgg)
            .build();

    return List.of(finalAggregation);
  }
}
