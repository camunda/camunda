/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.BatchSupplier;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.PipelineStats;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.exporter.tasks.util.SearchAfter;
import io.camunda.search.schema.config.DeleterConfiguration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

class DeleteByIdPipeline {
  private static final int MINIMUM_BATCH_SIZE = 50;

  private final DeleterConfiguration config;
  private final DeleteByIdSupport support;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;

  DeleteByIdPipeline(
      final DeleterConfiguration config,
      final DeleteByIdSupport support,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    this.config = config;
    this.support = support;
    this.metrics = metrics;
    this.logger = logger;
  }

  CompletableFuture<Void> deleteDocumentsById(
      final String indexName, final FilterByKeysQuery<?> filter, final Executor executor) {
    final var statsConsumer = new AtomicReference<PipelineStats>();
    final BatchSupplier<DocToDelete, SearchAfter> idsSupplier =
        (searchAfter, batchSize) ->
            support.searchDocumentsToDelete(indexName, filter, searchAfter, batchSize);
    return AsyncDocumentPipeline.builder(idsSupplier, this::deleteDocBatch, executor)
        .statsConsumer(statsConsumer::set)
        .logger(logger)
        .minBatchSize(MINIMUM_BATCH_SIZE)
        .batchSize(config.getDocumentBatchSize())
        .addRetryableException(IncompleteDocumentDeletionException.class)
        .maxRetryAttempts(config.getMaxRetryAttempts())
        .retryDelayMs(config.getRetryDelayMs())
        .retryRecorder(metrics::recordDeletionBatchRetry)
        .buildAndExecute()
        .thenAccept(
            stats -> {
              logger.trace(
                  "Successfully completed deleting from {} index {} docs in {}s",
                  indexName,
                  stats.totalDocumentsProcessed(),
                  stats.totalTimeTakenMs() / 1000);
            })
        .whenComplete(
            (val, err) -> {
              if (err != null) {
                final var stats = statsConsumer.get();
                logger.warn(
                    "Failed deleting from {} index, deleted {} docs so far in {}s, error={}",
                    indexName,
                    stats.totalDocumentsProcessed(),
                    stats.totalTimeTakenMs() / 1000,
                    err.getMessage(),
                    err);
              }
            });
  }

  private CompletableFuture<Integer> deleteDocBatch(
      final DocumentBatch<DocToDelete, SearchAfter> batch) {
    return support
        .deleteDocBatch(batch)
        .thenApply(
            deleted -> {
              final var batchSize = batch.documents().size();
              // it's possible something else has already deleted the
              // docs, but we will trigger a retry so we can do the search
              // and try again (just in case)
              if (deleted < batchSize) {
                throw new IncompleteDocumentDeletionException(
                    "Only %d/%d docs were deleted".formatted(deleted, batchSize));
              }
              return deleted;
            });
  }
}
