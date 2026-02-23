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
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.util.FunctionUtil;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Abstract background task for archiving records from a source index to a destination index.
 *
 * @param <B> the type of the archive batch, containing the records to be archived
 */
public abstract class ArchiverJob<B extends ArchiveBatch> implements BackgroundTask {

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
  abstract CompletableFuture<B> getNextBatch();

  abstract IndexTemplateDescriptor getTemplateDescriptor();

  @Override
  public CompletionStage<Integer> execute() {
    final var timer = Timer.start();

    return getNextBatch()
        .thenComposeAsync(this::archiveBatch, executor)
        // we schedule gathering timer metrics after the archiveBatch future - to correctly
        // measure the time it takes all in all, including searching, reindexing, deletion
        // There is some overhead with the scheduling at the executor, but this
        // should be negligible
        .thenComposeAsync(
            count -> {
              exporterMetrics.measureArchivingDuration(timer);
              return CompletableFuture.completedFuture(count);
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

  protected Executor getExecutor() {
    return executor;
  }

  protected CompletionStage<Integer> archiveBatch(final B batch) {
    if (batch == null || batch.finishDate() == null || batch.isEmpty()) {
      logger.trace("No {}s to archive", getJobName());
      return CompletableFuture.completedFuture(0);
    }

    logger.trace("Following {}s are found for archiving: {}", getJobName(), batch);
    recordArchivingMetric.accept(batch.size());

    return archive(getTemplateDescriptor(), batch)
        // we want to make sure the rescheduling happens after we update the metrics, so we peek
        // instead of creating an additional pipeline on the interim future
        .thenApplyAsync(FunctionUtil.peek(recordArchivedMetric), executor);
  }

  /**
   * Archives the given batch of records. This method moves the documents from the source index to
   * the destination archive index.
   *
   * @param templateDescriptor index template descriptor of the records to archive
   * @param batch the batch of records to archive
   * @return a future that completes when the archiving is finished
   */
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor, final B batch) {
    return archive(templateDescriptor, batch, Map.of());
  }

  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor,
      final B batch,
      final Map<String, String> filters) {
    final var sourceIdxName = templateDescriptor.getFullQualifiedName();
    final var idsMap = createIdsByFieldMap(templateDescriptor, batch);
    final var finishDate = batch.finishDate();
    return archiverRepository
        .moveDocuments(sourceIdxName, sourceIdxName + finishDate, idsMap, filters, executor)
        .thenApplyAsync(ok -> batch.size(), executor);
  }

  /**
   * Creates a map of IDs grouped by their field names for the given batch. This map is used by the
   * repository to identifying the documents to move.
   *
   * @param templateDescriptor the template descriptor defining the ID field
   * @param batch the batch of records to archive
   * @return a map where the key is the field name (e.g. "id" or "processInstanceKey") and the value
   *     is the list of IDs
   */
  protected abstract Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final B batch);
}
