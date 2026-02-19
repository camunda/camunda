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
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.JobTypeStatisticsFilter;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import java.util.ArrayList;
import java.util.List;

public class JobTypeStatisticsFilterTransformer
    extends IndexFilterTransformer<JobTypeStatisticsFilter> {

  public JobTypeStatisticsFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final JobTypeStatisticsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();

    if (filter.from() != null) {
      queries.addAll(
          dateTimeOperations(
              JobMetricsBatchTemplate.START_TIME, List.of(Operation.gte(filter.from()))));
    }

    if (filter.to() != null) {
      queries.addAll(
          dateTimeOperations(
              JobMetricsBatchTemplate.END_TIME, List.of(Operation.lte(filter.to()))));
    }

    // Optional jobType operations filter with advanced search capabilities
    if (filter.jobTypeOperations() != null && !filter.jobTypeOperations().isEmpty()) {
      queries.addAll(
          stringOperations(JobMetricsBatchTemplate.JOB_TYPE, filter.jobTypeOperations()));
    }

    return queries.isEmpty() ? matchAll() : and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return matchAll();
  }
}
