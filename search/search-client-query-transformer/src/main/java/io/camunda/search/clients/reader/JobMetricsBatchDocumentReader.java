/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.GlobalJobStatisticsAggregationResult;
import io.camunda.search.aggregation.result.JobTypeStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class JobMetricsBatchDocumentReader extends DocumentBasedReader
    implements JobMetricsBatchReader {

  public JobMetricsBatchDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public GlobalJobStatisticsEntity getGlobalJobStatistics(
      final GlobalJobStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .aggregate(query, GlobalJobStatisticsAggregationResult.class, resourceAccessChecks)
        .entity();
  }

  @Override
  public SearchQueryResult<JobTypeStatisticsEntity> getJobTypeStatistics(
      final JobTypeStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var aggResult =
        getSearchExecutor()
            .aggregate(query, JobTypeStatisticsAggregationResult.class, resourceAccessChecks);
    return new SearchQueryResult<>(
        aggResult.items().size(),
        !aggResult.items().isEmpty(),
        aggResult.items(),
        null,
        aggResult.endCursor());
  }
}
