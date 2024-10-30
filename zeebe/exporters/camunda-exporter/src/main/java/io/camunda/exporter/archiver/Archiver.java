/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import io.camunda.exporter.config.ExporterConfiguration.ArchiverConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import javax.annotation.WillCloseWhenClosed;
import org.agrona.CloseHelper;
import org.slf4j.Logger;

@SuppressWarnings({"unused", "FieldCanBeLocal"}) // can be removed in the future
public final class Archiver implements CloseableSilently {
  private final int partitionId;
  private final ArchiverRepository repository;
  private final ArchiverConfiguration config;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final ScheduledExecutorService executor;

  @VisibleForTesting
  Archiver(
      final int partitionId,
      final @WillCloseWhenClosed ArchiverRepository repository,
      final ArchiverConfiguration config,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final @WillCloseWhenClosed ScheduledExecutorService executor) {
    this.partitionId = partitionId;
    this.repository = Objects.requireNonNull(repository, "must specify a repository");
    this.config = Objects.requireNonNull(config, "must specify configuration");
    this.metrics = Objects.requireNonNull(metrics, "must specify metrics");
    this.logger = Objects.requireNonNull(logger, "must specify a logger");
    this.executor = Objects.requireNonNull(executor, "must specify an executor");
  }

  @Override
  public void close() {
    // avoid calling executor.close, which will await 1d (!) until termination
    // we also don't need to wait for the jobs to fully finish, as we should be able to handle
    // partial jobs (e.g. node crash/restart)
    executor.shutdownNow();
    // TODO: any closed resources used in the jobs should handle cases where it's been closed
    CloseHelper.close(
        error ->
            logger.warn("Failed to close archiver repository for partition {}", partitionId, error),
        repository);
  }

  public static Archiver create(
      final int partitionId,
      final @WillCloseWhenClosed ArchiverRepository repository,
      final ArchiverConfiguration config,
      final CamundaExporterMetrics metrics,
      final Logger logger) {
    final var threadFactory =
        Thread.ofPlatform()
            .name("exporter-" + partitionId + "-background-", 0)
            .uncaughtExceptionHandler(FatalErrorHandler.uncaughtExceptionHandler(logger))
            .factory();
    final var executor = defaultExecutor(threadFactory);

    return new Archiver(partitionId, repository, config, metrics, logger, executor);
  }

  private static ScheduledThreadPoolExecutor defaultExecutor(final ThreadFactory threadFactory) {
    // TODO: set size to 2 in case we need to do batch operations
    final var executor = new ScheduledThreadPoolExecutor(1, threadFactory);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);

    return executor;
  }
}
