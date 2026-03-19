/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.metrics;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.OpenSearchException;

public class CamundaArchiverMetrics implements AutoCloseable {
  public static final String ARCHIVE_OPERATION_STAGE_TAG_READ = "read";
  public static final String ARCHIVE_OPERATION_STAGE_TAG_COPY = "copy";
  public static final String ARCHIVE_OPERATION_STAGE_TAG_DELETE = "delete";
  private static final String COMPLETION_STATUS_FAILED = "failed";
  private static final String COMPLETION_STATUS_SUCCESS = "success";
  private static final Map<Class<? extends Throwable>, String> HANDLED_ERROR_TAGS =
      Map.of(
          SocketTimeoutException.class, "timeout",
          ElasticsearchException.class, "elasticsearch",
          OpenSearchException.class, "opensearch",
          Throwable.class, "other");

  private final MeterRegistry meterRegistry;
  private final Function<String, String> meterNameWrapper;

  /** time spent on search operations with percentile histogram */
  private final Timer archiverSearchTimer;

  /** time spent on delete operations with percentile histogram */
  private final Timer archiverDeleteTimer;

  /** time spent on reindex operations with percentile histogram */
  private final Timer archiverReindexTimer;

  /** timer for overall archive job by status (success/fail) combination */
  private final Map<String, Timer> archiverJobDurationTimersByKey = new ConcurrentHashMap<>();

  /** distribution summary of batch size of top-level instances chosen per job */
  private final Map<String, DistributionSummary> batchSizeSummaryByKey = new ConcurrentHashMap<>();

  /** counter of top-level instances archived per job */
  private final Map<String, Counter> archivedInstancesCounterByKey = new ConcurrentHashMap<>();

  /**
   * timer for how long each archiving operation take (read/copy/delete) by "job+index" combination
   * (20 indexes) and with operation outcome status (success/failed) and recording percentiles for
   * 50th, 75th, 90th and 99th.
   */
  private final Map<String, Timer> archiveOperationDurationTimersByKey = new ConcurrentHashMap<>();

  /**
   * distribution summary of how many actual documents "processed" during each archive operation //
   * stage (read/copy/delete) by "job+index" combination (20 indexes) and with operation outcome //
   * status (success/failed) and recording SLO's for 100, 500, 1_000, 10_000, 50_000, 100_000, //
   * 500_000, 1_000_000, 1_500_000, 2_000_000 docs.
   */
  private final Map<String, DistributionSummary> archivedDocsCountSummaryByKey =
      new ConcurrentHashMap<>();

  /**
   * Count of errors by type (socket-timeout,elasticsearch,opensearch and other) during each //
   * archive operation stage (read/copy/delete) for given source index
   */
  private final Map<String, Counter> archiveOperationErrorCounterByKey = new ConcurrentHashMap<>();

  public CamundaArchiverMetrics(final MeterRegistry meterRegistry) {
    this(meterRegistry, Function.identity());
  }

