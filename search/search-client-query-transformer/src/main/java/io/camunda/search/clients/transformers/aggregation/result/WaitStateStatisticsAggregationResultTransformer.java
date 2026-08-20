/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.WaitStateStatisticsAggregation.AGGREGATION_GROUP_ELEMENTS;

import io.camunda.search.aggregation.result.WaitStateStatisticsAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.WaitStateStatisticsEntity;
import java.util.ArrayList;
import java.util.Map;

public class WaitStateStatisticsAggregationResultTransformer
    implements AggregationResultTransformer<WaitStateStatisticsAggregationResult> {

  @Override
  public WaitStateStatisticsAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    final var items = new ArrayList<WaitStateStatisticsEntity>();
    final var group = aggregations.get(AGGREGATION_GROUP_ELEMENTS);
    if (group != null && group.aggregations() != null) {
      group
          .aggregations()
          .forEach(
              (elementId, bucket) ->
                  items.add(new WaitStateStatisticsEntity(elementId, bucket.docCount())));
    }
    return new WaitStateStatisticsAggregationResult(items);
  }
}
