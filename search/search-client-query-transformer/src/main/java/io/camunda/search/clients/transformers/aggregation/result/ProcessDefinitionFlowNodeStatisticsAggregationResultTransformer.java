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
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_INCIDENTS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODE_ID;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_CHILDREN_FN;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_PARENT_PI;

import io.camunda.search.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity.Builder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ProcessDefinitionFlowNodeStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<ProcessDefinitionFlowNodeStatisticsAggregationResult> {

  private void processFilter(
      final AggregationResult aggregationResult,
      final Map<String, ProcessFlowNodeStatisticsEntity.Builder> resultMap,
      final BiConsumer<ProcessFlowNodeStatisticsEntity.Builder, Long> resultConsumer) {

    final var group = aggregationResult.aggregations().get(AGGREGATION_GROUP_FLOW_NODE_ID);
    group
        .aggregations()
        .forEach(
            (flowNodeId, result) -> {
              final var parents = result.aggregations().get(AGGREGATION_TO_PARENT_PI);
              final var entity =
                  resultMap.getOrDefault(
                      flowNodeId,
                      new ProcessFlowNodeStatisticsEntity.Builder().flowNodeId(flowNodeId));
              resultConsumer.accept(entity, parents.docCount());
              resultMap.put(flowNodeId, entity);
            });
  }

  @Override
  public ProcessDefinitionFlowNodeStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    final var children = aggregations.get(AGGREGATION_TO_CHILDREN_FN);
    final var filter = children.aggregations().get(AGGREGATION_FILTER_FLOW_NODES);

    final var entitiesMap = new HashMap<String, Builder>();
    processFilter(
        filter.aggregations().get(AGGREGATION_FILTER_ACTIVE), entitiesMap, Builder::active);
    processFilter(
        filter.aggregations().get(AGGREGATION_FILTER_COMPLETED), entitiesMap, Builder::completed);
    processFilter(
        filter.aggregations().get(AGGREGATION_FILTER_CANCELED), entitiesMap, Builder::canceled);
    processFilter(
        filter.aggregations().get(AGGREGATION_FILTER_INCIDENTS), entitiesMap, Builder::incidents);

    return new ProcessDefinitionFlowNodeStatisticsAggregationResult(
        entitiesMap.values().stream().map(Builder::build).toList());
  }
}
