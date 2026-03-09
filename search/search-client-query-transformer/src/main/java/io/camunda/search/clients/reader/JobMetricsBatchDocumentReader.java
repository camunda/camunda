/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.GlobalJobStatisticsAggregationResult;
import io.camunda.search.aggregation.result.JobTimeSeriesStatisticsAggregationResult;
import io.camunda.search.aggregation.result.JobTypeStatisticsAggregationResult;
import io.camunda.search.aggregation.result.JobWorkerStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.JobErrorStatisticsEntity;
import io.camunda.search.entities.JobTimeSeriesStatisticsEntity;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.entities.JobWorkerStatisticsEntity;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobErrorStatisticsQuery;
import io.camunda.search.query.JobTimeSeriesStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.JobWorkerStatisticsQuery;
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
    return aggregateToResult(query, JobTypeStatisticsAggregationResult.class, resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<JobWorkerStatisticsEntity> getJobWorkerStatistics(
      final JobWorkerStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return aggregateToResult(
        query, JobWorkerStatisticsAggregationResult.class, resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<JobTimeSeriesStatisticsEntity> getJobTimeSeriesStatistics(
      final JobTimeSeriesStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return aggregateToResult(
        query, JobTimeSeriesStatisticsAggregationResult.class, resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<JobErrorStatisticsEntity> getJobErrorStatistics(
      final JobErrorStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("Job error statistics is not yet implemented");
  }
}
