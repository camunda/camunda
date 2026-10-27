/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.WaitStateStatisticsAggregation.AGGREGATION_GROUP_ELEMENTS;
import static io.camunda.search.aggregation.WaitStateStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;

import io.camunda.search.aggregation.WaitStateStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.webapps.schema.descriptors.template.WaitStateTemplate;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class WaitStateStatisticsAggregationTransformer
    implements AggregationTransformer<WaitStateStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<WaitStateStatisticsAggregation, ServiceTransformers> value) {
    return List.of(
        terms()
            .name(AGGREGATION_GROUP_ELEMENTS)
            .field(WaitStateTemplate.ELEMENT_ID)
            .size(AGGREGATION_TERMS_SIZE)
            .build());
  }
}
