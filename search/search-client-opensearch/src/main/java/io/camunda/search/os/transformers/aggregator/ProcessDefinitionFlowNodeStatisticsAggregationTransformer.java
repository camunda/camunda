/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import static io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery.*;

import io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.entities.ProcessDefinitionFlowNodeStatisticsEntity;
import org.opensearch.client.opensearch.core.SearchResponse;

public class ProcessDefinitionFlowNodeStatisticsAggregationTransformer
    implements SearchTransfomer<SearchResponse<?>, ProcessDefinitionFlowNodeStatisticsAggregation> {

  @Override
  public ProcessDefinitionFlowNodeStatisticsAggregation apply(final SearchResponse<?> value) {
    final var children = value.aggregations().get(AGGREGATION_TO_FLOW_NODES).children();
    final var filter = children.aggregations().get(AGGREGATION_FILTER_FLOW_NODES).filter();
    final var group = filter.aggregations().get(AGGREGATION_GROUP_FLOW_NODES).sterms();
    final var items =
        group.buckets().array().stream()
            .map(
                bucket -> {
                  final var groupFilters = bucket.aggregations().get(AGGREGATION_GROUP_FILTERS);
                  final var buckets = groupFilters.filters().buckets().keyed();
                  return new ProcessDefinitionFlowNodeStatisticsEntity.Builder()
                      .flowNodeId(bucket.key())
                      .active(buckets.get(AGGREGATION_ACTIVE).docCount())
                      .completed(buckets.get(AGGREGATION_COMPLETED).docCount())
                      .canceled(buckets.get(AGGREGATION_CANCELED).docCount())
                      .incidents(buckets.get(AGGREGATION_INCIDENTS).docCount())
                      .build();
                })
            .toList();
    return new ProcessDefinitionFlowNodeStatisticsAggregation(items);
  }
}
