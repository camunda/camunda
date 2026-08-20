/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.VariableNameAggregation.AGGREGATION_NAME_BY_NAME;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;

import io.camunda.search.aggregation.VariableNameAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class VariableNameAggregationTransformer
    implements AggregationTransformer<VariableNameAggregation> {

  private static final String AGGREGATION_FIELD_KEY = "_key";

  @Override
  public List<SearchAggregator> apply(
      final Tuple<VariableNameAggregation, ServiceTransformers> value) {
    final var aggregation = value.getLeft();
    final var limit = aggregation.page() != null ? aggregation.page().size() : null;

    final var byNameAgg =
        terms()
            .name(AGGREGATION_NAME_BY_NAME)
            .field(NAME)
            .size(limit)
            .sorting(List.of(new FieldSorting(AGGREGATION_FIELD_KEY, SortOrder.ASC)))
            .build();

    return List.of(byNameAgg);
  }
}
