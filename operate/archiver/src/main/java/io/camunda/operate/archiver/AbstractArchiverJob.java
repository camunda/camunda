/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.BackoffIdleStrategy;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class AbstractArchiverJob implements ArchiverJob {

  public static final String DATES_AGG = "datesAgg";
  public static final String INSTANCES_AGG = "instancesAgg";
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractArchiverJob.class);

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  private final BackoffIdleStrategy idleStrategy;
  private final BackoffIdleStrategy errorStrategy;
  private boolean shutdown = false;
  @Autowired private OperateProperties operateProperties;

  public AbstractArchiverJob() {
    idleStrategy = new BackoffIdleStrategy(2_000, 1.2f, 60_000);
    errorStrategy = new BackoffIdleStrategy(100, 1.2f, 10_000);
  }

  @Override
  public void run() {

    archiveNextBatch()
        .thenApply(
            (count) -> {
              errorStrategy.reset();

              if (count >= operateProperties.getArchiver().getRolloverBatchSize()) {
                idleStrategy.reset();
              } else {
                idleStrategy.idle();
              }

              final var delay =
                  Math.max(
                      operateProperties.getArchiver().getDelayBetweenRuns(),
                      idleStrategy.idleTime());

              return delay;
            })
        .exceptionally(
            (t) -> {
              LOGGER.error("Error occurred while archiving data. Will be retried.", t);
              errorStrategy.idle();
              final var delay =
                  Math.max(
                      operateProperties.getArchiver().getDelayBetweenRuns(),
                      errorStrategy.idleTime());
              return delay;
            })
        .thenAccept(
            (delay) -> {
              if (!shutdown) {
                archiverExecutor.schedule(this, Date.from(Instant.now().plusMillis(delay)));
              }
            });
  }

  @Override
  public CompletableFuture<Integer> archiveNextBatch() {
    return getNextBatch().thenCompose(this::archiveBatch);
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
  }
}
