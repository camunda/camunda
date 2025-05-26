/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;

import io.camunda.search.aggregation.BatchOperationAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import java.util.List;

public class BatchOperationAggregationTransformer
    implements AggregationTransformer<BatchOperationAggregation> {

  @Override
  public List<SearchAggregator> apply(final BatchOperationAggregation value) {

    // aggregate terms flow node id
    final var statusAgg =
        terms()
            .name(BatchOperationAggregation.AGGREGATION_BATCH_OPERATION_ID)
            .field(BatchOperationAggregation.AGGREGATION_BATCH_OPERATION_ID)
            .size(10)
            .build();

    return List.of(statusAgg);
  }
}