  public CamundaArchiverMetrics(
      final MeterRegistry meterRegistry, final Function<String, String> meterNameWrapper) {

    this.meterRegistry = meterRegistry;
    this.meterNameWrapper = meterNameWrapper;

    archiverSearchTimer =
        Timer.builder(meterNameWrapper.apply("archiver.request.duration"))
            .description(
                "Duration of how long it takes to run the search request to resolve completed entities, that need to be archived.")
            .tags("type", "search")
            .publishPercentileHistogram()
            .register(meterRegistry);

    archiverDeleteTimer =
        Timer.builder(meterNameWrapper.apply("archiver.request.duration"))
            .description(
                "Duration of how long it takes to run the delete request to remove completed entities, from old indices.")
            .tags("type", "delete")
            .publishPercentileHistogram()
            .register(meterRegistry);

    archiverReindexTimer =
        Timer.builder(meterNameWrapper.apply("archiver.request.duration"))
            .description(
                "Duration of how long it takes to run the reindex request to copy over to the dated indices, from old indices.")
            .tags("type", "reindex")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  @Override
  public void close() {
    // clean up all registered meters
    meterRegistry.remove(archiverSearchTimer);
    meterRegistry.remove(archiverReindexTimer);
    meterRegistry.remove(archiverDeleteTimer);

    archiverJobDurationTimersByKey.values().forEach(meterRegistry::remove);
    batchSizeSummaryByKey.values().forEach(meterRegistry::remove);
    archivedInstancesCounterByKey.values().forEach(meterRegistry::remove);
    archiveOperationDurationTimersByKey.values().forEach(meterRegistry::remove);
    archivedDocsCountSummaryByKey.values().forEach(meterRegistry::remove);
    archiveOperationErrorCounterByKey.values().forEach(meterRegistry::remove);
  }

  public ArchiverJobMetrics getArchiverJobMetrics(final String jobName) {
    return new ArchiverJobMetrics(jobName, this);
  }

  public void measureArchiverSearch(final Timer.Sample sample) {
    sample.stop(archiverSearchTimer);
  }

  public void measureArchiverReindex(final Sample timer) {
    timer.stop(archiverReindexTimer);
  }

  public void measureArchiverDelete(final Sample timer) {
    timer.stop(archiverDeleteTimer);
  }

  public void measureArchivingSuccessDuration(final String jobName, final Sample timer) {
    timer.stop(getArchiveDurationTimer(jobName, COMPLETION_STATUS_SUCCESS));
  }

  public void measureArchivingFailedDuration(final String jobName, final Sample timer) {
    timer.stop(getArchiveDurationTimer(jobName, COMPLETION_STATUS_FAILED));
  }

  public void measureArchivingBatchSize(final String jobName, final int batchSize) {
    getArchivingBatchSizeSummary(jobName).record(batchSize);
  }

  public void measureArchivedInstanceCount(final String jobName, final int batchSize) {
    getArchivedInstancesCounter(jobName).increment(batchSize);
  }

  public void measureSuccessfulArchiverStageMetrics(
      final String jobName,
      final String sourceIdx,
      final String stage,
      final Sample timer,
      final Long noOfDocs) {
    timer.stop(
        getArchiveOperationDurationTimer(jobName, sourceIdx, stage, COMPLETION_STATUS_SUCCESS));

    if (noOfDocs != null) {
      getArchiveOperationSummary(jobName, sourceIdx, stage, COMPLETION_STATUS_SUCCESS)
          .record(noOfDocs);
    }
  }

  public void measureFailedArchiverStageMetrics(
      final String jobName,
      final String sourceIdx,
      final String stage,
      final Sample timer,
      final Long noOfDocs,
      final Throwable throwable) {
    timer.stop(
        getArchiveOperationDurationTimer(jobName, sourceIdx, stage, COMPLETION_STATUS_FAILED));

    if (noOfDocs != null) {
      getArchiveOperationSummary(jobName, sourceIdx, stage, COMPLETION_STATUS_SUCCESS)
          .record(noOfDocs);
    }

    getArchiveOperationErrorCounter(jobName, sourceIdx, stage, throwable).increment();
  }

  private Timer getArchiveDurationTimer(final String jobName, final String status) {
    return archiverJobDurationTimersByKey.compute(
        getMeterKey(jobName, status),
        (key, existingTimer) -> {
          if (existingTimer == null) {
            return Timer.builder(meterNameWrapper.apply("archiver.job.duration"))
                .description(
                    "Duration of how long it takes for an archiver job to run, from reading a "
                        + "batch of top-level archivable instances to actually archiving and "
                        + "deleting from main index, all in all together.")
                .publishPercentiles()
                .tag("job", jobName)
                .tag("status", status)
                .register(meterRegistry);
          }
          return existingTimer;
        });
  }

  private DistributionSummary getArchivingBatchSizeSummary(final String jobName) {
    return batchSizeSummaryByKey.compute(
        getMeterKey(jobName),
        (key, existing) -> {
          if (existing == null) {
            return DistributionSummary.builder(meterNameWrapper.apply("archiver.job.batch.size"))
                .description(
                    "Summary of the batch size of archivable instances selected for archiving. "
                        + "These are top‑level instances and may fan out into many more archivable "
                        + "documents, depending on the parent–child and dependent index structure.")
                .tag("job", jobName)
                .publishPercentileHistogram()
                .register(meterRegistry);
          }
          return existing;
        });
  }

  private Counter getArchivedInstancesCounter(final String jobName) {
    return archivedInstancesCounterByKey.compute(
        getMeterKey(jobName),
        (key, existing) -> {
          if (existing == null) {
            return Counter.builder(meterNameWrapper.apply("archiver.job.archived.instances"))
                .description("Count of archivable instances (top-level instances) archived.")
                .tag("job", jobName)
                .register(meterRegistry);
          }
          return existing;
        });
  }

  private Timer getArchiveOperationDurationTimer(
      final String jobName, final String sourceIdx, final String stage, final String status) {
    return archiveOperationDurationTimersByKey.compute(
        getMeterKey(jobName, sourceIdx, stage, status),
        (key, existing) -> {
          if (existing == null) {
            return Timer.builder(meterNameWrapper.apply("archiver.operation.duration"))
                .description(
                    "Duration of how long it takes for each archive operation stage (read/copy/delete), "
                        + "measured separately with operation outcome (success/failed), to better "
                        + "understand where time is spent in the archiving process.")
                .publishPercentiles(.5, .75, .90, .99)
                .tag("job", jobName)
                .tag("source_index", sourceIdx)
                .tag("stage", stage)
                .tag("status", status)
                .register(meterRegistry);
          }
          return existing;
        });
  }

  private DistributionSummary getArchiveOperationSummary(
      final String jobName, final String sourceIdx, final String stage, final String status) {
    return archivedDocsCountSummaryByKey.compute(
        getMeterKey(jobName, sourceIdx, stage, status),
        (key, existing) -> {
          if (existing == null) {
            return DistributionSummary.builder(meterNameWrapper.apply("archiver.operation.docs"))
                .description(
                    "Summary of archive docs \"processed\" in each archiving operation "
                        + "stage (read/copy/delete), to understand the distribution actual docs "
                        + "processed per index and the outcome to identify potential impact.")
                .tag("job", jobName)
                .tag("source_index", sourceIdx)
                .tag("stage", stage)
                .tag("status", status)
                .serviceLevelObjectives(
                    100, 500, 1_000, 10_000, 50_000, 100_000, 500_000, 1_000_000, 1_500_000,
                    2_000_000)
                .register(meterRegistry);
          }
          return existing;
        });
  }

  private Counter getArchiveOperationErrorCounter(
      final String jobName, final String sourceIdx, final String stage, final Throwable throwable) {
    final String errorType = extractErrorTag(throwable);
    return archiveOperationErrorCounterByKey.compute(
        getMeterKey(jobName, sourceIdx, stage, errorType),
        (key, existing) -> {
          if (existing == null) {
            return Counter.builder(meterNameWrapper.apply("archiver.operation.error"))
                .description(
                    "Count of errors occurred in during each archiving stage (copy & delete only), "
                        + "to understand the types of errors affecting the archiving process.")
                .tag("job", jobName)
                .tag("source_index", sourceIdx)
                .tag("stage", stage)
                .tag("error_type", errorType)
                .register(meterRegistry);
          }
          return existing;
        });
  }

  private static String getMeterKey(final String... vals) {
    return String.join("-", vals);
  }

  private static String extractErrorTag(final Throwable ex) {
    if (ex != null && ex.getCause() != null) {
      if (ex.getCause() instanceof SocketTimeoutException) {
        return HANDLED_ERROR_TAGS.get(SocketTimeoutException.class);
      } else if (ex.getCause() instanceof ElasticsearchException) {
        return HANDLED_ERROR_TAGS.get(ElasticsearchException.class);
      } else if (ex.getCause() instanceof OpenSearchException) {
        return HANDLED_ERROR_TAGS.get(OpenSearchException.class);
      } else {
        return extractErrorTag(ex.getCause());
      }
    }
    return HANDLED_ERROR_TAGS.get(Throwable.class);
  }
}
