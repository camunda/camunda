/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.BatchSupplier;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.PipelineStats;
import io.camunda.zeebe.util.function.TriFunction;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;

public class ArchiveByIdPipeline {
  private static final int MINIMUM_BATCH_SIZE = 50;

  private final HistoryConfiguration config;
  private final TriFunction<String, String, List<IdWithRouting>, CompletableFuture<Integer>>
      reindexer;
  private final BiFunction<String, List<IdWithRouting>, CompletableFuture<Integer>> deleter;
  private final Function<String, CompletableFuture<Void>> setIndexLifeCycle;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;

  public ArchiveByIdPipeline(
      final HistoryConfiguration config,
      final TriFunction<String, String, List<IdWithRouting>, CompletableFuture<Integer>> reindexer,
      final BiFunction<String, List<IdWithRouting>, CompletableFuture<Integer>> deleter,
      final Function<String, CompletableFuture<Void>> setIndexLifeCycle,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    this.config = config;
    this.reindexer = reindexer;
    this.deleter = deleter;
    this.setIndexLifeCycle = setIndexLifeCycle;
    this.metrics = metrics;
    this.logger = logger;
  }

  public CompletableFuture<Void> moveBetweenIndexes(
      final BatchSupplier<IdWithRouting, SearchAfter> idsSupplier,
      final String sourceIdx,
      final String destinationIdx,
      final Executor executor) {
    final var timer = Timer.start();
    final var statsConsumer = new AtomicReference<PipelineStats>();
    return AsyncDocumentPipeline.builder(
            idsSupplier, batch -> move(sourceIdx, destinationIdx, batch))
        .statsConsumer(statsConsumer::set)
        .logger(logger)
        .executor(executor)
        .addRetryableException(BatchCountMismatchException.class)
        .minBatchSize(MINIMUM_BATCH_SIZE)
        .batchSize(config.getReindexBatchSize())
        .maxRetryAttempts(config.getArchiveByIdMaxRetryAttempts())
        .retryDelayMs(config.getArchiveByIdRetryDelayMs())
        .retryRecorder(metrics::recordArchiverBatchRetry)
        .buildAndExecute()
        .thenComposeAsync(
            stats -> {
              // always trigger set life cycle, which checks whether the policy was previously
              // applied and, if so, skips it. If nothing moved and the destination index is
              // not present, we already set .allowNoIndices(true), which prevents it from erroring.
              // However, if nothing moved because we previously moved them, but the call errored
              // at the put policy stage, this will reapply the policy and ensure no index is
              // left without the ILM policy.
              return setIndexLifeCycle.apply(destinationIdx).thenApply(ignore -> stats);
            },
            executor)
        .thenAccept(
            stats -> {
              logger.trace(
                  "Successfully completed archiving {} to the {} index, moved {} docs in {}s",
                  sourceIdx,
                  destinationIdx,
                  stats.totalDocumentsProcessed(),
                  stats.totalTimeTakenMs() / 1000);

              metrics.measureArchiveIndexDuration(
                  sourceIdx, timer, stats.totalDocumentsProcessed());
            })
        .whenComplete(
            (val, err) -> {
              if (err != null) {
                final var stats = statsConsumer.get();
                logger.warn(
                    "Failed archiving {} to the {} index, moved {} docs so far in {}s, error={}",
                    sourceIdx,
                    destinationIdx,
                    stats.totalDocumentsProcessed(),
                    stats.totalTimeTakenMs() / 1000,
                    err.getMessage(),
                    err);
              }
            });
  }

  private CompletableFuture<Integer> move(
      final String sourceIdx,
      final String destinationIdx,
      final DocumentBatch<IdWithRouting, SearchAfter> batch) {
    return reindex(sourceIdx, destinationIdx, batch)
        .thenCompose(reindexedCount -> delete(sourceIdx, batch));
  }

  private CompletableFuture<Integer> reindex(
      final String sourceIdx,
      final String destinationIdx,
      final DocumentBatch<IdWithRouting, SearchAfter> batch) {
    return reindexer
        .apply(sourceIdx, destinationIdx, batch.documents())
        .thenApply(
            reindexCount ->
                validateProcessedCount(
                    sourceIdx, "reindex", reindexCount, batch.documents().size()));
  }

  private CompletableFuture<Integer> delete(
      final String sourceIdx, final DocumentBatch<IdWithRouting, SearchAfter> batch) {
    return deleter
        .apply(sourceIdx, batch.documents())
        .thenApply(
            deleteCount ->
                validateProcessedCount(sourceIdx, "delete", deleteCount, batch.documents().size()));
  }

  private Integer validateProcessedCount(
      final String sourceIdx,
      final String operation,
      final int processedCount,
      final int expectedCount) {
    if (processedCount < expectedCount) {
      throw new BatchCountMismatchException(
          operation,
          String.format(
              "The '%s' operation for a batch when archiving %s processed %d docs, however was "
                  + "expecting %d docs",
              operation, sourceIdx, processedCount, expectedCount));
    } else if (processedCount > expectedCount) {
      logger.warn(
          "The '{}' operation for a batch when archiving {} processed {} docs, however was expecting {} docs",
          operation,
          sourceIdx,
          processedCount,
          expectedCount);
    }
    return processedCount;
  }
}
