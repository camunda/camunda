/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

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

public interface JobMetricsBatchReader extends SearchClientReader {

  GlobalJobStatisticsEntity getGlobalJobStatistics(
      GlobalJobStatisticsQuery query, ResourceAccessChecks resourceAccessChecks);

  SearchQueryResult<JobTypeStatisticsEntity> getJobTypeStatistics(
      JobTypeStatisticsQuery query, ResourceAccessChecks resourceAccessChecks);

  SearchQueryResult<JobWorkerStatisticsEntity> getJobWorkerStatistics(
      JobWorkerStatisticsQuery query, ResourceAccessChecks resourceAccessChecks);

  SearchQueryResult<JobTimeSeriesStatisticsEntity> getJobTimeSeriesStatistics(
      JobTimeSeriesStatisticsQuery query, ResourceAccessChecks resourceAccessChecks);

  SearchQueryResult<JobErrorStatisticsEntity> getJobErrorStatistics(
      JobErrorStatisticsQuery query, ResourceAccessChecks resourceAccessChecks);
}
