/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static java.util.Optional.ofNullable;

import io.camunda.db.rdbms.read.domain.GlobalJobStatisticsDbQuery;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.search.clients.reader.JobMetricsBatchReader;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobMetricsBatchDbReader implements JobMetricsBatchReader {

  private static final Logger LOG = LoggerFactory.getLogger(JobMetricsBatchDbReader.class);

  private final JobMetricsBatchMapper jobMetricsBatchMapper;

  public JobMetricsBatchDbReader(final JobMetricsBatchMapper jobMetricsBatchMapper) {
    this.jobMetricsBatchMapper = jobMetricsBatchMapper;
  }

  @Override
  public GlobalJobStatisticsEntity getGlobalJobStatistics(
      final GlobalJobStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Aggregate global job statistics with {}", query);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return emptyGlobalJobStatistics();
    }

    final var dbQuery =
        GlobalJobStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds()));

    final var result = jobMetricsBatchMapper.globalJobStatistics(dbQuery);

    if (result == null) {
      return emptyGlobalJobStatistics();
    }

    return new GlobalJobStatisticsEntity(
        new StatusMetric(ofNullable(result.createdCount()).orElse(0L), result.lastCreatedAt()),
        new StatusMetric(ofNullable(result.completedCount()).orElse(0L), result.lastCompletedAt()),
        new StatusMetric(ofNullable(result.failedCount()).orElse(0L), result.lastFailedAt()),
        ofNullable(result.incompleteBatch()).orElse(false));
  }

  @Override
  public SearchQueryResult<JobTypeStatisticsEntity> getJobTypeStatistics(
      final JobTypeStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  private GlobalJobStatisticsEntity emptyGlobalJobStatistics() {
    return new GlobalJobStatisticsEntity(
        new StatusMetric(0L, null), new StatusMetric(0L, null), new StatusMetric(0L, null), false);
  }

  private boolean shouldReturnEmptyResult(final ResourceAccessChecks resourceAccessChecks) {
    return resourceAccessChecks.tenantCheck().enabled()
        && resourceAccessChecks.getAuthorizedTenantIds().isEmpty();
  }
}
