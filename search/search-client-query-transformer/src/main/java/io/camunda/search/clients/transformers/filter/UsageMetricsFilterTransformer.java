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
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import java.util.ArrayList;
import java.util.List;

public class UsageMetricsFilterTransformer implements FilterTransformer<UsageMetricsFilter> {

  private final TasklistMetricIndex tasklistMetricIndex;
  private final MetricIndex operateMetricIndex;
  private UsageMetricsFilter filter;

  public UsageMetricsFilterTransformer(
      final TasklistMetricIndex tasklistMetricIndex, final MetricIndex metricIndex) {
    this.tasklistMetricIndex = tasklistMetricIndex;
    operateMetricIndex = metricIndex;
  }

  @Override
  public SearchQuery toSearchQuery(final UsageMetricsFilter filter) {
    this.filter = filter;
    final var queries = new ArrayList<SearchQuery>();
    queries.add(stringTerms("event", filter.events()));
    queries.addAll(
        dateTimeOperations(
            "eventTime",
            List.of(Operation.gte(filter.startTime()), Operation.lte(filter.endTime()))));
    return and(queries);
  }

  @Override
  public IndexDescriptor getIndex() {
    if (shouldUseTasklistIndex(filter)) {
      return tasklistMetricIndex;
    } else {
      return operateMetricIndex;
    }
  }

  private boolean shouldUseTasklistIndex(final UsageMetricsFilter filter) {
    return filter != null && filter.events().contains("task_completed_by_assignee");
  }
}
