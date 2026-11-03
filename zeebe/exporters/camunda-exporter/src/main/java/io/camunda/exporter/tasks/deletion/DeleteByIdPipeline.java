/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.tasks.util.AsyncDocumentPipeline;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.BatchSupplier;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.PipelineStats;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

class DeleteByIdPipeline {
  private final DeleteByIdSupport support;
  private final Logger logger;

  DeleteByIdPipeline(final DeleteByIdSupport support, final Logger logger) {
    this.support = support;
    this.logger = logger;
  }

  CompletableFuture<Void> deleteDocumentsById(
      final String indexName, final FilterByKeysQuery<?> filter, final Executor executor) {
    final var statsConsumer = new AtomicReference<PipelineStats>();
    final BatchSupplier<DocToDelete, SearchAfter> idsSupplier =
        (searchAfter, batchSize) ->
            support.searchDocumentsToDelete(indexName, filter, searchAfter, batchSize);
    return AsyncDocumentPipeline.builder(idsSupplier, support::deleteDocBatch, executor)
        .statsConsumer(statsConsumer::set)
        .logger(logger)
        .addRetryableException(DocumentDeletionException.class)
        // TODO configure batch sizes etc
        .buildAndExecute()
        .thenAccept(
            stats -> {
              logger.trace(
                  "Successfully completed deleting from {} index {} docs in {}s",
                  indexName,
                  stats.totalDocumentsProcessed(),
                  stats.totalTimeTakenMs() / 1000);

              // TODO add metrics
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
}
