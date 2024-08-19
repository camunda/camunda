/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.dto.optimize.SchedulerConfig;
import io.camunda.optimize.service.AbstractScheduledService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;

public abstract class AbstractImportScheduler<T extends SchedulerConfig>
    extends AbstractScheduledService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AbstractImportScheduler.class);
  protected final List<ImportMediator> importMediators;
  protected final T dataImportSourceDto;
  protected boolean isImporting = false;

  public AbstractImportScheduler(
      final List<ImportMediator> importMediators, final T dataImportSourceDto) {
    this.importMediators = importMediators;
    this.dataImportSourceDto = dataImportSourceDto;
  }

  @Override
  public void run() {
    if (isScheduledToRun()) {
      log.debug("Next round!");
      try {
        runImportRound();
      } catch (final Exception e) {
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
    isImporting = true;
    startScheduling();
  }

  public synchronized void stopImportScheduling() {
    log.info("Stop scheduling import from {}.", dataImportSourceDto);
    isImporting = false;
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
    final List<ImportMediator> currentImportRound =
        importMediators.stream()
            .filter(mediator -> forceImport || mediator.canImport())
            .collect(Collectors.toList());
    if (nothingToBeImported(currentImportRound)) {
      isImporting = false;
      if (!forceImport) {
        doBackoff();
      }
      return CompletableFuture.completedFuture(null);
    } else {
      isImporting = true;
      return executeImportRound(currentImportRound);
    }
  }

  public Future<Void> executeImportRound(final List<ImportMediator> currentImportRound) {
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
                  } catch (final IllegalStateException e) {
                    log.warn("Got into illegal state, will abort import round.", e);
                    throw e;
                  } catch (final Exception e) {
                    log.error(
                        "Was not able to execute import of [{}]",
                        mediator.getClass().getSimpleName(),
                        e);
                    return CompletableFuture.completedFuture(null);
                  }
                })
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(importTaskFutures);
  }

  public boolean isImporting() {
    return isImporting || hasActiveImportJobs();
  }

  public List<ImportMediator> getImportMediators() {
    return importMediators;
  }

  protected boolean hasActiveImportJobs() {
    return importMediators.stream().anyMatch(ImportMediator::hasPendingImportJobs);
  }

  protected boolean nothingToBeImported(final List<?> currentImportRound) {
    return currentImportRound.isEmpty();
  }

  protected void doBackoff() {
    final long timeToSleep =
        importMediators.stream()
            .map(ImportMediator::getBackoffTimeInMs)
            .min(Long::compare)
            .orElse(5000L);
    try {
      log.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (final InterruptedException e) {
      log.error("Scheduler was interrupted while sleeping.", e);
      Thread.currentThread().interrupt();
    }
  }

  public T getDataImportSourceDto() {
    return dataImportSourceDto;
  }
}
