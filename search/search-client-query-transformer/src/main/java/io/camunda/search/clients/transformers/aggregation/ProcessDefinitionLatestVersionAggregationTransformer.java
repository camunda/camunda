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
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.topHits;

import io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator.Builder;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.List;

public class ProcessDefinitionLatestVersionAggregationTransformer
    implements AggregationTransformer<ProcessDefinitionLatestVersionAggregation> {

  @Override
  public List<SearchAggregator> apply(final ProcessDefinitionLatestVersionAggregation value) {
    final Builder<ProcessEntity> builder = topHits();
    final SearchTopHitsAggregator<ProcessEntity> maxVersionsAgg =
        builder
            .name(AGGREGATION_NAME_LATEST_DEFINITION)
            .field(AGGREGATION_MAX_VERSION)
            .documentClass(ProcessEntity.class)
            .build();
    // aggregate terms by process id
    final var byProcessIdAgg =
        terms()
            .name(AGGREGATION_NAME_BY_PROCESS_ID)
            .field(AGGREGATION_GROUP_BPMN_PROCESS_ID)
            .aggregations(maxVersionsAgg)
            .build();

    return List.of(byProcessIdAgg);
  }
}
