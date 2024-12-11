/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.dateTimeOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UsageMetricsFilter;
import java.util.ArrayList;
import java.util.List;

public class UsageMetricsFilterTransformer implements FilterTransformer<UsageMetricsFilter> {

  @Override
  public SearchQuery toSearchQuery(final UsageMetricsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.add(stringTerms("event", filter.events()));
    queries.addAll(dateTimeOperations("eventTime", Operation.gte(filter.startTime()).values()));
    queries.addAll(dateTimeOperations("eventTime", Operation.lte(filter.endTime()).values()));
    return and(queries);
  }

  @Override
  public List<String> toIndices(final UsageMetricsFilter filter) {
    if (shouldUseTasklistIndex(filter)) {
      return List.of("tasklist-metric-8.3.0_");
    } else {
      return List.of("operate-metric-8.3.0_");
    }
  }

  private boolean shouldUseTasklistIndex(final UsageMetricsFilter filter) {
    return filter.events().contains("task_completed_by_assignee");
  }
}
