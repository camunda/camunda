/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_ACTIVE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_CANCELED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_COMPLETED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_INCIDENTS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FILTERS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODE_ID;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_PARENT_PI;

import io.camunda.search.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import java.util.ArrayList;
import java.util.Map;

public class ProcessDefinitionFlowNodeStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<ProcessDefinitionFlowNodeStatisticsAggregationResult> {

  @Override
  public ProcessDefinitionFlowNodeStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {

    // Get the top-level terms aggregation (grouped by flow node ID)
    final var termsAgg = aggregations.get(AGGREGATION_GROUP_FLOW_NODE_ID);

    final var items = new ArrayList<ProcessFlowNodeStatisticsEntity>();

    // Iterate through each flow node bucket
    termsAgg
        .aggregations()
        .forEach(
            (flowNodeId, termsResult) -> {
              // Get the filters aggregation for this flow node
              final var filtersAgg = termsResult.aggregations().get(AGGREGATION_GROUP_FILTERS);

              // Get each state's filter bucket and extract the parent aggregation doc count
              final var activeBucket = filtersAgg.aggregations().get(AGGREGATION_FILTER_ACTIVE);
              final var completedBucket =
                  filtersAgg.aggregations().get(AGGREGATION_FILTER_COMPLETED);
              final var canceledBucket = filtersAgg.aggregations().get(AGGREGATION_FILTER_CANCELED);
              final var incidentsBucket =
                  filtersAgg.aggregations().get(AGGREGATION_FILTER_INCIDENTS);

              // Build entity with all state counts in one pass
              items.add(
                  new ProcessFlowNodeStatisticsEntity.Builder()
                      .flowNodeId(flowNodeId)
                      .active(activeBucket.aggregations().get(AGGREGATION_TO_PARENT_PI).docCount())
                      .completed(
                          completedBucket.aggregations().get(AGGREGATION_TO_PARENT_PI).docCount())
                      .canceled(
                          canceledBucket.aggregations().get(AGGREGATION_TO_PARENT_PI).docCount())
                      .incidents(
                          incidentsBucket.aggregations().get(AGGREGATION_TO_PARENT_PI).docCount())
                      .build());
            });

    return new ProcessDefinitionFlowNodeStatisticsAggregationResult(items);
  }
}
