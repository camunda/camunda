/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.UsageMetricsAggregation.AGGREGATION_SUM_EVENT_TYPE;
import static io.camunda.search.aggregation.UsageMetricsAggregation.AGGREGATION_TERMS_EVENT_TYPE;
import static io.camunda.search.aggregation.UsageMetricsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.UsageMetricsAggregation.AGGREGATION_TERMS_TENANT_ID;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.sum;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;

import io.camunda.search.aggregation.UsageMetricsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class UsageMetricsAggregationTransformer
    implements AggregationTransformer<UsageMetricsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<UsageMetricsAggregation, ServiceTransformers> value) {

    final var sumEventTypeAgg =
        sum().name(AGGREGATION_SUM_EVENT_TYPE).field(UsageMetricIndex.EVENT_VALUE).build();

    final var termEventTypeAgg =
        terms()
            .name(AGGREGATION_TERMS_EVENT_TYPE)
            .field(UsageMetricIndex.EVENT_TYPE)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(sumEventTypeAgg)
            .build();

    final var termsTenantIdAggBuilder =
        terms()
            .name(AGGREGATION_TERMS_TENANT_ID)
            .field(UsageMetricIndex.TENANT_ID)
            .size(AGGREGATION_TERMS_SIZE);

    final var filter = value.getLeft().filter();
    return filter.withTenants()
        ? List.of(termsTenantIdAggBuilder.aggregations(termEventTypeAgg).build())
        : List.of(termEventTypeAgg, termsTenantIdAggBuilder.build());
  }
}
