/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractArchiverJob implements ArchiverJob {

  public static final String DATES_AGG = "datesAgg";
  public static final String INSTANCES_AGG = "instancesAgg";
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractArchiverJob.class);

  protected final ScheduledExecutorService archiverExecutor;

  private final BackoffIdleStrategy idleStrategy;
  private final BackoffIdleStrategy errorStrategy;
  private boolean shutdown = false;
  private final double rolloverBatchSize;
  private final long delayBetweenRuns;

  public AbstractArchiverJob(final ScheduledExecutorService archiverExecutor) {
    this.archiverExecutor = archiverExecutor;
    idleStrategy = new BackoffIdleStrategy(2_000, 1.2f, 60_000);
    errorStrategy = new BackoffIdleStrategy(100, 1.2f, 10_000);

    rolloverBatchSize = 100; // TODO operateProperties.getArchiver().getRolloverBatchSize();
    delayBetweenRuns =
        Duration.ofMinutes(1)
            .toMillis(); // TODO: operateProperties.getArchiver().getDelayBetweenRuns();
  }

  @Override
  public void run() {
    archiveNextBatch()
        .thenApply(
            (count) -> {
              errorStrategy.reset();

              if (count >= rolloverBatchSize) {
                idleStrategy.reset();
              } else {
                idleStrategy.idle();
              }

              final var delay = Math.max(delayBetweenRuns, idleStrategy.idleTime());

              return delay;
            })
        .exceptionally(
            (t) -> {
              LOGGER.error("Error occurred while archiving data. Will be retried.", t);
              errorStrategy.idle();
              final var delay = Math.max(delayBetweenRuns, errorStrategy.idleTime());
              return delay;
            })
        .thenAccept(
            (delay) -> {
              if (!shutdown) {
                archiverExecutor.schedule(this, delay, TimeUnit.MILLISECONDS);
              }
            });
  }

  @Override
  public CompletableFuture<Integer> archiveNextBatch() {
    return getNextBatch().thenCompose(this::archiveBatch);
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }
}
