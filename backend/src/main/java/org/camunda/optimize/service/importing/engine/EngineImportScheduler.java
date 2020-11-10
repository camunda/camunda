/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.ImportObserver;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class EngineImportScheduler extends AbstractScheduledService {
  // Iterating through this synchronized list is only thread-safe when synchronizing on the list itself, as per docs
  private final List<ImportObserver> importObservers = Collections.synchronizedList(new LinkedList<>());

  private final List<EngineImportMediator> importMediators;
  private final String engineAlias;
  private boolean isImporting = false;

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
    log.info("Start scheduling import from engine {}.", engineAlias);
    this.isImporting = true;
    startScheduling();
  }

  public synchronized void stopImportScheduling() {
    log.info("Stop scheduling import from engine {}.", engineAlias);
    this.isImporting = false;
    stopScheduling();
  }

  public void subscribe(ImportObserver importObserver) {
    importObservers.add(importObserver);
  }

  public void unsubscribe(ImportObserver importObserver) {
    importObservers.remove(importObserver);
  }

  public void shutdown() {
    log.debug("Scheduler for engine {} will shutdown.", engineAlias);
    getImportMediators().forEach(EngineImportMediator::shutdown);
  }

  public Future<Void> runImportRound() {
    return runImportRound(false);
  }

  public Future<Void> runImportRound(final boolean forceImport) {
    List<EngineImportMediator> currentImportRound = importMediators
      .stream()
      .filter(mediator -> forceImport || mediator.canImport())
      .collect(Collectors.toList());
    if (nothingToBeImported(currentImportRound)) {
      this.isImporting = false;
      if (!hasActiveImportJobs()) {
        notifyThatImportIsIdle();
      }
      if (!forceImport) {
        doBackoff();
      }
      return CompletableFuture.completedFuture(null);
    } else {
      this.isImporting = true;
      notifyThatImportIsInProgress();
      return executeImportRound(currentImportRound);
    }
  }

  public Future<Void> executeImportRound(List<EngineImportMediator> currentImportRound) {
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
        } catch (Exception e) {
          log.error("Was not able to execute import of [{}]", mediator.getClass().getSimpleName(), e);
          return CompletableFuture.completedFuture(null);
        }
      })
      .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(importTaskFutures);
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public boolean isImporting() {
    return isImporting || hasActiveImportJobs();
  }

  public List<EngineImportMediator> getImportMediators() {
    return importMediators;
  }

  private boolean hasActiveImportJobs() {
    return importMediators
      .stream()
      .anyMatch(EngineImportMediator::hasPendingImportJobs);
  }

  private void notifyThatImportIsInProgress() {
    synchronized (importObservers) {
      for (final ImportObserver importObserver : importObservers) {
        importObserver.importInProgress(engineAlias);
      }
    }
  }

  private void notifyThatImportIsIdle() {
    synchronized (importObservers) {
      for (final ImportObserver importObserver : importObservers) {
        importObserver.importIsIdle(engineAlias);
      }
    }
  }

  private boolean nothingToBeImported(List<?> currentImportRound) {
    return currentImportRound.isEmpty();
  }

  private void doBackoff() {
    long timeToSleep = importMediators
      .stream()
      .map(EngineImportMediator::getBackoffTimeInMs)
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
