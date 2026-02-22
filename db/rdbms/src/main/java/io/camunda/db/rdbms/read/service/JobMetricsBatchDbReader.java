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
import io.camunda.db.rdbms.read.domain.JobTypeStatisticsDbQuery;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.db.rdbms.sql.columns.JobTypeStatisticsColumn;
import io.camunda.search.clients.reader.JobMetricsBatchReader;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.JobTypeStatisticsSort;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.Collections;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobMetricsBatchDbReader extends AbstractEntityReader<JobTypeStatisticsEntity>
    implements JobMetricsBatchReader {

  private static final Logger LOG = LoggerFactory.getLogger(JobMetricsBatchDbReader.class);

  // Fixed sort: jobType asc
  // we don't allow sorting by other fields for this statistics type
  private static final JobTypeStatisticsSort JOB_TYPE_STATS_FIXED_SORT =
      JobTypeStatisticsSort.of(b -> b.jobType().asc());

  private final JobMetricsBatchMapper jobMetricsBatchMapper;

  public JobMetricsBatchDbReader(final JobMetricsBatchMapper jobMetricsBatchMapper) {
    super(JobTypeStatisticsColumn.values());
    this.jobMetricsBatchMapper = jobMetricsBatchMapper;
  }

  @Override
  public GlobalJobStatisticsEntity getGlobalJobStatistics(
      final GlobalJobStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Aggregate global job statistics with {}", query);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return EmptyResults.GLOBAL_JOB_STATISTICS;
    }

    final var dbQuery =
        GlobalJobStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds()));

    final var result = jobMetricsBatchMapper.globalJobStatistics(dbQuery);

    return new GlobalJobStatisticsEntity(
        new StatusMetric(ofNullable(result.createdCount()).orElse(0L), result.lastCreatedAt()),
        new StatusMetric(ofNullable(result.completedCount()).orElse(0L), result.lastCompletedAt()),
        new StatusMetric(ofNullable(result.failedCount()).orElse(0L), result.lastFailedAt()),
        ofNullable(result.incompleteBatch()).orElse(false));
  }

  @Override
  public SearchQueryResult<JobTypeStatisticsEntity> getJobTypeStatistics(
      final JobTypeStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Search job type statistics with {}", query);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return EmptyResults.JOB_TYPE_STATISTICS;
    }
    final var dbSort = convertSort(JOB_TYPE_STATS_FIXED_SORT);
    final var dbPage =
        convertPaging(dbSort, Optional.ofNullable(query.page()).orElse(SearchQueryPage.DEFAULT));
    final var dbQuery =
        JobTypeStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    final var results = jobMetricsBatchMapper.jobTypeStatistics(dbQuery);
    final var entities =
        results.stream()
            .map(
                result ->
                    new JobTypeStatisticsEntity(
                        result.jobType(),
                        new StatusMetric(
                            ofNullable(result.createdCount()).orElse(0L), result.lastCreatedAt()),
                        new StatusMetric(
                            ofNullable(result.completedCount()).orElse(0L),
                            result.lastCompletedAt()),
                        new StatusMetric(
                            ofNullable(result.failedCount()).orElse(0L), result.lastFailedAt()),
                        ofNullable(result.workers()).orElse(0)))
            .toList();

    return buildSearchQueryResult(
        entities.size(), entities, convertSort(JOB_TYPE_STATS_FIXED_SORT));
  }

  private static final class EmptyResults {
    private static final SearchQueryResult<JobTypeStatisticsEntity> JOB_TYPE_STATISTICS =
        SearchQueryResult.of(f -> f.total(0L).items(Collections.emptyList()).endCursor(""));

    private static final GlobalJobStatisticsEntity GLOBAL_JOB_STATISTICS =
        new GlobalJobStatisticsEntity(
            new StatusMetric(0L, null),
            new StatusMetric(0L, null),
            new StatusMetric(0L, null),
            false);

    private EmptyResults() {
      // Utility class
    }
  }
}
