/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.UsageMetricsTUAggregation.AGGREGATION_TERMS_ASSIGNEE_HASH;
import static io.camunda.search.aggregation.UsageMetricsTUAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.UsageMetricsTUAggregation.AGGREGATION_TERMS_TENANT_ID;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;

import io.camunda.search.aggregation.UsageMetricsTUAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class UsageMetricsTUAggregationTransformer
    implements AggregationTransformer<UsageMetricsTUAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<UsageMetricsTUAggregation, ServiceTransformers> value) {

    final var termsEventTypeAgg =
        terms()
            .name(AGGREGATION_TERMS_ASSIGNEE_HASH)
            .field(UsageMetricTUIndex.ASSIGNEE_HASH)
            .size(AGGREGATION_TERMS_SIZE)
            .build();

    final var termTenantAgg =
        terms()
            .name(AGGREGATION_TERMS_TENANT_ID)
            .field(UsageMetricTUIndex.TENANT_ID)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(termsEventTypeAgg)
            .build();

    final var filter = value.getLeft().filter();
    return filter.withTenants()
        ? List.of(termsEventTypeAgg, termTenantAgg)
        : List.of(termsEventTypeAgg);
  }
}
