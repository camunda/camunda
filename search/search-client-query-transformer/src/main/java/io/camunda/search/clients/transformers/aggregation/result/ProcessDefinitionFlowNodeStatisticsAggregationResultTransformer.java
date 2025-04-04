/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_ACTIVE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_CANCELED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FILTERS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_INCIDENTS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_FLOW_NODES;

import io.camunda.search.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.ProcessDefinitionFlowNodeStatisticsEntity;
import java.util.ArrayList;
import java.util.Map;

public class ProcessDefinitionFlowNodeStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<ProcessDefinitionFlowNodeStatisticsAggregationResult> {

  @Override
  public ProcessDefinitionFlowNodeStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    final var children = aggregations.get(AGGREGATION_TO_FLOW_NODES);
    final var filter = children.aggregations().get(AGGREGATION_FILTER_FLOW_NODES);
    final var group = filter.aggregations().get(AGGREGATION_GROUP_FLOW_NODES);
    final var items = new ArrayList<ProcessDefinitionFlowNodeStatisticsEntity>();
    group
        .aggregations()
        .forEach(
            (flowNodeId, terms) -> {
              final var groupFilters = terms.aggregations().get(AGGREGATION_GROUP_FILTERS);
              items.add(
                  new ProcessDefinitionFlowNodeStatisticsEntity.Builder()
                      .flowNodeId(flowNodeId)
                      .active(groupFilters.aggregations().get(AGGREGATION_ACTIVE).docCount())
                      .completed(groupFilters.aggregations().get(AGGREGATION_COMPLETED).docCount())
                      .canceled(groupFilters.aggregations().get(AGGREGATION_CANCELED).docCount())
                      .incidents(groupFilters.aggregations().get(AGGREGATION_INCIDENTS).docCount())
                      .build());
            });
    return new ProcessDefinitionFlowNodeStatisticsAggregationResult(items);
  }
}
