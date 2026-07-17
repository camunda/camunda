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
<<<<<<< HEAD
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractImportScheduler<T extends SchedulerConfig>
    extends AbstractScheduledService {

  protected final List<ImportMediator> importMediators;
  @Getter protected final T dataImportSourceDto;
  protected boolean isImporting = false;

  @Override
  public void run() {
    if (isScheduledToRun()) {
      log.debug("Next round!");
      try {
        runImportRound();
      } catch (Exception e) {
        log.error("Could not schedule next import round!", e);
      }
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ZERO);
  }

  public synchronized void startImportScheduling() {
    log.info("Start scheduling import from {}.", dataImportSourceDto);
    this.isImporting = true;
    startScheduling();
  }

  public synchronized void stopImportScheduling() {
    log.info("Stop scheduling import from {}.", dataImportSourceDto);
    this.isImporting = false;
    stopScheduling();
  }

  public void shutdown() {
    log.debug("Scheduler for {} will shutdown.", dataImportSourceDto);
    getImportMediators().forEach(ImportMediator::shutdown);
  }

  public Future<Void> runImportRound() {
    return runImportRound(false);
  }

  public Future<Void> runImportRound(final boolean forceImport) {
    List<ImportMediator> currentImportRound =
        importMediators.stream()
            .filter(mediator -> forceImport || mediator.canImport())
            .collect(Collectors.toList());
    if (nothingToBeImported(currentImportRound)) {
      this.isImporting = false;
      if (!forceImport) {
        doBackoff();
      }
      return CompletableFuture.completedFuture(null);
    } else {
      this.isImporting = true;
      return executeImportRound(currentImportRound);
    }
  }

  public Future<Void> executeImportRound(List<ImportMediator> currentImportRound) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Scheduling import round for {}",
          currentImportRound.stream()
              .map(mediator1 -> mediator1.getClass().getSimpleName())
              .collect(Collectors.joining(",")));
    }

    final CompletableFuture<?>[] importTaskFutures =
        currentImportRound.stream()
            .map(
                mediator -> {
                  try {
                    return mediator.runImport();
                  } catch (IllegalStateException e) {
                    log.warn("Got into illegal state, will abort import round.", e);
                    throw e;
                  } catch (Exception e) {
                    log.error(
                        "Was not able to execute import of [{}]",
                        mediator.getClass().getSimpleName(),
                        e);
                    return CompletableFuture.completedFuture(null);
                  }
                })
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(importTaskFutures);
=======
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
>>>>>>> 5ccb6c7d (fix: give each Optimize import mediator its own independent scheduler thread)
  }

  public boolean isImporting() {
    return (mediatorExecutor != null && !mediatorExecutor.isShutdown())
        || importMediators.stream().anyMatch(ImportMediator::hasPendingImportJobs);
  }

  public List<ImportMediator> getImportMediators() {
    return importMediators;
  }

<<<<<<< HEAD
  protected boolean hasActiveImportJobs() {
    return importMediators.stream().anyMatch(ImportMediator::hasPendingImportJobs);
  }

  protected boolean nothingToBeImported(List<?> currentImportRound) {
    return currentImportRound.isEmpty();
  }

  protected void doBackoff() {
    long timeToSleep =
        importMediators.stream()
            .map(ImportMediator::getBackoffTimeInMs)
            .min(Long::compare)
            .orElse(5000L);
    try {
      log.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      log.error("Scheduler was interrupted while sleeping.", e);
      Thread.currentThread().interrupt();
=======
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
>>>>>>> 5ccb6c7d (fix: give each Optimize import mediator its own independent scheduler thread)
    }
  }
}
