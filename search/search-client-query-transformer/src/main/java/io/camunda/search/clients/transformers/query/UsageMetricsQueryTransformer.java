/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.clients.core.RequestBuilders.searchRequest;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.query.UsageMetricsQuery.AGGREGATION_DECISION_INSTANCE;
import static io.camunda.search.query.UsageMetricsQuery.AGGREGATION_PROCESS_INSTANCE;
import static io.camunda.search.query.UsageMetricsQuery.AGGREGATION_USER_TASK_ASSIGNEES;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex;
import java.util.List;

public class UsageMetricsQueryTransformer
    implements ServiceTransformer<UsageMetricsQuery, SearchQueryRequest> {
  private static final String EVENT = MetricIndex.EVENT;
  private static final String EVENT_TASK_COMPLETED_BY_ASSIGNEE = "task_completed_by_assignee";
  private static final String EVENT_PROCESS_INSTANCE_FINISHED = "EVENT_PROCESS_INSTANCE_FINISHED";
  private static final String EVENT_PROCESS_INSTANCE_STARTED = "EVENT_PROCESS_INSTANCE_STARTED";
  private static final String EVENT_DECISION_INSTANCE_EVALUATED =
      "EVENT_DECISION_INSTANCE_EVALUATED";

  private final ServiceTransformers transformers;
  private final TasklistMetricIndex tasklistMetricIndex;

  public UsageMetricsQueryTransformer(
      final ServiceTransformers transformers, final TasklistMetricIndex tasklistMetricIndex) {
    this.transformers = transformers;
    this.tasklistMetricIndex = tasklistMetricIndex;
  }

  @Override
  public SearchQueryRequest apply(final UsageMetricsQuery query) {
    final var filter = query.filter();
    final var searchQueryFilter = toSearchQuery(filter);
    final var indices = toIndices(filter);
    final List<SearchAggregator> aggregations =
        List.of(
            new SearchFilterAggregator(
                AGGREGATION_PROCESS_INSTANCE,
                SearchQueryBuilders.stringTerms(
                    EVENT,
                    List.of(EVENT_PROCESS_INSTANCE_STARTED, EVENT_PROCESS_INSTANCE_FINISHED))),
            new SearchFilterAggregator(
                AGGREGATION_DECISION_INSTANCE,
                SearchQueryBuilders.term(EVENT, EVENT_DECISION_INSTANCE_EVALUATED)),
            new SearchFilterAggregator(
                AGGREGATION_USER_TASK_ASSIGNEES,
                SearchQueryBuilders.term(EVENT, EVENT_TASK_COMPLETED_BY_ASSIGNEE)));

    final var builder =
        searchRequest().index(indices).query(searchQueryFilter).aggregations(aggregations);

    return builder.build();
  }

  private SearchQuery toSearchQuery(final UsageMetricsFilter filter) {
    final var filterTransformer = transformers.getFilterTransformer(filter.getClass());
    final var transformedQuery = filterTransformer.apply(filter);
    return and(transformedQuery);
  }

  private List<String> toIndices(final UsageMetricsFilter filter) {
    final var filterTransformer = transformers.getFilterTransformer(filter.getClass());
    // right now metrics are split across the default metric index and a tasklist specific one
    return List.of(filterTransformer.getIndex().getAlias(), tasklistMetricIndex.getAlias());
  }
}
