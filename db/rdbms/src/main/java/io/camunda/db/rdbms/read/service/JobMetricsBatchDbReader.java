/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static java.util.Optional.ofNullable;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.GlobalJobStatisticsDbQuery;
import io.camunda.db.rdbms.read.domain.JobErrorStatisticsDbQuery;
import io.camunda.db.rdbms.read.domain.JobTimeSeriesStatisticsDbQuery;
import io.camunda.db.rdbms.read.domain.JobTypeStatisticsDbQuery;
import io.camunda.db.rdbms.read.domain.JobWorkerStatisticsDbQuery;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.db.rdbms.sql.columns.JobErrorStatisticsColumn;
import io.camunda.db.rdbms.sql.columns.JobTimeSeriesStatisticsColumn;
import io.camunda.db.rdbms.sql.columns.JobTypeStatisticsColumn;
import io.camunda.db.rdbms.sql.columns.JobWorkerStatisticsColumn;
import io.camunda.search.clients.reader.JobMetricsBatchReader;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import io.camunda.search.entities.JobErrorStatisticsEntity;
import io.camunda.search.entities.JobTimeSeriesStatisticsEntity;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.entities.JobWorkerStatisticsEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobErrorStatisticsQuery;
import io.camunda.search.query.JobTimeSeriesStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.JobWorkerStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.JobErrorStatisticsSort;
import io.camunda.search.sort.JobTimeSeriesStatisticsSort;
import io.camunda.search.sort.JobTypeStatisticsSort;
import io.camunda.search.sort.JobWorkerStatisticsSort;
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

  // Fixed sort: worker asc
  private static final JobWorkerStatisticsSort JOB_WORKER_STATS_FIXED_SORT =
      JobWorkerStatisticsSort.of(b -> b.worker().asc());

  // Fixed sort: time asc
  private static final JobTimeSeriesStatisticsSort JOB_TIME_SERIES_FIXED_SORT =
      JobTimeSeriesStatisticsSort.of(b -> b.time().asc());

  // Fixed sort: errorCode asc, errorMessage asc
  private static final JobErrorStatisticsSort JOB_ERROR_STATS_FIXED_SORT =
      JobErrorStatisticsSort.of(b -> b.errorCode().asc());

  private final JobMetricsBatchMapper jobMetricsBatchMapper;

  private final AbstractEntityReader<JobWorkerStatisticsEntity> workerStatsReader;
  private final AbstractEntityReader<JobTimeSeriesStatisticsEntity> timeSeriesStatsReader;
  private final AbstractEntityReader<JobErrorStatisticsEntity> errorStatsReader;

  public JobMetricsBatchDbReader(
      final JobMetricsBatchMapper jobMetricsBatchMapper, final RdbmsReaderConfig readerConfig) {
    super(JobTypeStatisticsColumn.values(), readerConfig);
    this.jobMetricsBatchMapper = jobMetricsBatchMapper;
    workerStatsReader = new WorkerStatsReader(readerConfig);
    timeSeriesStatsReader = new TimeSeriesStatsReader(readerConfig);
    errorStatsReader = new ErrorStatsReader(readerConfig);
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

  @Override
  public SearchQueryResult<JobWorkerStatisticsEntity> getJobWorkerStatistics(
      final JobWorkerStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Search job worker statistics with {}", query);
    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return EmptyResults.JOB_WORKER_STATISTICS;
    }
    final var dbSort = workerStatsReader.convertSort(JOB_WORKER_STATS_FIXED_SORT);
    final var dbPage =
        workerStatsReader.convertPaging(
            dbSort, Optional.ofNullable(query.page()).orElse(SearchQueryPage.DEFAULT));
    final var dbQuery =
        JobWorkerStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    final var results = jobMetricsBatchMapper.jobWorkerStatistics(dbQuery);
    final var entities =
        results.stream()
            .map(
                result ->
                    new JobWorkerStatisticsEntity(
                        result.worker(),
                        new StatusMetric(
                            ofNullable(result.createdCount()).orElse(0L), result.lastCreatedAt()),
                        new StatusMetric(
                            ofNullable(result.completedCount()).orElse(0L),
                            result.lastCompletedAt()),
                        new StatusMetric(
                            ofNullable(result.failedCount()).orElse(0L), result.lastFailedAt())))
            .toList();

    return workerStatsReader.buildSearchQueryResult(entities.size(), entities, dbSort);
  }

  @Override
  public SearchQueryResult<JobTimeSeriesStatisticsEntity> getJobTimeSeriesStatistics(
      final JobTimeSeriesStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Search job time-series statistics with {}", query);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return EmptyResults.JOB_TIME_SERIES_STATISTICS;
    }
    final var dbSort = timeSeriesStatsReader.convertSort(JOB_TIME_SERIES_FIXED_SORT);
    final var dbPage =
        timeSeriesStatsReader.convertPaging(
            dbSort, Optional.ofNullable(query.page()).orElse(SearchQueryPage.DEFAULT));

    final var dbQuery =
        JobTimeSeriesStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    final var results = jobMetricsBatchMapper.jobTimeSeriesStatistics(dbQuery);
    final var entities =
        results.stream()
            .map(
                result ->
                    new JobTimeSeriesStatisticsEntity(
                        result.bucketTime(),
                        new StatusMetric(
                            ofNullable(result.createdCount()).orElse(0L), result.lastCreatedAt()),
                        new StatusMetric(
                            ofNullable(result.completedCount()).orElse(0L),
                            result.lastCompletedAt()),
                        new StatusMetric(
                            ofNullable(result.failedCount()).orElse(0L), result.lastFailedAt())))
            .toList();

    return timeSeriesStatsReader.buildSearchQueryResult(entities.size(), entities, dbSort);
  }

  @Override
  public SearchQueryResult<JobErrorStatisticsEntity> getJobErrorStatistics(
      final JobErrorStatisticsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Search job error statistics with {}", query);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return EmptyResults.JOB_ERROR_STATISTICS;
    }

    final var dbSort = errorStatsReader.convertSort(JOB_ERROR_STATS_FIXED_SORT);
    final var dbPage =
        errorStatsReader.convertPaging(
            dbSort, Optional.ofNullable(query.page()).orElse(SearchQueryPage.DEFAULT));

    final var dbQuery =
        JobErrorStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    final var results = jobMetricsBatchMapper.jobErrorStatistics(dbQuery);
    final var entities =
        results.stream()
            .map(
                result ->
                    new JobErrorStatisticsEntity(
                        result.errorCode(),
                        result.errorMessage(),
                        ofNullable(result.workers()).orElse(0)))
            .toList();

    return errorStatsReader.buildSearchQueryResult(entities.size(), entities, dbSort);
  }

  /** Inner reader for worker statistics to provide typed AbstractEntityReader methods. */
  private static final class WorkerStatsReader
      extends AbstractEntityReader<JobWorkerStatisticsEntity> {

    WorkerStatsReader(final RdbmsReaderConfig readerConfig) {
      super(JobWorkerStatisticsColumn.values(), readerConfig);
    }
  }

  /** Inner reader for time-series statistics to provide typed AbstractEntityReader methods. */
  private static final class TimeSeriesStatsReader
      extends AbstractEntityReader<JobTimeSeriesStatisticsEntity> {

    TimeSeriesStatsReader(final RdbmsReaderConfig readerConfig) {
      super(JobTimeSeriesStatisticsColumn.values(), readerConfig);
    }
  }

  /** Inner reader for error statistics to provide typed AbstractEntityReader methods. */
  private static final class ErrorStatsReader
      extends AbstractEntityReader<JobErrorStatisticsEntity> {

    ErrorStatsReader(final RdbmsReaderConfig readerConfig) {
      super(JobErrorStatisticsColumn.values(), readerConfig);
    }
  }

  private static final class EmptyResults {
    private static final SearchQueryResult<JobTypeStatisticsEntity> JOB_TYPE_STATISTICS =
        SearchQueryResult.of(f -> f.total(0L).items(Collections.emptyList()).endCursor(""));

    private static final SearchQueryResult<JobWorkerStatisticsEntity> JOB_WORKER_STATISTICS =
        SearchQueryResult.of(f -> f.total(0L).items(Collections.emptyList()).endCursor(""));

    private static final SearchQueryResult<JobTimeSeriesStatisticsEntity>
        JOB_TIME_SERIES_STATISTICS =
            SearchQueryResult.of(f -> f.total(0L).items(Collections.emptyList()).endCursor(""));

    private static final SearchQueryResult<JobErrorStatisticsEntity> JOB_ERROR_STATISTICS =
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
