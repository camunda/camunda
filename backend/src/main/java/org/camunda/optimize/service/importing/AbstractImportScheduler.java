/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SchedulerConfig;
import org.camunda.optimize.service.AbstractScheduledService;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractImportScheduler<T extends SchedulerConfig> extends AbstractScheduledService {

  protected final List<ImportMediator> importMediators;
  @Getter
  protected final T dataImportSourceDto;
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
    return new PeriodicTrigger(0L);
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
    List<ImportMediator> currentImportRound = importMediators
      .stream()
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
          .collect(Collectors.joining(","))
      );
    }

    final CompletableFuture<?>[] importTaskFutures = currentImportRound
      .stream()
      .map(mediator -> {
        try {
          return mediator.runImport();
        } catch (IllegalStateException e) {
          log.warn("Got into illegal state, will abort import round.", e);
          throw e;
        } catch (Exception e) {
          log.error("Was not able to execute import of [{}]", mediator.getClass().getSimpleName(), e);
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
    return importMediators
      .stream()
      .anyMatch(ImportMediator::hasPendingImportJobs);
  }

  protected boolean nothingToBeImported(List<?> currentImportRound) {
    return currentImportRound.isEmpty();
  }

  protected void doBackoff() {
    long timeToSleep = importMediators
      .stream()
      .map(ImportMediator::getBackoffTimeInMs)
      .min(Long::compare)
      .orElse(5000L);
    try {
      log.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      log.error("Scheduler was interrupted while sleeping.", e);
      Thread.currentThread().interrupt();
    }
  }

}
