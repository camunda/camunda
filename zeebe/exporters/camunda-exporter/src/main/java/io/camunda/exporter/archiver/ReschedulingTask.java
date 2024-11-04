/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import io.camunda.zeebe.util.ExponentialBackoff;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public final class ReschedulingTask implements Runnable {
  private final ArchiverJob job;
  private final int batchSize;
  private final ScheduledExecutorService executor;
  private final Logger logger;
  private final ExponentialBackoff idleStrategy;
  private final ExponentialBackoff errorStrategy;

  private long delayMs;
  private long errorDelayMs;

  public ReschedulingTask(
      final ArchiverJob job,
      final int batchSize,
      final long delayBetweenRunsMs,
      final ScheduledExecutorService executor,
      final Logger logger) {
    this.job = job;
    this.batchSize = batchSize;
    this.executor = executor;
    this.logger = logger;

    idleStrategy = new ExponentialBackoff(60_000, delayBetweenRunsMs, 1.2, 0);
    errorStrategy = new ExponentialBackoff(10_000, delayBetweenRunsMs, 1.2, 0);
  }

  @Override
  public void run() {
    var batchArchived = job.archiveNextBatch();
    // while we could always expect this to return a non-null result, we don't necessarily want to
    // stop, and more importantly, we want to make it transparent that something went wrong
    if (batchArchived == null) {
      logger.warn(
          "Expected to archive a batch asynchronously, but no result returned for job {}; rescheduling anyway",
          job);
      batchArchived = CompletableFuture.completedFuture(0);
    }

    batchArchived
        .thenApplyAsync(this::onBatchArchived, executor)
        .exceptionallyAsync(this::onArchivingError, executor)
        .thenAcceptAsync(this::rescheduleJob, executor);
  }

  private long onBatchArchived(final int count) {
    errorDelayMs = 0;

    // if we worked on as much as the batch size, then there's probably even more work to
    // be done, so use the minimum delay between runs; otherwise, backoff from the last
    // known delay
    delayMs = count >= batchSize ? idleStrategy.applyAsLong(0) : idleStrategy.applyAsLong(delayMs);
    return delayMs;
  }

  private long onArchivingError(final Throwable error) {
    errorDelayMs = errorStrategy.applyAsLong(errorDelayMs);

    logger.error("Error occurred while archiving data; operation will be retried", error);
    return errorDelayMs;
  }

  private void rescheduleJob(final long delay) {
    logger.trace("Rescheduling archiving job {} in {}ms", job, delay);
    executor.schedule(this, delay, TimeUnit.MILLISECONDS);
  }
}
