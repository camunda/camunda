/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.google.common.base.Stopwatch;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncDocumentPipeline<DocType, SearchAfterFieldType> {
  private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(AsyncDocumentPipeline.class);

  private static final double BATCH_SIZE_REDUCTION_FACTOR = 0.5;
  private static final List<Class<? extends Throwable>> DEFAULT_RETRYABLE_EXCEPTIONS =
      List.of(
          SocketTimeoutException.class, ElasticsearchException.class, OpenSearchException.class);
  private static final List<Class<? extends Throwable>> DEFAULT_BATCH_REDUCTION_EXCEPTIONS =
      List.of(SocketTimeoutException.class);

  private final BatchSupplier<DocType, SearchAfterFieldType> batchSupplier;
  private final BatchProcessor<DocType, SearchAfterFieldType> batchProcessor;
  private final Executor executor;
  private final Logger logger;
  private final int minBatchSize;
  private final int maxRetryAttempts;
  private final int retryDelayMs;
  private final List<Class<? extends Throwable>> retryableExceptions;
  private final List<Class<? extends Throwable>> batchReductionExceptions;
  private final Runnable retryRecorder;

  private final AtomicReference<DocumentBatch<DocType, SearchAfterFieldType>> lastSearchResponse =
      new AtomicReference<>(null);
  private final AtomicBoolean finished = new AtomicBoolean(false);
  private final AtomicLong totalRead = new AtomicLong(0L);
  private final AtomicLong totalProcessed = new AtomicLong(0L);
  private final AtomicInteger retryCount = new AtomicInteger(0);
  private final AtomicInteger currentBatchSize;
  private final AtomicLong totalTimeTakenMs = new AtomicLong(0);

  private AsyncDocumentPipeline(final Builder<DocType, SearchAfterFieldType> builder) {
    batchSupplier = builder.batchSupplier;
    batchProcessor = builder.batchProcessor;
    executor = builder.executor;
    logger = builder.logger;
    minBatchSize = builder.minBatchSize;
    maxRetryAttempts = builder.maxRetryAttempts;
    retryDelayMs = builder.retryDelayMs;
    retryableExceptions = builder.retryableExceptions;
    batchReductionExceptions = builder.batchReductionExceptions;
    retryRecorder = builder.retryRecorder;
    currentBatchSize = builder.batchSize;
  }

  public CompletableFuture<PipelineStats> execute() {
    return AsyncRepeatUntil.repeatUntil(this::processNextBatch, ignored -> finished.get())
        .thenApply(
            ignore ->
                new PipelineStats(totalRead.get(), totalProcessed.get(), totalTimeTakenMs.get()));
  }

  CompletableFuture<Void> processNextBatch() {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    return batchSupplier
        .supply(getLastSearchPosition(), currentBatchSize.get())
        .thenComposeAsync(
            batch -> {
              if (batch.isEmpty()) {
                finished.set(true);
                return CompletableFuture.completedFuture(null);
              }

              totalRead.accumulateAndGet(batch.documents.size(), Long::sum);

              return processBatch(batch)
                  .thenRun(() -> batchCompleted(batch))
                  .exceptionallyCompose(this::batchFailed);
            },
            executor)
        .whenCompleteAsync(
            (val, err) ->
                totalTimeTakenMs.accumulateAndGet(
                    stopwatch.stop().elapsed(TimeUnit.MILLISECONDS), Long::sum),
            executor);
  }

  private CompletableFuture<Void> processBatch(
      final DocumentBatch<DocType, SearchAfterFieldType> batch) {
    try {
      return batchProcessor.process(batch);
    } catch (final RuntimeException ex) {
      return CompletableFuture.failedFuture(ex);
    }
  }

  private void batchCompleted(final DocumentBatch<DocType, SearchAfterFieldType> batch) {
    totalProcessed.addAndGet(batch.documents.size());

    // advance search position only after batch processed successfully
    // so we can retry the batch if we want
    lastSearchResponse.set(batch);

    retryCount.set(0);
  }

  private CompletableFuture<Void> batchFailed(final Throwable ex) {
    adjustBatchSize(ex);

    if (isRetryableError(ex) && retryCount.incrementAndGet() <= maxRetryAttempts) {

      retryRecorder.run();

      logger.trace(
          "Encountered retryable error when running doc pipeline, "
              + "retrying the batch (attempt {}/{}). Next batch size {}. Error: {}",
          retryCount.get(),
          maxRetryAttempts,
          currentBatchSize.get(),
          ex.getMessage());

      // Whilst this is crude, we exploit the fact the ES/OS visibility is
      // around 2 second, and incrementing delay (default=1000ms) should give a
      // fighting chance to complete in the next attempt. If not will fail and
      // the next retry should take this over the full 2-second refresh interval
      final int retryDelayMs = this.retryDelayMs * retryCount.get();
      return CompletableFuture.supplyAsync(
          () -> null,
          CompletableFuture.delayedExecutor(retryDelayMs, TimeUnit.MILLISECONDS, executor));
    }
    // reset retry count so the next batch starts with fresh retries
    retryCount.set(0);
    // re-throw unexpected exceptions
    throw ex instanceof final RuntimeException re ? re : new RuntimeException(ex);
  }

  private SearchAfterFieldType getLastSearchPosition() {
    final var lstResponse = lastSearchResponse.get();
    return lstResponse == null ? null : lstResponse.searchAfter();
  }

  private void adjustBatchSize(final Throwable ex) {
    if (currentBatchSize.get() <= minBatchSize) {
      return;
    }

    if (shouldReduceBatchSize(ex)) {
      currentBatchSize.set(
          (int) Math.max(minBatchSize, currentBatchSize.get() * BATCH_SIZE_REDUCTION_FACTOR));
    }
  }

  private boolean shouldReduceBatchSize(final Throwable thr) {
    return batchReductionExceptions.stream().anyMatch(clazz -> matchesThrowableOrCause(thr, clazz));
  }

  private boolean isRetryableError(final Throwable thr) {
    return retryableExceptions.stream().anyMatch(clazz -> matchesThrowableOrCause(thr, clazz));
  }

  private boolean matchesThrowableOrCause(
      final Throwable thr, final Class<? extends Throwable> throwableClass) {
    return thr != null
        && (throwableClass.isInstance(thr) || throwableClass.isInstance(thr.getCause()));
  }

  public record DocumentBatch<D, T>(List<D> documents, T searchAfter) {
    public static <D, T> DocumentBatch<D, T> empty() {
      return new DocumentBatch<>(List.of(), null);
    }

    public static <D, T> DocumentBatch<D, T> from(final List<D> documents, final T searchAfter) {
      return new DocumentBatch<>(documents, searchAfter);
    }

    public boolean isEmpty() {
      return documents.isEmpty();
    }
  }

  public static class Builder<DocType, SearchAfterFieldType> {
    private final BatchSupplier<DocType, SearchAfterFieldType> batchSupplier;
    private final BatchProcessor<DocType, SearchAfterFieldType> batchProcessor;
    private Executor executor = ForkJoinPool.commonPool();
    private Logger logger = DEFAULT_LOGGER;
    private AtomicInteger batchSize = new AtomicInteger(2500);
    private int minBatchSize = 50;
    private int maxRetryAttempts = 3;
    private int retryDelayMs = 1_000;
    private final List<Class<? extends Throwable>> retryableExceptions =
        new ArrayList<>(DEFAULT_RETRYABLE_EXCEPTIONS);
    private final List<Class<? extends Throwable>> batchReductionExceptions =
        new ArrayList<>(DEFAULT_BATCH_REDUCTION_EXCEPTIONS);
    private Runnable retryRecorder =
        () -> {
          /* no-op */
        };

    private Builder(
        final BatchSupplier<DocType, SearchAfterFieldType> batchSupplier,
        final BatchProcessor<DocType, SearchAfterFieldType> batchProcessor) {
      this.batchSupplier = batchSupplier;
      this.batchProcessor = batchProcessor;
    }

    public static <DocType, SearchAfterFieldType> Builder<DocType, SearchAfterFieldType> builder(
        final BatchSupplier<DocType, SearchAfterFieldType> batchSupplier,
        final BatchProcessor<DocType, SearchAfterFieldType> batchProcessor) {
      return new Builder<>(batchSupplier, batchProcessor);
    }

    public Builder<DocType, SearchAfterFieldType> executor(final Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder<DocType, SearchAfterFieldType> logger(final Logger logger) {
      this.logger = logger;
      return this;
    }

    public Builder<DocType, SearchAfterFieldType> minBatchSize(final int minBatchSize) {
      this.minBatchSize = minBatchSize;
      return this;
    }

    public Builder<DocType, SearchAfterFieldType> batchSize(final int batchSize) {
      this.batchSize.set(batchSize);
      return this;
    }

    /**
     * Use this if you want to persist changes to batch sizes across different pipeline runs
     *
     * @param batchSize the mutable atomic integer to track batch size with
     * @return the builder
     */
    public Builder<DocType, SearchAfterFieldType> batchSize(final AtomicInteger batchSize) {
      this.batchSize = batchSize;
      return this;
    }

    public Builder<DocType, SearchAfterFieldType> maxRetryAttempts(final int maxRetryAttempts) {
      this.maxRetryAttempts = maxRetryAttempts;
      return this;
    }

    public Builder<DocType, SearchAfterFieldType> retryDelayMs(final int retryDelayMs) {
      this.retryDelayMs = retryDelayMs;
      return this;
    }

    /**
     * Add an exception that what it occurs we will attempt to retry the batch again
     *
     * @param exceptionClass the exception it is ok to retry
     * @return the builder
     */
    public Builder<DocType, SearchAfterFieldType> addRetryableException(
        final Class<? extends Throwable> exceptionClass) {
      retryableExceptions.add(exceptionClass);
      return this;
    }

    /**
     * Add an exception that is used to trigger a reduction in batch size
     *
     * @param exceptionClass the exception that we will use as a signal to reduce the batch size
     * @return the builder
     */
    public Builder<DocType, SearchAfterFieldType> addBatchReductionException(
        final Class<? extends Throwable> exceptionClass) {
      batchReductionExceptions.add(exceptionClass);
      return this;
    }

    public Builder<DocType, SearchAfterFieldType> retryRecorder(final Runnable retryRecorder) {
      this.retryRecorder = retryRecorder;
      return this;
    }

    public CompletableFuture<PipelineStats> buildAndExecute() {
      final var pipeline = new AsyncDocumentPipeline<>(this);
      return pipeline.execute();
    }
  }

  public record PipelineStats(
      long totalDocumentsRead, long totalDocumentsProcessed, long totalTimeTakenMs) {}

  public interface BatchSupplier<DocType, SearchAfterFieldType> {
    CompletableFuture<DocumentBatch<DocType, SearchAfterFieldType>> supply(
        SearchAfterFieldType searchAfter, int batchSize);
  }

  public interface BatchProcessor<DocType, SearchAfterFieldType> {
    CompletableFuture<Void> process(DocumentBatch<DocType, SearchAfterFieldType> batch);
  }
}
