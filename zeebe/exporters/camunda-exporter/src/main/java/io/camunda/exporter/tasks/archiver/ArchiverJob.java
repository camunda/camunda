/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.zeebe.util.FunctionUtil;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

public abstract class ArchiverJob implements BackgroundTask {

  private final ArchiverRepository archiverRepository;
  private final CamundaExporterMetrics exporterMetrics;
  private final Logger logger;
  private final Executor executor;

  private final Consumer<Integer> recordArchivingMetric;
  private final Consumer<Integer> recordArchivedMetric;

  public ArchiverJob(
      final ArchiverRepository archiverRepository,
      final CamundaExporterMetrics exporterMetrics,
      final Logger logger,
      final Executor executor,
      final Consumer<Integer> recordArchivingMetric,
      final Consumer<Integer> recordArchivedMetric) {
    this.archiverRepository = archiverRepository;
    this.exporterMetrics = exporterMetrics;
    this.logger = logger;
    this.executor = executor;
    this.recordArchivingMetric = recordArchivingMetric;
    this.recordArchivedMetric = recordArchivedMetric;
  }

  /**
   * Identifier of the archive job, used primarily in logging
   *
   * @return the job name
   */
  abstract String getJobName();

  /**
   * Fetches the next batch of records to be archived
   *
   * @return a future completed with the next batch to be archived, or null/empty if there is
   *     nothing to archive
   */
  abstract CompletableFuture<ArchiveBatch> getNextBatch();

  /**
   * The source index name from which to archive records
   *
   * @return the source index name
   */
  abstract String getSourceIndexName();

  /**
   * The name of the field representing the unique identifier of a record in the source index
   *
   * @return the id field name
   */
  abstract String getIdFieldName();

  @Override
  public CompletionStage<Integer> execute() {
    final var timer = Timer.start();

    return getNextBatch()
        .thenComposeAsync(this::archiveBatch, executor)
        // we schedule gathering timer metrics after the archiveBatch future - to correctly
        // measure the time it takes all in all, including searching, reindexing, deletion
        // There is some overhead with the scheduling at the executor, but this
        // should be negligible
        .whenCompleteAsync(
            (count, error) -> {
              exporterMetrics.measureArchivingDuration(timer);
            },
            executor);
  }

  @Override
  public String toString() {
    return String.format("%s archiver job", getJobName());
  }

  protected ArchiverRepository getArchiverRepository() {
    return archiverRepository;
  }

  protected CompletionStage<Integer> archiveBatch(final ArchiveBatch batch) {
    if (batch == null || batch.ids() == null || batch.ids().isEmpty()) {
      logger.trace("No {}s to archive", getJobName());
      return CompletableFuture.completedFuture(0);
    }

    logger.trace("Following {}s are found for archiving: {}", getJobName(), batch);
    recordArchivingMetric.accept(batch.ids().size());

    return archive(getSourceIndexName(), batch.finishDate(), getIdFieldName(), batch.ids())
        // we want to make sure the rescheduling happens after we update the metrics, so we peek
        // instead of creating an additional pipeline on the interim future
        .thenApplyAsync(FunctionUtil.peek(recordArchivedMetric), executor);
  }

  protected CompletableFuture<Integer> archive(
      final String sourceIdx,
      final String finishDate,
      final String idFieldName,
      final List<String> ids) {
    return archiverRepository
        .moveDocuments(sourceIdx, sourceIdx + finishDate, idFieldName, ids, executor)
        .thenApplyAsync(ok -> ids.size(), executor);
  }
}
