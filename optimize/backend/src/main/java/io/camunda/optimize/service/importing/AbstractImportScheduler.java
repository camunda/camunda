/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.dto.optimize.SchedulerConfig;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public abstract class AbstractImportScheduler<T extends SchedulerConfig> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractImportScheduler.class);

  protected final List<ImportMediator> importMediators;
  protected final T dataImportSourceDto;
  private volatile ScheduledExecutorService mediatorExecutor;

  public AbstractImportScheduler(
      final List<ImportMediator> importMediators, final T dataImportSourceDto) {
    this.importMediators = importMediators;
    this.dataImportSourceDto = dataImportSourceDto;
  }

  public synchronized void startImportScheduling() {
    LOG.info("Start scheduling import from {}.", dataImportSourceDto);
    if (mediatorExecutor == null || mediatorExecutor.isShutdown()) {
      final int poolSize = Math.max(1, importMediators.size());
      mediatorExecutor =
          Executors.newScheduledThreadPool(
              poolSize,
              r -> {
                final Thread t = new Thread(r, getClass().getSimpleName() + "-importer");
                t.setDaemon(true);
                return t;
              });
      importMediators.forEach(this::submitMediatorRun);
    }
  }

  public synchronized void stopImportScheduling() {
    LOG.info("Stop scheduling import from {}.", dataImportSourceDto);
    if (mediatorExecutor != null) {
      mediatorExecutor.shutdown();
    }
  }

  public void shutdown() {
    LOG.debug("Scheduler for {} will shutdown.", dataImportSourceDto);
    importMediators.forEach(ImportMediator::shutdown);
  }

  public boolean isImporting() {
    return (mediatorExecutor != null && !mediatorExecutor.isShutdown())
        || importMediators.stream().anyMatch(ImportMediator::hasPendingImportJobs);
  }

  public List<ImportMediator> getImportMediators() {
    return importMediators;
  }

  public T getDataImportSourceDto() {
    return dataImportSourceDto;
  }

  private void submitMediatorRun(final ImportMediator mediator) {
    if (mediatorExecutor == null || mediatorExecutor.isShutdown()) {
      return;
    }
    try {
      mediatorExecutor.submit(() -> runMediatorAndReschedule(mediator));
    } catch (final RejectedExecutionException e) {
      LOG.debug(
          "Mediator {} not submitted, executor is shutting down.",
          mediator.getClass().getSimpleName());
    }
  }

  private void runMediatorAndReschedule(final ImportMediator mediator) {
    try {
      mediator.runImport();
    } catch (final Exception e) {
      LOG.error("Was not able to execute import of [{}]", mediator.getClass().getSimpleName(), e);
    }
    rescheduleMediator(mediator);
  }

  private void rescheduleMediator(final ImportMediator mediator) {
    if (mediatorExecutor == null || mediatorExecutor.isShutdown()) {
      return;
    }
    final long delayMs = mediator.getBackoffTimeInMs();
    try {
      if (delayMs <= 0) {
        mediatorExecutor.submit(() -> runMediatorAndReschedule(mediator));
      } else {
        mediatorExecutor.schedule(
            () -> runMediatorAndReschedule(mediator), delayMs, TimeUnit.MILLISECONDS);
      }
    } catch (final RejectedExecutionException e) {
      LOG.debug(
          "Mediator {} not rescheduled, executor is shutting down.",
          mediator.getClass().getSimpleName());
    }
  }
}
