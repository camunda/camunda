/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import static io.camunda.search.query.UsageMetricsQuery.AGGREGATION_DECISION_INSTANCE;
import static io.camunda.search.query.UsageMetricsQuery.AGGREGATION_PROCESS_INSTANCE;
import static io.camunda.search.query.UsageMetricsQuery.AGGREGATION_USER_TASK_ASSIGNEES;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.entities.UsageMetricsCount;

public class UsageMetricsResponseTransformer
    implements SearchTransfomer<SearchResponse<?>, UsageMetricsCount> {
  public UsageMetricsResponseTransformer() {}

  @Override
  public UsageMetricsCount apply(final SearchResponse<?> value) {
    final long processInstances =
        value.aggregations().get(AGGREGATION_PROCESS_INSTANCE).filter().docCount();
    final long decisionInstances =
        value.aggregations().get(AGGREGATION_DECISION_INSTANCE).filter().docCount();
    final long assignees =
        value.aggregations().get(AGGREGATION_USER_TASK_ASSIGNEES).filter().docCount();
    return new UsageMetricsCount(assignees, processInstances, decisionInstances);
  }
}
